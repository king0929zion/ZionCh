package com.zionchat.app.data

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64

data class ZiCodeGitHubUser(
    val login: String,
    val id: Long,
    val avatarUrl: String? = null
)

data class ZiCodeGitHubRepo(
    val owner: String,
    val name: String,
    val fullName: String,
    val defaultBranch: String = "main",
    val privateRepo: Boolean = true
)

data class ZiCodeRepoEntry(
    val path: String,
    val name: String,
    val type: String,
    val sha: String = "",
    val size: Int = 0
)

data class ZiCodeRepoFile(
    val path: String,
    val name: String,
    val sha: String,
    val size: Int,
    val content: String
)

data class ZiCodeWorkspaceAccess(
    val userLogin: String,
    val hasRead: Boolean,
    val hasWrite: Boolean,
    val hasAdmin: Boolean,
    val repo: ZiCodeGitHubRepo
)

interface ZiCodeGitHubService {
    suspend fun getAuthenticatedUser(pat: String): Result<ZiCodeGitHubUser>

    suspend fun getRepo(workspace: ZiCodeWorkspace, pat: String): Result<ZiCodeGitHubRepo>

    suspend fun checkWorkspaceAccess(workspace: ZiCodeWorkspace, pat: String): Result<ZiCodeWorkspaceAccess>

    suspend fun listAccessibleRepos(pat: String, perPage: Int = 100): Result<List<ZiCodeGitHubRepo>>

    suspend fun listRepoDir(
        workspace: ZiCodeWorkspace,
        pat: String,
        ref: String = workspace.defaultBranch,
        path: String = ""
    ): Result<List<ZiCodeRepoEntry>>

    suspend fun readRepoFile(
        workspace: ZiCodeWorkspace,
        pat: String,
        ref: String = workspace.defaultBranch,
        path: String
    ): Result<ZiCodeRepoFile>
}

class DefaultZiCodeGitHubService(
    private val client: OkHttpClient = OkHttpClient(),
    private val gson: Gson = Gson()
) : ZiCodeGitHubService {
    private fun buildRequest(url: String, pat: String): Request {
        return Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", "Bearer ${pat.trim()}")
            .addHeader("Accept", "application/vnd.github+json")
            .addHeader("X-GitHub-Api-Version", "2022-11-28")
            .addHeader("User-Agent", "ZionChat-ZiCode")
            .build()
    }

    override suspend fun getAuthenticatedUser(pat: String): Result<ZiCodeGitHubUser> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val token = pat.trim()
                require(token.isNotBlank()) { "PAT 不能为空" }
                val request = buildRequest("https://api.github.com/user", token)
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        error(extractGitHubError(body).ifBlank { "认证失败: HTTP ${response.code}" })
                    }
                    val obj = gson.fromJson(body, JsonObject::class.java) ?: error("响应解析失败")
                    val login = obj.get("login")?.asString?.trim().orEmpty()
                    val id = obj.get("id")?.asLong ?: 0L
                    if (login.isBlank() || id <= 0L) error("GitHub 用户信息无效")
                    ZiCodeGitHubUser(
                        login = login,
                        id = id,
                        avatarUrl = obj.get("avatar_url")?.asString?.trim()?.ifBlank { null }
                    )
                }
            }
        }
    }

    override suspend fun getRepo(workspace: ZiCodeWorkspace, pat: String): Result<ZiCodeGitHubRepo> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val owner = workspace.owner.trim()
                val repo = workspace.repo.trim()
                require(owner.isNotBlank() && repo.isNotBlank()) { "仓库信息不完整" }
                val request = buildRequest("https://api.github.com/repos/$owner/$repo", pat)
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val fallback = "$owner/$repo 仓库不可访问: HTTP ${response.code}"
                        error(extractGitHubError(body).ifBlank { fallback })
                    }
                    val obj = gson.fromJson(body, JsonObject::class.java) ?: error("仓库响应解析失败")
                    val defaultBranch = obj.get("default_branch")?.asString?.trim().orEmpty().ifBlank { "main" }
                    val fullName = obj.get("full_name")?.asString?.trim().orEmpty().ifBlank { "$owner/$repo" }
                    val privateRepo = obj.get("private")?.asBoolean ?: true
                    ZiCodeGitHubRepo(
                        owner = owner,
                        name = repo,
                        fullName = fullName,
                        defaultBranch = defaultBranch,
                        privateRepo = privateRepo
                    )
                }
            }
        }
    }

    override suspend fun checkWorkspaceAccess(workspace: ZiCodeWorkspace, pat: String): Result<ZiCodeWorkspaceAccess> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val user = getAuthenticatedUser(pat).getOrThrow()
                val owner = workspace.owner.trim()
                val repo = workspace.repo.trim()
                val request = buildRequest("https://api.github.com/repos/$owner/$repo", pat)
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val fallback = "$owner/$repo 权限校验失败: HTTP ${response.code}"
                        error(extractGitHubError(body).ifBlank { fallback })
                    }
                    val obj = gson.fromJson(body, JsonObject::class.java) ?: error("仓库权限响应解析失败")
                    val permissions = obj.getAsJsonObject("permissions")
                    val hasWrite = permissions?.get("push")?.asBoolean ?: false
                    val hasRead = permissions?.get("pull")?.asBoolean ?: true
                    val hasAdmin = permissions?.get("admin")?.asBoolean ?: false
                    ZiCodeWorkspaceAccess(
                        userLogin = user.login,
                        hasRead = hasRead,
                        hasWrite = hasWrite,
                        hasAdmin = hasAdmin,
                        repo = ZiCodeGitHubRepo(
                            owner = owner,
                            name = repo,
                            fullName = obj.get("full_name")?.asString?.trim().orEmpty().ifBlank { "$owner/$repo" },
                            defaultBranch = obj.get("default_branch")?.asString?.trim().orEmpty().ifBlank { "main" },
                            privateRepo = obj.get("private")?.asBoolean ?: true
                        )
                    )
                }
            }
        }
    }

    override suspend fun listAccessibleRepos(pat: String, perPage: Int): Result<List<ZiCodeGitHubRepo>> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val token = pat.trim()
                require(token.isNotBlank()) { "PAT 不能为空" }
                val safePerPage = perPage.coerceIn(1, 100)
                val url =
                    "https://api.github.com/user/repos?sort=updated&direction=desc&per_page=$safePerPage&affiliation=owner,collaborator,organization_member"
                val request = buildRequest(url, token)
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        error(extractGitHubError(body).ifBlank { "获取项目列表失败: HTTP ${response.code}" })
                    }
                    val array = gson.fromJson(body, JsonArray::class.java) ?: JsonArray()
                    array.mapNotNull { element ->
                        val obj = element?.asJsonObject ?: return@mapNotNull null
                        val fullName = obj.get("full_name")?.asString?.trim().orEmpty()
                        val ownerLogin =
                            obj.getAsJsonObject("owner")
                                ?.get("login")
                                ?.asString
                                ?.trim()
                                .orEmpty()
                        val repoName = obj.get("name")?.asString?.trim().orEmpty()
                        if (ownerLogin.isBlank() || repoName.isBlank()) return@mapNotNull null
                        ZiCodeGitHubRepo(
                            owner = ownerLogin,
                            name = repoName,
                            fullName = fullName.ifBlank { "$ownerLogin/$repoName" },
                            defaultBranch = obj.get("default_branch")?.asString?.trim().orEmpty().ifBlank { "main" },
                            privateRepo = obj.get("private")?.asBoolean ?: true
                        )
                    }
                }
            }
        }
    }

    override suspend fun listRepoDir(
        workspace: ZiCodeWorkspace,
        pat: String,
        ref: String,
        path: String
    ): Result<List<ZiCodeRepoEntry>> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val owner = workspace.owner.trim()
                val repo = workspace.repo.trim()
                require(owner.isNotBlank() && repo.isNotBlank()) { "仓库信息不完整" }
                val normalizedRef = ref.trim().ifBlank { workspace.defaultBranch }
                val normalizedPath = path.trim().trim('/').replace('\\', '/')
                val encodedPath = encodePath(normalizedPath)
                val url =
                    if (encodedPath.isBlank()) {
                        "https://api.github.com/repos/$owner/$repo/contents?ref=${urlEncode(normalizedRef)}"
                    } else {
                        "https://api.github.com/repos/$owner/$repo/contents/$encodedPath?ref=${urlEncode(normalizedRef)}"
                    }
                val request = buildRequest(url, pat)
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val fallback = if (response.code == 404) "目录不存在或仓库为空" else "读取目录失败: HTTP ${response.code}"
                        error(extractGitHubError(body).ifBlank { fallback })
                    }
                    val json = gson.fromJson(body, JsonElement::class.java)
                    val entries =
                        when {
                            json == null || json.isJsonNull -> emptyList()
                            json.isJsonArray -> parseRepoEntryArray(json.asJsonArray)
                            json.isJsonObject -> listOfNotNull(parseRepoEntry(json.asJsonObject))
                            else -> emptyList()
                        }
                    entries.sortedWith(
                        compareBy<ZiCodeRepoEntry> { if (it.type.equals("dir", ignoreCase = true)) 0 else 1 }
                            .thenBy { it.name.lowercase() }
                    )
                }
            }
        }
    }

    override suspend fun readRepoFile(
        workspace: ZiCodeWorkspace,
        pat: String,
        ref: String,
        path: String
    ): Result<ZiCodeRepoFile> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val owner = workspace.owner.trim()
                val repo = workspace.repo.trim()
                val normalizedPath = path.trim().trim('/').replace('\\', '/')
                require(owner.isNotBlank() && repo.isNotBlank()) { "仓库信息不完整" }
                require(normalizedPath.isNotBlank()) { "文件路径不能为空" }
                val normalizedRef = ref.trim().ifBlank { workspace.defaultBranch }
                val encodedPath = encodePath(normalizedPath)
                val url = "https://api.github.com/repos/$owner/$repo/contents/$encodedPath?ref=${urlEncode(normalizedRef)}"
                val request = buildRequest(url, pat)
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val fallback = if (response.code == 404) "文件不存在" else "读取文件失败: HTTP ${response.code}"
                        error(extractGitHubError(body).ifBlank { fallback })
                    }
                    val obj = gson.fromJson(body, JsonObject::class.java) ?: error("文件响应解析失败")
                    val type = obj.get("type")?.asString?.trim().orEmpty()
                    if (!type.equals("file", ignoreCase = true)) {
                        error("目标不是文件")
                    }
                    val encoded = obj.get("content")?.asString.orEmpty().replace("\n", "")
                    val decoded =
                        runCatching { String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8) }
                            .getOrElse { error("文件内容解码失败") }
                    ZiCodeRepoFile(
                        path = obj.get("path")?.asString?.trim().orEmpty().ifBlank { normalizedPath },
                        name = obj.get("name")?.asString?.trim().orEmpty().ifBlank { normalizedPath.substringAfterLast('/') },
                        sha = obj.get("sha")?.asString?.trim().orEmpty(),
                        size = obj.get("size")?.asInt ?: decoded.length,
                        content = decoded
                    )
                }
            }
        }
    }

    private fun parseRepoEntryArray(array: JsonArray): List<ZiCodeRepoEntry> {
        return array.mapNotNull { element ->
            val obj = element?.asJsonObject ?: return@mapNotNull null
            parseRepoEntry(obj)
        }
    }

    private fun parseRepoEntry(obj: JsonObject): ZiCodeRepoEntry? {
        val rawPath = obj.get("path")?.asString?.trim().orEmpty()
        val rawName = obj.get("name")?.asString?.trim().orEmpty()
        val type = obj.get("type")?.asString?.trim().orEmpty().ifBlank { "file" }
        if (rawPath.isBlank() || rawName.isBlank()) return null
        return ZiCodeRepoEntry(
            path = rawPath,
            name = rawName,
            type = type,
            sha = obj.get("sha")?.asString?.trim().orEmpty(),
            size = obj.get("size")?.asInt ?: 0
        )
    }

    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
    }

    private fun encodePath(path: String): String {
        val normalized = path.trim().trim('/').replace('\\', '/')
        if (normalized.isBlank()) return ""
        return normalized.split('/').joinToString("/") { segment -> urlEncode(segment) }
    }

    private fun extractGitHubError(body: String): String {
        if (body.isBlank()) return ""
        return runCatching {
            val obj = gson.fromJson(body, JsonObject::class.java)
            val message = obj?.get("message")?.asString?.trim().orEmpty()
            val documentationUrl = obj?.get("documentation_url")?.asString?.trim().orEmpty()
            when {
                message.isBlank() -> ""
                documentationUrl.isBlank() -> message
                else -> "$message ($documentationUrl)"
            }
        }.getOrDefault("")
    }
}
