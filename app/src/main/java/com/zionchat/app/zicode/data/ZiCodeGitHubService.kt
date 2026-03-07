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
            return@withContext Result.failure(IllegalArgumentException("File path cannot be empty."))
        }

        executeJson(
            Request.Builder()
                .url("$apiBase/repos/${owner.trim()}/${repo.trim()}/contents/${encodePath(normalizedPath)}")
                .get()
                .applyGitHubHeaders(token)
                .build()
        ).mapCatching { body ->
            val root = body.asJsonObject
            root.toFilePreview(normalizedPath)
        }
    }

    suspend fun listBranches(
        token: String,
        owner: String,
        repo: String
    ): Result<List<ZiCodeGitHubBranch>> = withContext(Dispatchers.IO) {
        executeJson(
            Request.Builder()
                .url("$apiBase/repos/${owner.trim()}/${repo.trim()}/branches?per_page=100")
                .get()
                .applyGitHubHeaders(token)
                .build()
        ).mapCatching { body ->
            body.asJsonArray.mapNotNull { element ->
                runCatching {
                    val root = element.asJsonObject
                    ZiCodeGitHubBranch(
                        name = root.string("name"),
                        sha = root.getAsJsonObject("commit")?.string("sha").orEmpty(),
                        protectedBranch = root.booleanOrFalse("protected")
                    )
                }.getOrNull()
            }
        }
    }

    suspend fun createBranch(
        token: String,
        owner: String,
        repo: String,
        newBranchName: String,
        sourceSha: String
    ): Result<ZiCodeGitHubBranch> = withContext(Dispatchers.IO) {
        val payload =
            JsonObject().apply {
                addProperty("ref", "refs/heads/${newBranchName.trim()}")
                addProperty("sha", sourceSha.trim())
            }
        executeJson(
            Request.Builder()
                .url("$apiBase/repos/${owner.trim()}/${repo.trim()}/git/refs")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .applyGitHubHeaders(token)
                .build()
        ).mapCatching { body ->
            val root = body.asJsonObject
            ZiCodeGitHubBranch(
                name = root.string("ref").substringAfterLast('/'),
                sha = root.getAsJsonObject("object")?.string("sha").orEmpty(),
                protectedBranch = false
            )
        }
    }

    suspend fun listCommits(
        token: String,
        owner: String,
        repo: String,
        branch: String? = null,
        limit: Int = 12
    ): Result<List<ZiCodeGitHubCommit>> = withContext(Dispatchers.IO) {
        val refParam =
            branch?.trim()?.takeIf { it.isNotBlank() }?.let {
                "&sha=${encodeValue(it)}"
            }.orEmpty()
        executeJson(
            Request.Builder()
                .url("$apiBase/repos/${owner.trim()}/${repo.trim()}/commits?per_page=${limit.coerceIn(1, 50)}$refParam")
                .get()
                .applyGitHubHeaders(token)
                .build()
        ).mapCatching { body ->
            body.asJsonArray.mapNotNull { element ->
                runCatching { element.asJsonObject.toCommit() }.getOrNull()
            }
        }
    }

    suspend fun listIssues(
        token: String,
        owner: String,
        repo: String,
        state: String = "open",
        limit: Int = 20
    ): Result<List<ZiCodeGitHubIssue>> = withContext(Dispatchers.IO) {
        executeJson(
            Request.Builder()
                .url("$apiBase/repos/${owner.trim()}/${repo.trim()}/issues?state=${encodeValue(state.trim().ifBlank { "open" })}&per_page=${limit.coerceIn(1, 50)}")
                .get()
                .applyGitHubHeaders(token)
                .build()
        ).mapCatching { body ->
            body.asJsonArray.mapNotNull { element ->
                runCatching {
                    val root = element.asJsonObject
                    if (root.has("pull_request")) null else root.toIssue()
                }.getOrNull()
            }
        }
    }

    suspend fun createIssue(
        token: String,
        owner: String,
        repo: String,
        title: String,
        body: String = "",
        labels: List<String> = emptyList(),
        assignees: List<String> = emptyList()
    ): Result<ZiCodeGitHubIssue> = withContext(Dispatchers.IO) {
        val payload =
            JsonObject().apply {
                addProperty("title", title.trim())
                body.trim().takeIf { it.isNotBlank() }?.let { addProperty("body", it) }
                if (labels.isNotEmpty()) {
                    add("labels", JsonArray().apply { labels.filter { it.isNotBlank() }.forEach { add(it.trim()) } })
                }
                if (assignees.isNotEmpty()) {
                    add("assignees", JsonArray().apply { assignees.filter { it.isNotBlank() }.forEach { add(it.trim()) } })
                }
            }
        executeJson(
            Request.Builder()
                .url("$apiBase/repos/${owner.trim()}/${repo.trim()}/issues")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .applyGitHubHeaders(token)
                .build()
        ).mapCatching { body ->
            body.asJsonObject.toIssue()
        }
    }

    suspend fun listPullRequests(
        token: String,
        owner: String,
        repo: String,
        state: String = "open",
        limit: Int = 20
    ): Result<List<ZiCodeGitHubPullRequest>> = withContext(Dispatchers.IO) {
        executeJson(
            Request.Builder()
                .url("$apiBase/repos/${owner.trim()}/${repo.trim()}/pulls?state=${encodeValue(state.trim().ifBlank { "open" })}&per_page=${limit.coerceIn(1, 50)}")
                .get()
                .applyGitHubHeaders(token)
                .build()
        ).mapCatching { body ->
            body.asJsonArray.mapNotNull { element ->
                runCatching { element.asJsonObject.toPullRequest() }.getOrNull()
            }
        }
    }

    suspend fun createPullRequest(
        token: String,
        owner: String,
        repo: String,
        title: String,
        head: String,
        base: String,
        body: String = "",
        draft: Boolean = false
    ): Result<ZiCodeGitHubPullRequest> = withContext(Dispatchers.IO) {
        val payload =
            JsonObject().apply {
                addProperty("title", title.trim())
                addProperty("head", head.trim())
                addProperty("base", base.trim())
                body.trim().takeIf { it.isNotBlank() }?.let { addProperty("body", it) }
                addProperty("draft", draft)
            }
        executeJson(
            Request.Builder()
                .url("$apiBase/repos/${owner.trim()}/${repo.trim()}/pulls")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .applyGitHubHeaders(token)
                .build()
        ).mapCatching { body ->
            body.asJsonObject.toPullRequest()
        }
    }

    suspend fun createOrUpdateFile(
        token: String,
        owner: String,
        repo: String,
        path: String,
        content: String,
        message: String,
        branch: String? = null,
        sha: String? = null
    ): Result<ZiCodeGitHubCommit> = withContext(Dispatchers.IO) {
        val normalizedPath = normalizePath(path)
        if (normalizedPath.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("File path cannot be empty."))
        }
        val payload =
            JsonObject().apply {
                addProperty("message", message.trim().ifBlank { "Update $normalizedPath" })
                addProperty("content", Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
                branch?.trim()?.takeIf { it.isNotBlank() }?.let { addProperty("branch", it) }
                sha?.trim()?.takeIf { it.isNotBlank() }?.let { addProperty("sha", it) }
            }
        executeJson(
            Request.Builder()
                .url("$apiBase/repos/${owner.trim()}/${repo.trim()}/contents/${encodePath(normalizedPath)}")
                .put(payload.toString().toRequestBody(jsonMediaType))
                .applyGitHubHeaders(token)
                .build()
        ).mapCatching { body ->
            body.asJsonObject.getAsJsonObject("commit")?.toCommit()
                ?: throw IllegalStateException("GitHub did not return commit data.")
        }
    }

    suspend fun deleteFile(
        token: String,
        owner: String,
        repo: String,
        path: String,
        sha: String,
        message: String,
        branch: String? = null
    ): Result<ZiCodeGitHubCommit> = withContext(Dispatchers.IO) {
        val normalizedPath = normalizePath(path)
        if (normalizedPath.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("File path cannot be empty."))
        }
        val payload =
            JsonObject().apply {
                addProperty("message", message.trim().ifBlank { "Delete $normalizedPath" })
                addProperty("sha", sha.trim())
                branch?.trim()?.takeIf { it.isNotBlank() }?.let { addProperty("branch", it) }
            }
        executeJson(
            Request.Builder()
                .url("$apiBase/repos/${owner.trim()}/${repo.trim()}/contents/${encodePath(normalizedPath)}")
                .delete(payload.toString().toRequestBody(jsonMediaType))
                .applyGitHubHeaders(token)
                .build()
        ).mapCatching { body ->
            body.asJsonObject.getAsJsonObject("commit")?.toCommit()
                ?: throw IllegalStateException("GitHub did not return delete commit data.")
        }
    }

    suspend fun listWorkflows(
        token: String,
        owner: String,
        repo: String
    ): Result<List<ZiCodeGitHubWorkflow>> = withContext(Dispatchers.IO) {
        executeJson(
            Request.Builder()
                .url("$apiBase/repos/${owner.trim()}/${repo.trim()}/actions/workflows?per_page=100")
                .get()
                .applyGitHubHeaders(token)
                .build()
        ).mapCatching { body ->
            body.asJsonObject.getAsJsonArray("workflows")
                ?.mapNotNull { element ->
                    runCatching {
                        val root = element.asJsonObject
                        ZiCodeGitHubWorkflow(
                            id = root.longOrNull("id") ?: 0L,
                            name = root.string("name"),
                            path = root.stringOrNull("path").orEmpty(),
                            state = root.stringOrNull("state").orEmpty()
                        )
                    }.getOrNull()
                }
                .orEmpty()
        }
    }

    suspend fun dispatchWorkflow(
        token: String,
        owner: String,
        repo: String,
        workflowIdOrFileName: String,
        ref: String,
        inputs: Map<String, String> = emptyMap()
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val payload =
            JsonObject().apply {
                addProperty("ref", ref.trim())
                add(
                    "inputs",
                    JsonObject().apply {
                        inputs.forEach { (key, value) ->
                            if (key.isNotBlank()) addProperty(key.trim(), value)
                        }
                    }
                )
            }
        executeEmpty(
            Request.Builder()
                .url("$apiBase/repos/${owner.trim()}/${repo.trim()}/actions/workflows/${workflowIdOrFileName.trim()}/dispatches")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .applyGitHubHeaders(token)
                .build()
        )
    }

    suspend fun listWorkflowRuns(
        token: String,
        owner: String,
        repo: String,
        limit: Int = 15
    ): Result<List<ZiCodeGitHubWorkflowRun>> = withContext(Dispatchers.IO) {
        executeJson(
            Request.Builder()
                .url("$apiBase/repos/${owner.trim()}/${repo.trim()}/actions/runs?per_page=${limit.coerceIn(1, 50)}")
                .get()
                .applyGitHubHeaders(token)
                .build()
        ).mapCatching { body ->
            body.asJsonObject.getAsJsonArray("workflow_runs")
                ?.mapNotNull { element ->
                    runCatching { element.asJsonObject.toWorkflowRun() }.getOrNull()
                }
                .orEmpty()
        }
    }

    suspend fun listArtifacts(
        token: String,
        owner: String,
        repo: String,
        runId: Long? = null,
        limit: Int = 15
    ): Result<List<ZiCodeGitHubArtifact>> = withContext(Dispatchers.IO) {
        val url =
            if (runId != null && runId > 0L) {
                "$apiBase/repos/${owner.trim()}/${repo.trim()}/actions/runs/$runId/artifacts?per_page=${limit.coerceIn(1, 50)}"
            } else {
                "$apiBase/repos/${owner.trim()}/${repo.trim()}/actions/artifacts?per_page=${limit.coerceIn(1, 50)}"
            }
        executeJson(
            Request.Builder()
                .url(url)
                .get()
                .applyGitHubHeaders(token)
                .build()
        ).mapCatching { body ->
            body.asJsonObject.getAsJsonArray("artifacts")
                ?.mapNotNull { element ->
                    runCatching { element.asJsonObject.toArtifact() }.getOrNull()
                }
                .orEmpty()
        }
    }

    suspend fun readWorkflowRunTrace(
        token: String,
        owner: String,
        repo: String,
        runId: Long
    ): Result<String> = withContext(Dispatchers.IO) {
        executeJson(
            Request.Builder()
                .url("$apiBase/repos/${owner.trim()}/${repo.trim()}/actions/runs/$runId/jobs?per_page=100")
                .get()
                .applyGitHubHeaders(token)
                .build()
        ).mapCatching { body ->
            val jobs = body.asJsonObject.getAsJsonArray("jobs").orEmpty()
            if (jobs.size() == 0) {
                return@mapCatching "No visible jobs were returned for this workflow run."
            }
            buildString {
                jobs.forEach { jobElement ->
                    val job = jobElement.asJsonObject
                    val jobName = job.string("name").ifBlank { "Job" }
                    appendLine("$jobName · ${job.stringOrNull("status").orEmpty()} · ${job.stringOrNull("conclusion").orEmpty().ifBlank { "running" }}")
                    job.getAsJsonArray("steps")?.forEach { stepElement ->
                        val step = stepElement.asJsonObject
                        append("  - ")
                        append(step.string("name"))
                        append(" · ")
                        append(step.stringOrNull("status").orEmpty())
                        step.stringOrNull("conclusion")?.takeIf { it.isNotBlank() }?.let {
                            append(" · ")
                            append(it)
                        }
                        appendLine()
                    }
                }
            }.trim()
        }
    }

    suspend fun cancelWorkflowRun(
        token: String,
        owner: String,
        repo: String,
        runId: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        executeEmpty(
            Request.Builder()
                .url("$apiBase/repos/${owner.trim()}/${repo.trim()}/actions/runs/$runId/cancel")
                .post("{}".toRequestBody(jsonMediaType))
                .applyGitHubHeaders(token)
                .build()
        )
    }

    suspend fun rerunWorkflowRun(
        token: String,
        owner: String,
        repo: String,
        runId: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        executeEmpty(
            Request.Builder()
                .url("$apiBase/repos/${owner.trim()}/${repo.trim()}/actions/runs/$runId/rerun")
                .post("{}".toRequestBody(jsonMediaType))
                .applyGitHubHeaders(token)
                .build()
        )
    }

    suspend fun listReleases(
        token: String,
        owner: String,
        repo: String,
        limit: Int = 10
    ): Result<List<ZiCodeGitHubRelease>> = withContext(Dispatchers.IO) {
        executeJson(
            Request.Builder()
                .url("$apiBase/repos/${owner.trim()}/${repo.trim()}/releases?per_page=${limit.coerceIn(1, 30)}")
                .get()
                .applyGitHubHeaders(token)
                .build()
        ).mapCatching { body ->
            body.asJsonArray.mapNotNull { element ->
                runCatching { element.asJsonObject.toRelease() }.getOrNull()
            }
        }
    }

    suspend fun createRelease(
        token: String,
        owner: String,
        repo: String,
        tagName: String,
        releaseName: String,
        body: String,
        draft: Boolean = false,
        prerelease: Boolean = false,
        targetCommitish: String? = null
    ): Result<ZiCodeGitHubRelease> = withContext(Dispatchers.IO) {
        val payload =
            JsonObject().apply {
                addProperty("tag_name", tagName.trim())
                addProperty("name", releaseName.trim().ifBlank { tagName.trim() })
                addProperty("body", body)
                addProperty("draft", draft)
                addProperty("prerelease", prerelease)
                targetCommitish?.trim()?.takeIf { it.isNotBlank() }?.let { addProperty("target_commitish", it) }
            }
        executeJson(
            Request.Builder()
                .url("$apiBase/repos/${owner.trim()}/${repo.trim()}/releases")
                .post(payload.toString().toRequestBody(jsonMediaType))
                .applyGitHubHeaders(token)
                .build()
        ).mapCatching { body ->
            body.asJsonObject.toRelease()
        }
    }

    suspend fun fetchPagesInfo(
        token: String,
        owner: String,
        repo: String
    ): Result<ZiCodeGitHubPagesInfo> = withContext(Dispatchers.IO) {
        executeJson(
            Request.Builder()
                .url("$apiBase/repos/${owner.trim()}/${repo.trim()}/pages")
                .get()
                .applyGitHubHeaders(token)
                .build()
        ).mapCatching { body ->
            val root = body.asJsonObject
            val source = root.getAsJsonObject("source")
            ZiCodeGitHubPagesInfo(
                status = root.stringOrNull("status").orEmpty().ifBlank { "built" },
                htmlUrl = root.stringOrNull("html_url"),
                sourceBranch = source?.stringOrNull("branch"),
                sourcePath = source?.stringOrNull("path")
            )
        }.recoverCatching { throwable ->
            if (throwable.message.orEmpty().contains("(404)")) {
                ZiCodeGitHubPagesInfo(status = "not_configured")
            } else {
                throw throwable
            }
        }
    }

    private fun JsonObject.toFilePreview(path: String): ZiCodeFilePreview {
        val encoded = stringOrNull("content").orEmpty().replace("\n", "")
        val size = longOrNull("size") ?: 0L
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
                "GitHub did not return previewable text content. The file may be binary or too large."
            }

        val truncated = previewText.length > previewLimit
        return ZiCodeFilePreview(
            path = path,
            content = previewText.take(previewLimit),
            size = size.coerceAtLeast(previewText.length.toLong()),
            truncated = truncated
        )
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

    private fun executeEmpty(request: Request): Result<Unit> {
        return runCatching {
            client.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException(parseGitHubError(rawBody, response.code))
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

    private fun JsonObject.toCommit(): ZiCodeGitHubCommit {
        val commitObject = getAsJsonObject("commit") ?: this
        val authorObject = commitObject.getAsJsonObject("author") ?: getAsJsonObject("author")
        return ZiCodeGitHubCommit(
            sha = string("sha"),
            message = commitObject.stringOrNull("message").orEmpty().lineSequence().firstOrNull().orEmpty(),
            authorName = authorObject?.stringOrNull("name"),
            htmlUrl = stringOrNull("html_url"),
            committedAt = parseIsoTime(authorObject?.stringOrNull("date"))
        )
    }

    private fun JsonObject.toWorkflowRun(): ZiCodeGitHubWorkflowRun {
        return ZiCodeGitHubWorkflowRun(
            id = longOrNull("id") ?: 0L,
            name = stringOrNull("name").orEmpty().ifBlank { "Workflow run" },
            status = stringOrNull("status").orEmpty(),
            conclusion = stringOrNull("conclusion"),
            htmlUrl = stringOrNull("html_url").orEmpty(),
            branch = stringOrNull("head_branch"),
            event = stringOrNull("event"),
            createdAt = parseIsoTime(stringOrNull("created_at")),
            updatedAt = parseIsoTime(stringOrNull("updated_at"))
        )
    }

    private fun JsonObject.toRelease(): ZiCodeGitHubRelease {
        return ZiCodeGitHubRelease(
            id = longOrNull("id") ?: 0L,
            name = stringOrNull("name").orEmpty(),
            tagName = stringOrNull("tag_name").orEmpty(),
            htmlUrl = stringOrNull("html_url").orEmpty(),
            draft = booleanOrFalse("draft"),
            prerelease = booleanOrFalse("prerelease"),
            publishedAt = parseIsoTime(stringOrNull("published_at"))
        )
    }

    private fun JsonObject.toIssue(): ZiCodeGitHubIssue {
        return ZiCodeGitHubIssue(
            id = longOrNull("id") ?: 0L,
            number = longOrNull("number")?.toInt() ?: 0,
            title = stringOrNull("title").orEmpty(),
            state = stringOrNull("state").orEmpty(),
            htmlUrl = stringOrNull("html_url").orEmpty(),
            bodyPreview = stringOrNull("body")?.take(600),
            authorLogin = getAsJsonObject("user")?.stringOrNull("login"),
            createdAt = parseIsoTime(stringOrNull("created_at")),
            updatedAt = parseIsoTime(stringOrNull("updated_at"))
        )
    }

    private fun JsonObject.toPullRequest(): ZiCodeGitHubPullRequest {
        val head = getAsJsonObject("head")
        val base = getAsJsonObject("base")
        return ZiCodeGitHubPullRequest(
            id = longOrNull("id") ?: 0L,
            number = longOrNull("number")?.toInt() ?: 0,
            title = stringOrNull("title").orEmpty(),
            state = stringOrNull("state").orEmpty(),
            htmlUrl = stringOrNull("html_url").orEmpty(),
            draft = booleanOrFalse("draft"),
            headRef = head?.stringOrNull("ref"),
            baseRef = base?.stringOrNull("ref"),
            authorLogin = getAsJsonObject("user")?.stringOrNull("login"),
            createdAt = parseIsoTime(stringOrNull("created_at")),
            updatedAt = parseIsoTime(stringOrNull("updated_at"))
        )
    }

    private fun JsonObject.toArtifact(): ZiCodeGitHubArtifact {
        return ZiCodeGitHubArtifact(
            id = longOrNull("id") ?: 0L,
            name = stringOrNull("name").orEmpty(),
            sizeInBytes = longOrNull("size_in_bytes") ?: 0L,
            expired = booleanOrFalse("expired"),
            downloadUrl = stringOrNull("archive_download_url"),
            createdAt = parseIsoTime(stringOrNull("created_at")),
            updatedAt = parseIsoTime(stringOrNull("updated_at"))
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

    private fun JsonObject.has(key: String): Boolean {
        return get(key) != null
    }

    private fun JsonObject.getAsJsonArray(key: String): JsonArray? {
        val value = get(key) ?: return null
        if (!value.isJsonArray) return null
        return value.asJsonArray
    }

    private fun JsonArray?.orEmpty(): JsonArray {
        return this ?: JsonArray()
    }

    private fun parseGitHubError(body: String, statusCode: Int): String {
        val parsed =
            runCatching {
                JsonParser.parseString(body)
                    .takeIf { it.isJsonObject }
                    ?.asJsonObject
                    ?.stringOrNull("message")
            }.getOrNull()
        val suffix = parsed?.takeIf { it.isNotBlank() } ?: "GitHub request failed"
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
                encodeValue(segment)
            }
    }

    private fun encodeValue(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
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
