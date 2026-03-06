package com.zionchat.app.zicode.data

import android.util.Base64
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class ZiCodeGitHubService {
    private val client =
        OkHttpClient.Builder()
            .callTimeout(25, TimeUnit.SECONDS)
            .build()

    suspend fun fetchViewer(token: String): Result<ZiCodeViewer> = withContext(Dispatchers.IO) {
        executeJson(
            Request.Builder()
                .url("$apiBase/user")
                .get()
                .applyGitHubHeaders(token)
                .build()
        ).mapCatching { body ->
            val root = body.asJsonObject
            ZiCodeViewer(
                login = root.string("login"),
                displayName = root.stringOrNull("name"),
                avatarUrl = root.stringOrNull("avatar_url")
            )
        }
    }

    suspend fun listRepos(token: String): Result<List<ZiCodeRemoteRepo>> = withContext(Dispatchers.IO) {
        executeJson(
            Request.Builder()
                .url("$apiBase/user/repos?per_page=100&sort=updated&direction=desc")
                .get()
                .applyGitHubHeaders(token)
                .build()
        ).mapCatching { body ->
            body.asJsonArray.mapNotNull { element ->
                runCatching { element.asJsonObject.toRemoteRepo() }.getOrNull()
            }
        }
    }

    suspend fun fetchRepo(
        token: String,
        owner: String,
        repo: String
    ): Result<ZiCodeRemoteRepo> = withContext(Dispatchers.IO) {
        executeJson(
            Request.Builder()
                .url("$apiBase/repos/${owner.trim()}/${repo.trim()}")
                .get()
                .applyGitHubHeaders(token)
                .build()
        ).mapCatching { body ->
            body.asJsonObject.toRemoteRepo()
        }
    }

    suspend fun createRepo(
        token: String,
        name: String,
        description: String,
        privateRepo: Boolean
    ): Result<ZiCodeRemoteRepo> = withContext(Dispatchers.IO) {
        val payload =
            JsonObject().apply {
                addProperty("name", name.trim())
                addProperty("description", description.trim())
                addProperty("private", privateRepo)
                addProperty("auto_init", true)
            }

        executeJson(
            Request.Builder()
                .url("$apiBase/user/repos")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .applyGitHubHeaders(token)
                .build()
        ).mapCatching { body ->
            body.asJsonObject.toRemoteRepo()
        }
    }

    suspend fun listDirectory(
        token: String,
        owner: String,
        repo: String,
        path: String
    ): Result<List<ZiCodeRepoNode>> = withContext(Dispatchers.IO) {
        val normalizedPath = normalizePath(path)
        val url =
            if (normalizedPath.isBlank()) {
                "$apiBase/repos/${owner.trim()}/${repo.trim()}/contents"
            } else {
                "$apiBase/repos/${owner.trim()}/${repo.trim()}/contents/${encodePath(normalizedPath)}"
            }

        executeJson(
            Request.Builder()
                .url(url)
                .get()
                .applyGitHubHeaders(token)
                .build()
        ).mapCatching { body ->
            val array =
                if (body.isJsonArray) {
                    body.asJsonArray
                } else {
                    JsonArray().apply { add(body) }
                }
            array.mapNotNull { element ->
                runCatching { element.asJsonObject.toRepoNode() }.getOrNull()
            }.sortedWith(
                compareBy<ZiCodeRepoNode> { it.type != "dir" }
                    .thenBy { it.name.lowercase() }
            )
        }
    }

    suspend fun readFile(
        token: String,
        owner: String,
        repo: String,
        path: String
    ): Result<ZiCodeFilePreview> = withContext(Dispatchers.IO) {
        val normalizedPath = normalizePath(path)
        if (normalizedPath.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("文件路径不能为空。"))
        }

        executeJson(
            Request.Builder()
                .url("$apiBase/repos/${owner.trim()}/${repo.trim()}/contents/${encodePath(normalizedPath)}")
                .get()
                .applyGitHubHeaders(token)
                .build()
        ).mapCatching { body ->
            val root = body.asJsonObject
            val encoded = root.stringOrNull("content").orEmpty().replace("\n", "")
            val size = root.longOrNull("size") ?: 0L
            val decoded =
                if (encoded.isNotBlank()) {
                    runCatching {
                        String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
                    }.getOrDefault("")
                } else {
                    ""
                }

            val previewText =
                if (decoded.isNotBlank()) {
                    decoded
                } else {
                    "GitHub 没有返回可直接预览的文本内容，可能是二进制文件或体积过大。"
                }

            val truncated = previewText.length > previewLimit
            ZiCodeFilePreview(
                path = normalizedPath,
                content = previewText.take(previewLimit),
                size = size.coerceAtLeast(previewText.length.toLong()),
                truncated = truncated
            )
        }
    }

    private fun executeJson(request: Request): Result<com.google.gson.JsonElement> {
        return runCatching {
            client.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException(parseGitHubError(rawBody, response.code))
                }
                if (rawBody.isBlank()) {
                    JsonObject()
                } else {
                    JsonParser.parseString(rawBody)
                }
            }
        }
    }

    private fun Request.Builder.applyGitHubHeaders(token: String): Request.Builder {
        return this
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer ${token.trim()}")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "ZionChat-ZiCode")
    }

    private fun JsonObject.toRemoteRepo(): ZiCodeRemoteRepo {
        val ownerObject = getAsJsonObject("owner")
        val owner = ownerObject?.string("login").orEmpty()
        val name = string("name")
        return ZiCodeRemoteRepo(
            id = longOrNull("id") ?: 0L,
            owner = owner,
            name = name,
            fullName = string("full_name").ifBlank { "$owner/$name" },
            description = stringOrNull("description").orEmpty(),
            privateRepo = booleanOrFalse("private"),
            defaultBranch = stringOrNull("default_branch").orEmpty().ifBlank { "main" },
            htmlUrl = stringOrNull("html_url").orEmpty(),
            homepageUrl = stringOrNull("homepage"),
            pushedAt = parseIsoTime(stringOrNull("pushed_at")),
            updatedAt = parseIsoTime(stringOrNull("updated_at"))
        )
    }

    private fun JsonObject.toRepoNode(): ZiCodeRepoNode {
        return ZiCodeRepoNode(
            name = string("name"),
            path = string("path"),
            type = string("type"),
            size = longOrNull("size"),
            sha = stringOrNull("sha"),
            downloadUrl = stringOrNull("download_url")
        )
    }

    private fun JsonObject.string(key: String): String {
        return stringOrNull(key).orEmpty()
    }

    private fun JsonObject.stringOrNull(key: String): String? {
        val value = get(key) ?: return null
        if (!value.isJsonPrimitive) return null
        return runCatching { value.asString.trim() }.getOrNull()
    }

    private fun JsonObject.longOrNull(key: String): Long? {
        val value = get(key) ?: return null
        if (!value.isJsonPrimitive) return null
        return runCatching { value.asLong }.getOrNull()
    }

    private fun JsonObject.booleanOrFalse(key: String): Boolean {
        val value = get(key) ?: return false
        if (!value.isJsonPrimitive) return false
        return runCatching { value.asBoolean }.getOrDefault(false)
    }

    private fun parseGitHubError(body: String, statusCode: Int): String {
        val parsed =
            runCatching {
                JsonParser.parseString(body)
                    .takeIf { it.isJsonObject }
                    ?.asJsonObject
                    ?.stringOrNull("message")
            }.getOrNull()
        val suffix = parsed?.takeIf { it.isNotBlank() } ?: "GitHub 请求失败"
        return "GitHub API ($statusCode): $suffix"
    }

    private fun parseIsoTime(raw: String?): Long {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return 0L
        return runCatching {
            isoFormatter.parse(value)?.time ?: 0L
        }.getOrDefault(0L)
    }

    private fun normalizePath(path: String): String {
        return path.trim().trim('/').replace(Regex("/{2,}"), "/")
    }

    private fun encodePath(path: String): String {
        return path.split('/')
            .filter { it.isNotBlank() }
            .joinToString("/") { segment ->
                URLEncoder.encode(segment, Charsets.UTF_8.name()).replace("+", "%20")
            }
    }

    companion object {
        private const val apiBase = "https://api.github.com"
        private const val previewLimit = 32_000
        private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        private val isoFormatter =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
    }
}
