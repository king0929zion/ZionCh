package com.zionchat.app.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

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
