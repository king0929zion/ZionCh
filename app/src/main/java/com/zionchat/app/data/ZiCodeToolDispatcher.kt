package com.zionchat.app.data

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.math.max
import kotlin.math.min

data class ZiCodeToolDispatchResult(
    val success: Boolean,
    val resultJson: String? = null,
    val error: String? = null,
    val userHint: String = ""
)

interface ZiCodeToolDispatcher {
    suspend fun dispatch(
        sessionId: String,
        workspace: ZiCodeWorkspace,
        settings: ZiCodeSettings,
        toolName: String,
        argsJson: String
    ): ZiCodeToolDispatchResult
}

class DefaultZiCodeToolDispatcher(
    private val repository: AppRepository,
    private val gitHubService: ZiCodeGitHubService,
    private val policyService: ZiCodePolicyService,
    private val mcpClient: McpClient,
    private val client: OkHttpClient = OkHttpClient(),
    private val gson: Gson = Gson()
) : ZiCodeToolDispatcher {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val stagedChangesBySession = mutableMapOf<String, MutableMap<String, String?>>()
    private val stageMutex = Mutex()

    override suspend fun dispatch(
        sessionId: String,
        workspace: ZiCodeWorkspace,
        settings: ZiCodeSettings,
        toolName: String,
        argsJson: String
    ): ZiCodeToolDispatchResult {
        val startedAt = System.currentTimeMillis()
        val call =
            ZiCodeToolCall(
                sessionId = sessionId,
                toolName = toolName,
                argsJson = argsJson,
                status = "running",
                startedAt = startedAt,
                userHint = buildUserHint(toolName, argsJson)
            )
        repository.upsertZiCodeToolCall(call)

        val result = runCatching {
            val args = parseArgs(argsJson)
            val configuredPat = settings.pat.trim()
            fun requirePat(): String = configuredPat.ifBlank { error("PAT 未配置，无法执行 GitHub 工具调用") }
            when (toolName.trim()) {
                "repo.list_tree" -> handleRepoListTree(workspace, requirePat(), args)
                "repo.list_dir" -> handleRepoListDir(workspace, requirePat(), args)
                "repo.read_file" -> handleRepoReadFile(workspace, requirePat(), args)
                "repo.search" -> handleRepoSearch(workspace, requirePat(), args)
                "repo.get_file_meta" -> handleRepoFileMeta(workspace, requirePat(), args)
                "repo.list_branches" -> handleRepoListBranches(workspace, requirePat(), args)
                "repo.create_branch" -> handleRepoCreateBranch(sessionId, workspace, requirePat(), args)
                "repo.replace_range" -> handleRepoReplaceRange(sessionId, workspace, requirePat(), args)
                "repo.apply_patch" -> handleRepoApplyPatch(sessionId, workspace, requirePat(), args)
                "repo.commit_push" -> handleRepoCommitPush(sessionId, workspace, requirePat(), args)
                "repo.create_pr" -> handleRepoCreatePr(workspace, requirePat(), args)
                "repo.comment_pr" -> handleRepoCommentPr(workspace, requirePat(), args)
                "repo.merge_pr" -> handleRepoMergePr(workspace, requirePat(), args)
                "actions.trigger_workflow" -> handleActionsTriggerWorkflow(workspace, requirePat(), args)
                "actions.get_run" -> handleActionsGetRun(workspace, requirePat(), args)
                "actions.get_latest_run" -> handleActionsGetLatestRun(workspace, requirePat(), args)
                "actions.get_logs_summary" -> handleActionsLogSummary(workspace, requirePat(), args)
                "actions.list_artifacts" -> handleActionsListArtifacts(workspace, requirePat(), args)
                "actions.download_artifact" -> handleActionsDownloadArtifact(workspace, requirePat(), args)
                "pages.get_settings" -> handlePagesGetSettings(workspace, requirePat())
                "pages.enable" -> handlePagesEnable(workspace, requirePat(), args)
                "pages.set_source" -> handlePagesSetSource(workspace, requirePat(), args)
                "pages.get_deployments" -> handlePagesDeployments(workspace, requirePat())
                "pages.get_latest_url" -> handlePagesLatestUrl(workspace, requirePat())
                "pages.deploy" -> handlePagesDeploy(workspace, requirePat())
                "policy.get_toolspec" -> handlePolicyGetToolspec()
                "policy.check_risk" -> handlePolicyCheckRisk(args)
                "mcp.list_servers" -> handleMcpListServers(args)
                "mcp.list_tools" -> handleMcpListTools(args)
                "mcp.call_tool" -> handleMcpCallTool(args)
                else -> error("不支持的工具: $toolName")
            }
        }

        val finishedAt = System.currentTimeMillis()
        val stored =
            call.copy(
                status = if (result.isSuccess) "success" else "error",
                endedAt = finishedAt,
                result = result.getOrNull()?.resultJson,
                error = result.exceptionOrNull()?.message
            )
        repository.upsertZiCodeToolCall(stored)

        return result.getOrElse { throwable ->
            ZiCodeToolDispatchResult(
                success = false,
                error = throwable.message ?: "工具执行失败",
                userHint = call.userHint
            )
        }
    }

    private fun parseArgs(argsJson: String): JsonObject {
        val raw = argsJson.trim().ifBlank { "{}" }
        return runCatching { gson.fromJson(raw, JsonObject::class.java) }
            .getOrNull()
            ?: JsonObject()
    }

    private fun handleRepoListTree(workspace: ZiCodeWorkspace, pat: String, args: JsonObject): ZiCodeToolDispatchResult {
        val ref =
            args.stringOrDefault("ref", workspace.defaultBranch)
                .trim()
                .ifBlank { workspace.defaultBranch.ifBlank { "main" } }
        val tree =
            runCatching {
                getGitTree(workspace, pat, ref, recursive = true)
            }.getOrElse { throwable ->
                if (isRepositoryEmptyError(throwable.message)) {
                    runCatching {
                        initializeRepositoryIfEmpty(workspace, pat, ref)
                    }.getOrElse { initError ->
                        error(composeRepositoryInitFailure(initError.message))
                    }
                    getGitTree(workspace, pat, ref, recursive = true)
                } else {
                    throw throwable
                }
            }
        return ZiCodeToolDispatchResult(success = true, resultJson = gson.toJson(tree), userHint = "📂 正在读取仓库文件结构…")
    }

    private fun handleRepoListDir(workspace: ZiCodeWorkspace, pat: String, args: JsonObject): ZiCodeToolDispatchResult {
        val ref =
            args.stringOrDefault("ref", workspace.defaultBranch)
                .trim()
                .ifBlank { workspace.defaultBranch.ifBlank { "main" } }
        val path = args.stringOrDefault("path", "")
        val list =
            runCatching {
                listDirectory(workspace, pat, ref, path)
            }.getOrElse { throwable ->
                if (isRepositoryEmptyError(throwable.message)) {
                    runCatching {
                        initializeRepositoryIfEmpty(workspace, pat, ref)
                    }.getOrElse { initError ->
                        error(composeRepositoryInitFailure(initError.message))
                    }
                    listDirectory(workspace, pat, ref, path)
                } else {
                    throw throwable
                }
            }
        return ZiCodeToolDispatchResult(success = true, resultJson = gson.toJson(list), userHint = "📁 正在浏览目录…")
    }

    private fun handleRepoReadFile(workspace: ZiCodeWorkspace, pat: String, args: JsonObject): ZiCodeToolDispatchResult {
        val ref = args.stringOrDefault("ref", workspace.defaultBranch)
        val path = args.requireString("path")
        val file = readFile(workspace, pat, ref, path)
        return ZiCodeToolDispatchResult(success = true, resultJson = gson.toJson(file), userHint = "📄 正在读取文件 `$path`…")
    }

    private fun handleRepoSearch(workspace: ZiCodeWorkspace, pat: String, args: JsonObject): ZiCodeToolDispatchResult {
        val keyword = args.requireString("keyword")
        val perPage = args.intOrDefault("per_page", 20).coerceIn(1, 100)
        val result = searchCode(workspace, pat, keyword, perPage)
        return ZiCodeToolDispatchResult(success = true, resultJson = gson.toJson(result), userHint = "🔍 正在搜索关键字 `$keyword`…")
    }

    private fun handleRepoFileMeta(workspace: ZiCodeWorkspace, pat: String, args: JsonObject): ZiCodeToolDispatchResult {
        val ref = args.stringOrDefault("ref", workspace.defaultBranch)
        val path = args.requireString("path")
        val meta = getFileMeta(workspace, pat, ref, path)
        return ZiCodeToolDispatchResult(success = true, resultJson = gson.toJson(meta), userHint = "ℹ️ 正在获取文件信息…")
    }

    private fun handleRepoListBranches(workspace: ZiCodeWorkspace, pat: String, args: JsonObject): ZiCodeToolDispatchResult {
        val perPage = args.intOrDefault("per_page", 100).coerceIn(1, 100)
        val branches = listBranches(workspace, pat, perPage)
        return ZiCodeToolDispatchResult(
            success = true,
            resultJson = gson.toJson(branches),
            userHint = "🌿 正在读取分支列表…"
        )
    }

    private suspend fun handleRepoCreateBranch(
        sessionId: String,
        workspace: ZiCodeWorkspace,
        pat: String,
        args: JsonObject
    ): ZiCodeToolDispatchResult {
        val baseRef = args.stringOrDefault("base", workspace.defaultBranch)
        val requestedBranch = args.stringOrDefault("branch", "").trim()
        val resolvedBranch =
            requestedBranch.ifBlank { "ai/task-${System.currentTimeMillis()}" }

        val result = createBranch(workspace, pat, resolvedBranch, baseRef)

        // Persist the resolved branch to the current session so subsequent calls reuse it.
        val existing = repository.zicodeSessionsFlow.first().firstOrNull { it.id == sessionId }
        if (existing != null && existing.branchName.isNullOrBlank()) {
            repository.upsertZiCodeSession(
                existing.copy(
                    branchName = resolvedBranch,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }

        val payload =
            JsonObject().apply {
                addProperty("branch", resolvedBranch)
                add("ref", result)
            }
        return ZiCodeToolDispatchResult(
            success = true,
            resultJson = gson.toJson(payload),
            userHint = "🌿 正在创建分支 `$resolvedBranch`…"
        )
    }

    private suspend fun handleRepoReplaceRange(
        sessionId: String,
        workspace: ZiCodeWorkspace,
        pat: String,
        args: JsonObject
    ): ZiCodeToolDispatchResult {
        val ref = args.stringOrDefault("ref", workspace.defaultBranch)
        val path = args.requireString("path")
        val startLine = args.requireInt("start_line")
        val endLine = args.requireInt("end_line")
        val replacement = args.stringOrDefault("replacement", "")

        val content = getWorkingFileContent(sessionId, workspace, pat, ref, path)
        val lines = content.split("\n").toMutableList()
        val startIndex = (startLine - 1).coerceAtLeast(0)
        val endIndex = endLine.coerceAtLeast(startLine)
        if (lines.isEmpty() && startIndex > 0) error("行号超出范围")
        if (startIndex > lines.lastIndex + 1) error("start_line 超出范围")

        val safeEndExclusive = min(max(endIndex, startLine), lines.size)
        val replacementLines = if (replacement.isEmpty()) emptyList() else replacement.split("\n")
        val before = lines.take(startIndex)
        val after = lines.drop(safeEndExclusive)
        val merged = (before + replacementLines + after).joinToString("\n")
        stageFile(sessionId, path, merged)

        val result = JsonObject().apply {
            addProperty("path", path)
            addProperty("staged", true)
            addProperty("start_line", startLine)
            addProperty("end_line", endLine)
        }
        return ZiCodeToolDispatchResult(success = true, resultJson = gson.toJson(result), userHint = "✏️ 正在修改文件 `$path`…")
    }

    private suspend fun handleRepoApplyPatch(
        sessionId: String,
        workspace: ZiCodeWorkspace,
        pat: String,
        args: JsonObject
    ): ZiCodeToolDispatchResult {
        val ref = args.stringOrDefault("ref", workspace.defaultBranch)
        val patch = args.requireString("patch")
        val parsedFiles = parseUnifiedDiffFiles(patch)
        if (parsedFiles.isEmpty()) {
            error("补丁内容为空或格式不正确")
        }

        val summary = JsonArray()
        parsedFiles.forEach { filePatch ->
            when (filePatch.operation) {
                "delete" -> {
                    stageFile(sessionId, filePatch.path, null)
                }
                "add" -> {
                    val patched = applyUnifiedDiffToText("", filePatch.hunks)
                    stageFile(sessionId, filePatch.path, patched)
                }
                else -> {
                    val original = getWorkingFileContent(sessionId, workspace, pat, ref, filePatch.path)
                    val patched = applyUnifiedDiffToText(original, filePatch.hunks)
                    stageFile(sessionId, filePatch.path, patched)
                }
            }
            summary.add(
                JsonObject().apply {
                    addProperty("path", filePatch.path)
                    addProperty("operation", filePatch.operation)
                }
            )
        }

        val result = JsonObject().apply {
            add("files", summary)
            addProperty("staged", true)
        }
        return ZiCodeToolDispatchResult(success = true, resultJson = gson.toJson(result), userHint = "🩹 正在应用代码补丁…")
    }

    private suspend fun handleRepoCommitPush(
        sessionId: String,
        workspace: ZiCodeWorkspace,
        pat: String,
        args: JsonObject
    ): ZiCodeToolDispatchResult {
        val message = args.stringOrDefault("message", "ZiCode update")
        val session = repository.zicodeSessionsFlow.first().firstOrNull { it.id == sessionId }
            ?: error("会话不存在，无法提交")

        val changes = stageMutex.withLock { stagedChangesBySession[sessionId]?.toMap().orEmpty() }
        if (changes.isEmpty()) error("没有可提交的暂存变更")

        val branchName = session.branchName?.trim().takeUnless { it.isNullOrBlank() }
            ?: "ai/${sessionId.take(8)}-${System.currentTimeMillis().toString().takeLast(6)}"

        ensureBranch(workspace, pat, branchName, workspace.defaultBranch)

        val headSha = getBranchHeadSha(workspace, pat, branchName)
        val baseTreeSha = getCommitTreeSha(workspace, pat, headSha)
        val treeItems = JsonArray()

        changes.forEach { (path, content) ->
            if (content == null) {
                treeItems.add(
                    JsonObject().apply {
                        addProperty("path", path)
                        add("sha", JsonNull.INSTANCE)
                    }
                )
            } else {
                val blobSha = createBlob(workspace, pat, content)
                treeItems.add(
                    JsonObject().apply {
                        addProperty("path", path)
                        addProperty("mode", "100644")
                        addProperty("type", "blob")
                        addProperty("sha", blobSha)
                    }
                )
            }
        }

        val treeSha = createTree(workspace, pat, baseTreeSha, treeItems)
        val commitSha = createCommit(workspace, pat, message, treeSha, headSha)
        updateBranchRef(workspace, pat, branchName, commitSha)

        repository.upsertZiCodeSession(session.copy(branchName = branchName, updatedAt = System.currentTimeMillis()))
        stageMutex.withLock { stagedChangesBySession.remove(sessionId) }

        val result = JsonObject().apply {
            addProperty("branch", branchName)
            addProperty("commit_sha", commitSha)
            addProperty("changed_files", changes.size)
        }
        return ZiCodeToolDispatchResult(success = true, resultJson = gson.toJson(result), userHint = "📤 正在提交并推送代码…")
    }

    private fun handleRepoCreatePr(workspace: ZiCodeWorkspace, pat: String, args: JsonObject): ZiCodeToolDispatchResult {
        val head = args.requireString("head")
        val base = args.stringOrDefault("base", workspace.defaultBranch)
        val title = args.requireString("title")
        val body = args.stringOrDefault("body", "")
        val pr = createPullRequest(workspace, pat, title, head, base, body)
        return ZiCodeToolDispatchResult(success = true, resultJson = gson.toJson(pr), userHint = "📋 正在创建 Pull Request…")
    }

    private fun handleRepoCommentPr(workspace: ZiCodeWorkspace, pat: String, args: JsonObject): ZiCodeToolDispatchResult {
        val prNumber = args.requireInt("pr_number")
        val body = args.requireString("body")
        val comment = createIssueComment(workspace, pat, prNumber, body)
        return ZiCodeToolDispatchResult(success = true, resultJson = gson.toJson(comment), userHint = "💬 正在写入 PR 评论…")
    }

    private fun handleRepoMergePr(workspace: ZiCodeWorkspace, pat: String, args: JsonObject): ZiCodeToolDispatchResult {
        val prNumber = args.requireInt("pr_number")
        val method = args.stringOrDefault("merge_method", "squash")
        val merge = mergePullRequest(workspace, pat, prNumber, method)
        return ZiCodeToolDispatchResult(success = true, resultJson = gson.toJson(merge), userHint = "🔀 正在合并 Pull Request…")
    }

    private fun handleActionsTriggerWorkflow(
        workspace: ZiCodeWorkspace,
        pat: String,
        args: JsonObject
    ): ZiCodeToolDispatchResult {
        val workflow = args.requireString("workflow")
        val ref = args.stringOrDefault("ref", workspace.defaultBranch)
        val inputs = args.getAsJsonObject("inputs") ?: JsonObject()
        triggerWorkflowDispatch(workspace, pat, workflow, ref, inputs)
        val result = JsonObject().apply {
            addProperty("workflow", workflow)
            addProperty("ref", ref)
            addProperty("dispatched", true)
        }
        return ZiCodeToolDispatchResult(success = true, resultJson = gson.toJson(result), userHint = "🚀 正在运行工作流 `$workflow`…")
    }

    private fun handleActionsGetRun(workspace: ZiCodeWorkspace, pat: String, args: JsonObject): ZiCodeToolDispatchResult {
        val runId = args.requireLong("run_id")
        val run = getWorkflowRun(workspace, pat, runId)
        return ZiCodeToolDispatchResult(success = true, resultJson = gson.toJson(run), userHint = "⏳ 正在检查构建状态…")
    }

    private fun handleActionsGetLatestRun(workspace: ZiCodeWorkspace, pat: String, args: JsonObject): ZiCodeToolDispatchResult {
        val workflow = args.stringOrDefault("workflow", "")
        val branch = args.stringOrDefault("branch", "")
        val latest = getLatestWorkflowRun(workspace, pat, workflow, branch)
        return ZiCodeToolDispatchResult(
            success = true,
            resultJson = gson.toJson(latest),
            userHint = "⏳ 正在获取最新构建状态…"
        )
    }

    private fun handleActionsLogSummary(workspace: ZiCodeWorkspace, pat: String, args: JsonObject): ZiCodeToolDispatchResult {
        val runId = args.requireLong("run_id")
        val summary = buildRunLogsSummary(workspace, pat, runId)
        return ZiCodeToolDispatchResult(success = true, resultJson = gson.toJson(summary), userHint = "📊 正在分析构建日志…")
    }

    private fun handleActionsListArtifacts(workspace: ZiCodeWorkspace, pat: String, args: JsonObject): ZiCodeToolDispatchResult {
        val runId = args.requireLong("run_id")
        val artifacts = listRunArtifacts(workspace, pat, runId)
        return ZiCodeToolDispatchResult(success = true, resultJson = gson.toJson(artifacts), userHint = "📦 正在获取构建产物列表…")
    }

    private fun handleActionsDownloadArtifact(
        workspace: ZiCodeWorkspace,
        pat: String,
        args: JsonObject
    ): ZiCodeToolDispatchResult {
        val artifactId = args.requireLong("artifact_id")
        val artifact = getArtifactDownloadInfo(workspace, pat, artifactId)
        return ZiCodeToolDispatchResult(success = true, resultJson = gson.toJson(artifact), userHint = "⬇️ 正在下载构建产物…")
    }

    private fun handlePagesGetSettings(workspace: ZiCodeWorkspace, pat: String): ZiCodeToolDispatchResult {
        val pages = getPagesSettings(workspace, pat)
        return ZiCodeToolDispatchResult(success = true, resultJson = gson.toJson(pages), userHint = "⚙️ 正在读取 Pages 配置…")
    }

    private fun handlePagesEnable(workspace: ZiCodeWorkspace, pat: String, args: JsonObject): ZiCodeToolDispatchResult {
        val branch = args.stringOrDefault("branch", workspace.defaultBranch)
        val path = args.stringOrDefault("path", "/")
        val pages = createPagesSite(workspace, pat, branch, path)
        return ZiCodeToolDispatchResult(success = true, resultJson = gson.toJson(pages), userHint = "🌐 正在启用 GitHub Pages…")
    }

    private fun handlePagesSetSource(workspace: ZiCodeWorkspace, pat: String, args: JsonObject): ZiCodeToolDispatchResult {
        val branch = args.stringOrDefault("branch", workspace.defaultBranch)
        val path = args.stringOrDefault("path", "/")
        val pages = updatePagesSource(workspace, pat, branch, path)
        return ZiCodeToolDispatchResult(success = true, resultJson = gson.toJson(pages), userHint = "🔧 正在设置 Pages 构建来源…")
    }

    private fun handlePagesDeployments(workspace: ZiCodeWorkspace, pat: String): ZiCodeToolDispatchResult {
        val deployments = getPagesDeployments(workspace, pat)
        return ZiCodeToolDispatchResult(success = true, resultJson = gson.toJson(deployments), userHint = "📜 正在获取部署记录…")
    }

    private fun handlePagesLatestUrl(workspace: ZiCodeWorkspace, pat: String): ZiCodeToolDispatchResult {
        val pages = getPagesSettings(workspace, pat)
        val result = JsonObject().apply {
            addProperty("url", pages.get("html_url")?.asString.orEmpty())
            add("settings", pages)
        }
        return ZiCodeToolDispatchResult(success = true, resultJson = gson.toJson(result), userHint = "🔗 正在获取在线地址…")
    }

    private fun handlePagesDeploy(workspace: ZiCodeWorkspace, pat: String): ZiCodeToolDispatchResult {
        val build = triggerPagesBuild(workspace, pat)
        return ZiCodeToolDispatchResult(success = true, resultJson = gson.toJson(build), userHint = "🚀 正在部署到 GitHub Pages…")
    }

    private fun handlePolicyGetToolspec(): ZiCodeToolDispatchResult {
        val toolspec = policyService.getToolspec()
        return ZiCodeToolDispatchResult(
            success = true,
            resultJson = gson.toJson(toolspec),
            userHint = "📖 正在加载能力说明…"
        )
    }

    private fun handlePolicyCheckRisk(args: JsonObject): ZiCodeToolDispatchResult {
        val patchText = args.stringOrDefault("patch", "")
        val touchedPaths =
            args.getAsJsonArray("touched_paths")
                ?.mapNotNull { it.asString?.trim()?.takeIf(String::isNotBlank) }
                .orEmpty()
        val risk = policyService.checkRisk(patchText, touchedPaths)
        return ZiCodeToolDispatchResult(
            success = true,
            resultJson = gson.toJson(risk),
            userHint = "🛡️ 正在评估补丁风险…"
        )
    }

    private suspend fun handleMcpListServers(args: JsonObject): ZiCodeToolDispatchResult {
        val includeDisabled = args.boolOrDefault("include_disabled", false)
        val servers =
            repository.mcpListFlow.first()
                .filter { includeDisabled || it.enabled }
                .map { server ->
                    JsonObject().apply {
                        addProperty("id", server.id)
                        addProperty("name", server.name)
                        addProperty("url", server.url)
                        addProperty("protocol", server.protocol.name)
                        addProperty("enabled", server.enabled)
                        addProperty("tool_count", server.tools.size)
                    }
                }
        val result = JsonObject().apply {
            add("servers", JsonArray().also { arr -> servers.forEach { arr.add(it) } })
            addProperty("count", servers.size)
        }
        return ZiCodeToolDispatchResult(
            success = true,
            resultJson = gson.toJson(result),
            userHint = "🧩 正在获取 MCP 服务列表…"
        )
    }

    private suspend fun handleMcpListTools(args: JsonObject): ZiCodeToolDispatchResult {
        val server = resolveMcpServer(args) ?: error("未找到 MCP 服务，请提供 server_id / server_name")
        val useCached = args.boolOrDefault("use_cached", true)
        val tools =
            if (useCached && server.tools.isNotEmpty()) {
                server.tools
            } else {
                val fetched = mcpClient.fetchTools(server).getOrElse { throwable ->
                    error(throwable.message ?: "MCP 工具同步失败")
                }
                repository.updateMcpTools(server.id, fetched)
                fetched
            }

        val result = JsonObject().apply {
            addProperty("server_id", server.id)
            addProperty("server_name", server.name)
            add("tools", gson.toJsonTree(tools))
            addProperty("count", tools.size)
        }
        return ZiCodeToolDispatchResult(
            success = true,
            resultJson = gson.toJson(result),
            userHint = "🧰 正在获取 MCP 工具列表…"
        )
    }

    private suspend fun handleMcpCallTool(args: JsonObject): ZiCodeToolDispatchResult {
        val server = resolveMcpServer(args) ?: error("未找到 MCP 服务，请提供 server_id / server_name")
        val toolName =
            args.stringOrDefault("tool_name", "").ifBlank {
                args.stringOrDefault("toolName", "")
            }.ifBlank {
                error("参数 tool_name 不能为空")
            }
        val argumentsObj = args.getAsJsonObject("arguments") ?: args.getAsJsonObject("args") ?: JsonObject()
        val argumentMap = argumentsObj.toAnyMap()
        val callResult =
            mcpClient.callTool(
                config = server,
                toolCall = McpToolCall(toolName = toolName, arguments = argumentMap)
            ).getOrElse { throwable ->
                error(throwable.message ?: "MCP 工具调用失败")
            }

        val result = JsonObject().apply {
            addProperty("server_id", server.id)
            addProperty("server_name", server.name)
            addProperty("tool_name", toolName)
            addProperty("success", callResult.success)
            addProperty("content", callResult.content)
            addProperty("error", callResult.error)
        }
        return ZiCodeToolDispatchResult(
            success = true,
            resultJson = gson.toJson(result),
            userHint = "🛠️ 正在调用 MCP 工具 `$toolName`…"
        )
    }

    private suspend fun getWorkingFileContent(
        sessionId: String,
        workspace: ZiCodeWorkspace,
        pat: String,
        ref: String,
        path: String
    ): String {
        val staged = stageMutex.withLock { stagedChangesBySession[sessionId]?.get(path) }
        if (staged != null) return staged
        return readFile(workspace, pat, ref, path).content
    }

    private suspend fun stageFile(sessionId: String, path: String, content: String?) {
        stageMutex.withLock {
            val map = stagedChangesBySession.getOrPut(sessionId) { linkedMapOf() }
            map[path] = content
        }
    }

    private fun buildUserHint(toolName: String, argsJson: String): String {
        val args = parseArgs(argsJson)
        return when (toolName.trim()) {
            "repo.list_tree" -> "📂 正在读取仓库文件结构…"
            "repo.list_dir" -> "📁 正在浏览目录…"
            "repo.read_file" -> "📄 正在读取文件 `${args.stringOrDefault("path", "")}`…"
            "repo.search" -> "🔍 正在搜索关键字 `${args.stringOrDefault("keyword", "")}`…"
            "repo.get_file_meta" -> "ℹ️ 正在获取文件信息…"
            "repo.list_branches" -> "🌿 正在读取分支列表…"
            "repo.create_branch" -> {
                val branch = args.stringOrDefault("branch", "")
                if (branch.isBlank()) "🌿 正在创建分支…" else "🌿 正在创建分支 `$branch`…"
            }
            "repo.apply_patch" -> "🩹 正在应用代码补丁…"
            "repo.replace_range" -> "✏️ 正在修改文件 `${args.stringOrDefault("path", "")}`…"
            "repo.commit_push" -> "📤 正在提交并推送代码…"
            "repo.create_pr" -> "📋 正在创建 Pull Request…"
            "repo.comment_pr" -> "💬 正在写入 PR 评论…"
            "repo.merge_pr" -> "🔀 正在合并 Pull Request…"
            "actions.trigger_workflow" -> "🚀 正在运行工作流 `${args.stringOrDefault("workflow", "")}`…"
            "actions.get_run" -> "⏳ 正在检查构建状态…"
            "actions.get_latest_run" -> "⏳ 正在获取最新构建状态…"
            "actions.get_logs_summary" -> "📊 正在分析构建日志…"
            "actions.list_artifacts" -> "📦 正在获取构建产物列表…"
            "actions.download_artifact" -> "⬇️ 正在下载构建产物…"
            "pages.get_settings" -> "⚙️ 正在读取 Pages 配置…"
            "pages.enable" -> "🌐 正在启用 GitHub Pages…"
            "pages.set_source" -> "🔧 正在设置 Pages 构建来源…"
            "pages.get_deployments" -> "📜 正在获取部署记录…"
            "pages.get_latest_url" -> "🔗 正在获取在线地址…"
            "pages.deploy" -> "🚀 正在部署到 GitHub Pages…"
            "policy.get_toolspec" -> "📖 正在加载能力说明…"
            "policy.check_risk" -> "🛡️ 正在评估补丁风险…"
            "mcp.list_servers" -> "🧩 正在获取 MCP 服务列表…"
            "mcp.list_tools" -> "🧰 正在获取 MCP 工具列表…"
            "mcp.call_tool" -> "🛠️ 正在调用 MCP 工具…"
            else -> "⏳ 正在执行工具调用…"
        }
    }

    private suspend fun resolveMcpServer(args: JsonObject): McpConfig? {
        val serverId =
            args.stringOrDefault("server_id", "").ifBlank {
                args.stringOrDefault("serverId", "")
            }
        val serverName =
            args.stringOrDefault("server_name", "").ifBlank {
                args.stringOrDefault("serverName", "")
            }
        val servers = repository.mcpListFlow.first()
        return when {
            serverId.isNotBlank() -> servers.firstOrNull { it.id == serverId }
            serverName.isNotBlank() -> servers.firstOrNull { it.name.equals(serverName, ignoreCase = true) }
            else -> servers.firstOrNull { it.enabled } ?: servers.firstOrNull()
        }
    }

    private fun getGitTree(workspace: ZiCodeWorkspace, pat: String, branch: String, recursive: Boolean): JsonObject {
        val headSha = getBranchHeadSha(workspace, pat, branch)
        val treeSha = getCommitTreeSha(workspace, pat, headSha)
        val url =
            "https://api.github.com/repos/${workspace.owner}/${workspace.repo}/git/trees/$treeSha" +
                if (recursive) "?recursive=1" else ""
        return getJson(url, pat).asJsonObject
    }

    private fun listDirectory(workspace: ZiCodeWorkspace, pat: String, ref: String, path: String): JsonElement {
        val encodedPath = encodePath(path.trim())
        val base = "https://api.github.com/repos/${workspace.owner}/${workspace.repo}/contents"
        val url = if (encodedPath.isBlank()) "$base?ref=${urlEncode(ref)}" else "$base/$encodedPath?ref=${urlEncode(ref)}"
        return getJson(url, pat)
    }

    private fun readFile(workspace: ZiCodeWorkspace, pat: String, ref: String, path: String): RepoFileContent {
        val encodedPath = encodePath(path)
        val url = "https://api.github.com/repos/${workspace.owner}/${workspace.repo}/contents/$encodedPath?ref=${urlEncode(ref)}"
        val obj = getJson(url, pat).asJsonObject
        val encoded = obj.get("content")?.asString.orEmpty().replace("\n", "")
        val bytes = Base64.getDecoder().decode(encoded)
        val content = String(bytes, StandardCharsets.UTF_8)
        return RepoFileContent(
            path = path,
            sha = obj.get("sha")?.asString.orEmpty(),
            size = obj.get("size")?.asInt ?: content.length,
            content = content
        )
    }

    private fun getFileMeta(workspace: ZiCodeWorkspace, pat: String, ref: String, path: String): RepoFileMeta {
        val encodedPath = encodePath(path)
        val url = "https://api.github.com/repos/${workspace.owner}/${workspace.repo}/contents/$encodedPath?ref=${urlEncode(ref)}"
        val obj = getJson(url, pat).asJsonObject
        return RepoFileMeta(
            path = obj.get("path")?.asString.orEmpty().ifBlank { path },
            sha = obj.get("sha")?.asString.orEmpty(),
            size = obj.get("size")?.asInt ?: 0,
            type = obj.get("type")?.asString.orEmpty()
        )
    }

    private fun searchCode(workspace: ZiCodeWorkspace, pat: String, keyword: String, perPage: Int): JsonObject {
        val query = "$keyword repo:${workspace.owner}/${workspace.repo}"
        val url = "https://api.github.com/search/code?q=${urlEncode(query)}&per_page=$perPage"
        return getJson(url, pat).asJsonObject
    }

    private fun createBranch(workspace: ZiCodeWorkspace, pat: String, branch: String, baseRef: String): JsonObject {
        val targetBranch = branch.trim().ifBlank { error("分支名不能为空") }
        val sourceBranch = baseRef.trim().ifBlank { workspace.defaultBranch.ifBlank { "main" } }
        val baseSha =
            runCatching { getBranchHeadSha(workspace, pat, sourceBranch) }
                .getOrElse { throwable ->
                    if (isRepositoryEmptyError(throwable.message)) {
                        initializeRepositoryIfEmpty(workspace, pat, sourceBranch)
                        getBranchHeadSha(workspace, pat, sourceBranch)
                    } else {
                        throw throwable
                    }
                }

        if (targetBranch.equals(sourceBranch, ignoreCase = true)) {
            return getBranchRef(workspace, pat, sourceBranch)
        }

        val body = JsonObject().apply {
            addProperty("ref", "refs/heads/$targetBranch")
            addProperty("sha", baseSha)
        }
        val url = "https://api.github.com/repos/${workspace.owner}/${workspace.repo}/git/refs"
        return runCatching { postJson(url, pat, body) }
            .getOrElse { throwable ->
                if (isReferenceAlreadyExists(throwable.message)) {
                    getBranchRef(workspace, pat, targetBranch)
                } else {
                    throw throwable
                }
            }
    }

    private fun ensureBranch(workspace: ZiCodeWorkspace, pat: String, branch: String, baseRef: String) {
        runCatching {
            getBranchHeadSha(workspace, pat, branch)
        }.getOrElse {
            createBranch(workspace, pat, branch, baseRef)
        }
    }

    private fun createBlob(workspace: ZiCodeWorkspace, pat: String, content: String): String {
        val body = JsonObject().apply {
            addProperty("content", content)
            addProperty("encoding", "utf-8")
        }
        val url = "https://api.github.com/repos/${workspace.owner}/${workspace.repo}/git/blobs"
        return postJson(url, pat, body).get("sha")?.asString.orEmpty().ifBlank { error("创建 blob 失败") }
    }

    private fun createTree(
        workspace: ZiCodeWorkspace,
        pat: String,
        baseTreeSha: String?,
        treeItems: JsonArray
    ): String {
        val body = JsonObject().apply {
            baseTreeSha?.trim()?.takeIf { it.isNotBlank() }?.let { addProperty("base_tree", it) }
            add("tree", treeItems)
        }
        val url = "https://api.github.com/repos/${workspace.owner}/${workspace.repo}/git/trees"
        return postJson(url, pat, body).get("sha")?.asString.orEmpty().ifBlank { error("创建 tree 失败") }
    }

    private fun createCommit(
        workspace: ZiCodeWorkspace,
        pat: String,
        message: String,
        treeSha: String,
        parentSha: String?
    ): String {
        val body = JsonObject().apply {
            addProperty("message", message)
            addProperty("tree", treeSha)
            val parents = JsonArray()
            parentSha?.trim()?.takeIf { it.isNotBlank() }?.let { parents.add(it) }
            add("parents", parents)
        }
        val url = "https://api.github.com/repos/${workspace.owner}/${workspace.repo}/git/commits"
        return postJson(url, pat, body).get("sha")?.asString.orEmpty().ifBlank { error("创建 commit 失败") }
    }

    private fun getBranchRef(workspace: ZiCodeWorkspace, pat: String, branch: String): JsonObject {
        val url = "https://api.github.com/repos/${workspace.owner}/${workspace.repo}/git/ref/heads/${urlEncode(branch)}"
        return getJson(url, pat).asJsonObject
    }

    private fun initializeRepositoryIfEmpty(
        workspace: ZiCodeWorkspace,
        pat: String,
        branch: String
    ): JsonObject {
        val primaryBranch = workspace.defaultBranch.trim().ifBlank { "main" }
        val requestedBranch = branch.trim().ifBlank { primaryBranch }
        return runCatching {
            initializeRepositoryByGitData(
                workspace = workspace,
                pat = pat,
                primaryBranch = primaryBranch,
                requestedBranch = requestedBranch
            )
        }.getOrElse { gitDataError ->
            runCatching {
                initializeRepositoryByContentsApi(
                    workspace = workspace,
                    pat = pat,
                    primaryBranch = primaryBranch,
                    requestedBranch = requestedBranch
                )
            }.getOrElse { contentsError ->
                val errorMessage = contentsError.message.orEmpty().ifBlank { gitDataError.message.orEmpty() }
                error(composeRepositoryInitFailure(errorMessage))
            }
        }
    }

    private fun initializeRepositoryByGitData(
        workspace: ZiCodeWorkspace,
        pat: String,
        primaryBranch: String,
        requestedBranch: String
    ): JsonObject {
        val initContent = buildInitialRepoContent(workspace)
        val blobSha = createBlob(workspace, pat, initContent)
        val treeItems = JsonArray().apply {
            add(
                JsonObject().apply {
                    addProperty("path", "README.md")
                    addProperty("mode", "100644")
                    addProperty("type", "blob")
                    addProperty("sha", blobSha)
                }
            )
        }
        val treeSha = createTree(workspace, pat, baseTreeSha = null, treeItems = treeItems)
        val commitSha = createCommit(
            workspace = workspace,
            pat = pat,
            message = "chore: initialize repository for ZiCode",
            treeSha = treeSha,
            parentSha = null
        )
        val primaryRef = createOrGetRef(workspace, pat, primaryBranch, commitSha)
        val requestedRef =
            if (requestedBranch.equals(primaryBranch, ignoreCase = true)) {
                primaryRef
            } else {
                createOrGetRef(workspace, pat, requestedBranch, commitSha)
            }
        return JsonObject().apply {
            addProperty("initialized", true)
            addProperty("branch", primaryBranch)
            addProperty("requested_branch", requestedBranch)
            addProperty("commit_sha", commitSha)
            add("ref", primaryRef)
            add("requested_ref", requestedRef)
        }
    }

    private fun initializeRepositoryByContentsApi(
        workspace: ZiCodeWorkspace,
        pat: String,
        primaryBranch: String,
        requestedBranch: String
    ): JsonObject {
        val initContent = buildInitialRepoContent(workspace)
        val commitSha =
            runCatching {
                putRepositoryFile(
                    workspace = workspace,
                    pat = pat,
                    path = "README.md",
                    content = initContent,
                    branch = primaryBranch,
                    message = "chore: initialize repository for ZiCode"
                )
            }.recoverCatching {
                putRepositoryFile(
                    workspace = workspace,
                    pat = pat,
                    path = "README.md",
                    content = initContent,
                    branch = null,
                    message = "chore: initialize repository for ZiCode"
                )
            }.getOrElse { throwable ->
                throw IllegalStateException(throwable.message ?: "initialize by contents api failed")
            }

        val primaryRef = createOrGetRef(workspace, pat, primaryBranch, commitSha)
        val requestedRef =
            if (requestedBranch.equals(primaryBranch, ignoreCase = true)) {
                primaryRef
            } else {
                createOrGetRef(workspace, pat, requestedBranch, commitSha)
            }

        return JsonObject().apply {
            addProperty("initialized", true)
            addProperty("branch", primaryBranch)
            addProperty("requested_branch", requestedBranch)
            addProperty("commit_sha", commitSha)
            add("ref", primaryRef)
            add("requested_ref", requestedRef)
        }
    }

    private fun createOrGetRef(
        workspace: ZiCodeWorkspace,
        pat: String,
        branch: String,
        commitSha: String
    ): JsonObject {
        val safeBranch = branch.trim().ifBlank { error("分支名不能为空") }
        val body = JsonObject().apply {
            addProperty("ref", "refs/heads/$safeBranch")
            addProperty("sha", commitSha)
        }
        val url = "https://api.github.com/repos/${workspace.owner}/${workspace.repo}/git/refs"
        return runCatching { postJson(url, pat, body) }.getOrElse { throwable ->
            if (isReferenceAlreadyExists(throwable.message)) {
                getBranchRef(workspace, pat, safeBranch)
            } else {
                throw throwable
            }
        }
    }

    private fun putRepositoryFile(
        workspace: ZiCodeWorkspace,
        pat: String,
        path: String,
        content: String,
        branch: String?,
        message: String
    ): String {
        val body = JsonObject().apply {
            addProperty("message", message)
            addProperty("content", Base64.getEncoder().encodeToString(content.toByteArray(StandardCharsets.UTF_8)))
            branch?.trim()?.takeIf { it.isNotBlank() }?.let { addProperty("branch", it) }
        }
        val encodedPath = encodePath(path)
        val url = "https://api.github.com/repos/${workspace.owner}/${workspace.repo}/contents/$encodedPath"
        val response = putJson(url, pat, body)
        return response.getAsJsonObject("commit")?.get("sha")?.asString.orEmpty().ifBlank {
            error("初始化提交失败")
        }
    }

    private fun buildInitialRepoContent(workspace: ZiCodeWorkspace): String {
        return buildString {
            appendLine("# ${workspace.repo}")
            appendLine()
            appendLine("Initialized by ZiCode.")
        }
    }

    private fun composeRepositoryInitFailure(rawMessage: String?): String {
        val normalized = rawMessage.orEmpty().trim().lowercase()
        return when {
            normalized.contains("resource not accessible") ||
                normalized.contains("permission") ||
                normalized.contains("403") ||
                normalized.contains("forbidden") ->
                "空仓库自动初始化失败：PAT 缺少仓库写入权限。"

            normalized.contains("not found") || normalized.contains("404") ->
                "空仓库自动初始化失败：仓库不可访问或权限不足。"

            normalized.contains("rate limit") || normalized.contains("timeout") ->
                "空仓库自动初始化失败：网络或 GitHub 限流，请稍后重试。"

            else ->
                "空仓库自动初始化失败，请确认 PAT 具备 repo 写入权限后重试。"
        }
    }

    private fun isRepositoryEmptyError(message: String?): Boolean {
        val normalized = message.orEmpty().lowercase()
        return normalized.contains("repository is empty") ||
            normalized.contains("git repository is empty") ||
            normalized.contains("this repository is empty")
    }

    private fun isReferenceAlreadyExists(message: String?): Boolean {
        return message.orEmpty().lowercase().contains("reference already exists")
    }

    private fun updateBranchRef(workspace: ZiCodeWorkspace, pat: String, branch: String, commitSha: String): JsonObject {
        val body = JsonObject().apply {
            addProperty("sha", commitSha)
            addProperty("force", false)
        }
        val url = "https://api.github.com/repos/${workspace.owner}/${workspace.repo}/git/refs/heads/${urlEncode(branch)}"
        return patchJson(url, pat, body)
    }

    private fun createPullRequest(
        workspace: ZiCodeWorkspace,
        pat: String,
        title: String,
        head: String,
        base: String,
        body: String
    ): JsonObject {
        val requestBody = JsonObject().apply {
            addProperty("title", title)
            addProperty("head", head)
            addProperty("base", base)
            addProperty("body", body)
        }
        val url = "https://api.github.com/repos/${workspace.owner}/${workspace.repo}/pulls"
        return postJson(url, pat, requestBody)
    }

    private fun createIssueComment(workspace: ZiCodeWorkspace, pat: String, prNumber: Int, body: String): JsonObject {
        val requestBody = JsonObject().apply { addProperty("body", body) }
        val url = "https://api.github.com/repos/${workspace.owner}/${workspace.repo}/issues/$prNumber/comments"
        return postJson(url, pat, requestBody)
    }

    private fun mergePullRequest(workspace: ZiCodeWorkspace, pat: String, prNumber: Int, method: String): JsonObject {
        val requestBody = JsonObject().apply { addProperty("merge_method", method.trim().ifBlank { "squash" }) }
        val url = "https://api.github.com/repos/${workspace.owner}/${workspace.repo}/pulls/$prNumber/merge"
        return putJson(url, pat, requestBody)
    }

    private fun listBranches(workspace: ZiCodeWorkspace, pat: String, perPage: Int): JsonObject {
        val url = "https://api.github.com/repos/${workspace.owner}/${workspace.repo}/branches?per_page=$perPage"
        val branches = getJson(url, pat)
        return JsonObject().apply {
            add("branches", branches)
            addProperty("count", branches.asJsonArray.size())
        }
    }

    private fun getBranchHeadSha(workspace: ZiCodeWorkspace, pat: String, branch: String): String {
        val url = "https://api.github.com/repos/${workspace.owner}/${workspace.repo}/git/ref/heads/${urlEncode(branch)}"
        val obj = getJson(url, pat).asJsonObject
        return obj.getAsJsonObject("object")?.get("sha")?.asString.orEmpty().ifBlank {
            error("无法读取分支 $branch 的 HEAD")
        }
    }

    private fun getCommitTreeSha(workspace: ZiCodeWorkspace, pat: String, commitSha: String): String {
        val url = "https://api.github.com/repos/${workspace.owner}/${workspace.repo}/git/commits/$commitSha"
        val obj = getJson(url, pat).asJsonObject
        return obj.getAsJsonObject("tree")?.get("sha")?.asString.orEmpty().ifBlank {
            error("无法读取 commit tree")
        }
    }

    private fun triggerWorkflowDispatch(
        workspace: ZiCodeWorkspace,
        pat: String,
        workflow: String,
        ref: String,
        inputs: JsonObject
    ) {
        val body = JsonObject().apply {
            addProperty("ref", ref)
            add("inputs", inputs)
        }
        val url = "https://api.github.com/repos/${workspace.owner}/${workspace.repo}/actions/workflows/${urlEncode(workflow)}/dispatches"
        postJson(url, pat, body)
    }

    private fun getWorkflowRun(workspace: ZiCodeWorkspace, pat: String, runId: Long): JsonObject {
        val url = "https://api.github.com/repos/${workspace.owner}/${workspace.repo}/actions/runs/$runId"
        return getJson(url, pat).asJsonObject
    }

    private fun getLatestWorkflowRun(
        workspace: ZiCodeWorkspace,
        pat: String,
        workflow: String,
        branch: String
    ): JsonObject {
        val workflowPath = workflow.trim().takeIf { it.isNotBlank() }?.let { "/workflows/${urlEncode(it)}" }.orEmpty()
        val branchQuery = branch.trim().takeIf { it.isNotBlank() }?.let { "&branch=${urlEncode(it)}" }.orEmpty()
        val url =
            "https://api.github.com/repos/${workspace.owner}/${workspace.repo}/actions$workflowPath/runs?per_page=1$branchQuery"
        val obj = getJson(url, pat).asJsonObject
        val runs = obj.getAsJsonArray("workflow_runs") ?: JsonArray()
        val latest = runs.firstOrNull()?.asJsonObject
        return JsonObject().apply {
            add("latest", latest ?: JsonObject())
            add("raw", obj)
        }
    }

    private fun buildRunLogsSummary(workspace: ZiCodeWorkspace, pat: String, runId: Long): JsonObject {
        val run = getWorkflowRun(workspace, pat, runId)
        val jobs = getRunJobs(workspace, pat, runId)
        val jobsArray = jobs.getAsJsonArray("jobs") ?: JsonArray()
        val failedJob = jobsArray.firstOrNull { item ->
            item.asJsonObject.get("conclusion")?.asString == "failure"
        }?.asJsonObject

        val logText =
            failedJob?.get("id")?.asLong?.let { jobId ->
                getJobLogs(workspace, pat, jobId)
            }.orEmpty()

        val report = parseRunReport(logText)
        return JsonObject().apply {
            add("run", run)
            add("jobs", jobs)
            add("report", gson.toJsonTree(report))
        }
    }

    private fun listRunArtifacts(workspace: ZiCodeWorkspace, pat: String, runId: Long): JsonObject {
        val url = "https://api.github.com/repos/${workspace.owner}/${workspace.repo}/actions/runs/$runId/artifacts"
        return getJson(url, pat).asJsonObject
    }

    private fun getArtifactDownloadInfo(workspace: ZiCodeWorkspace, pat: String, artifactId: Long): JsonObject {
        val url = "https://api.github.com/repos/${workspace.owner}/${workspace.repo}/actions/artifacts/$artifactId/zip"
        val response = headRequest(url, pat)
        return JsonObject().apply {
            addProperty("artifact_id", artifactId)
            addProperty("status_code", response.first)
            addProperty("download_url", response.second)
        }
    }

    private fun getPagesSettings(workspace: ZiCodeWorkspace, pat: String): JsonObject {
        val url = "https://api.github.com/repos/${workspace.owner}/${workspace.repo}/pages"
        return getJson(url, pat).asJsonObject
    }

    private fun createPagesSite(workspace: ZiCodeWorkspace, pat: String, branch: String, path: String): JsonObject {
        val body = JsonObject().apply {
            add("source", JsonObject().apply {
                addProperty("branch", branch)
                addProperty("path", path)
            })
        }
        val url = "https://api.github.com/repos/${workspace.owner}/${workspace.repo}/pages"
        return postJson(url, pat, body)
    }

    private fun updatePagesSource(workspace: ZiCodeWorkspace, pat: String, branch: String, path: String): JsonObject {
        val body = JsonObject().apply {
            add("source", JsonObject().apply {
                addProperty("branch", branch)
                addProperty("path", path)
            })
        }
        val url = "https://api.github.com/repos/${workspace.owner}/${workspace.repo}/pages"
        return putJson(url, pat, body)
    }

    private fun getPagesDeployments(workspace: ZiCodeWorkspace, pat: String): JsonObject {
        val url = "https://api.github.com/repos/${workspace.owner}/${workspace.repo}/pages/deployments"
        val deployments = getJson(url, pat)
        val count =
            when {
                deployments.isJsonArray -> deployments.asJsonArray.size()
                deployments.isJsonObject -> deployments.asJsonObject.entrySet().size
                else -> 0
            }
        return JsonObject().apply {
            add("deployments", deployments)
            addProperty("count", count)
        }
    }

    private fun triggerPagesBuild(workspace: ZiCodeWorkspace, pat: String): JsonObject {
        val body = JsonObject()
        val url = "https://api.github.com/repos/${workspace.owner}/${workspace.repo}/pages/builds"
        return postJson(url, pat, body)
    }

    private fun getRunJobs(workspace: ZiCodeWorkspace, pat: String, runId: Long): JsonObject {
        val url = "https://api.github.com/repos/${workspace.owner}/${workspace.repo}/actions/runs/$runId/jobs?per_page=100"
        return getJson(url, pat).asJsonObject
    }

    private fun getJobLogs(workspace: ZiCodeWorkspace, pat: String, jobId: Long): String {
        val url = "https://api.github.com/repos/${workspace.owner}/${workspace.repo}/actions/jobs/$jobId/logs"
        return getText(url, pat)
    }

    private fun getJson(url: String, pat: String): JsonElement {
        return executeRequest(
            Request.Builder()
                .url(url)
                .get()
                .withGitHubHeaders(pat)
                .build()
        )
    }

    private fun postJson(url: String, pat: String, body: JsonObject): JsonObject {
        return executeRequest(
            Request.Builder()
                .url(url)
                .post(gson.toJson(body).toRequestBody(jsonMediaType))
                .withGitHubHeaders(pat)
                .build()
        ).asJsonObject
    }

    private fun putJson(url: String, pat: String, body: JsonObject): JsonObject {
        return executeRequest(
            Request.Builder()
                .url(url)
                .put(gson.toJson(body).toRequestBody(jsonMediaType))
                .withGitHubHeaders(pat)
                .build()
        ).asJsonObject
    }

    private fun patchJson(url: String, pat: String, body: JsonObject): JsonObject {
        return executeRequest(
            Request.Builder()
                .url(url)
                .patch(gson.toJson(body).toRequestBody(jsonMediaType))
                .withGitHubHeaders(pat)
                .build()
        ).asJsonObject
    }

    private fun executeRequest(request: Request): JsonElement {
        return runBlockingIo {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = parseGitHubError(raw).ifBlank { "HTTP ${response.code}" }
                    error(message)
                }
                if (raw.isBlank()) JsonObject() else gson.fromJson(raw, JsonElement::class.java)
            }
        }
    }

    private fun getText(url: String, pat: String): String {
        return runBlockingIo {
            val request = Request.Builder().url(url).get().withGitHubHeaders(pat).build()
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val message = parseGitHubError(raw).ifBlank { "HTTP ${response.code}" }
                    error(message)
                }
                raw
            }
        }
    }

    private fun headRequest(url: String, pat: String): Pair<Int, String> {
        return runBlockingIo {
            val request = Request.Builder().url(url).get().withGitHubHeaders(pat).build()
            client.newCall(request).execute().use { response ->
                val finalUrl = response.request.url.toString()
                response.code to finalUrl
            }
        }
    }

    private fun parseRunReport(logText: String): ZiCodeReport {
        if (logText.isBlank()) {
            return ZiCodeReport(
                status = "error",
                summary = "日志为空，无法生成摘要",
                failingStep = null,
                errorSummary = "No logs available",
                fileHints = emptyList(),
                nextReads = emptyList(),
                artifacts = emptyList(),
                pagesUrl = null,
                deploymentStatus = null
            )
        }

        val failureLine =
            logText
                .lineSequence()
                .firstOrNull { line ->
                    val lower = line.lowercase()
                    lower.contains("error") || lower.contains("exception") || lower.contains("failed")
                }
                ?.trim()

        val fileHints =
            Regex("([A-Za-z0-9_./-]+\\.[A-Za-z0-9]+:\\d+)")
                .findAll(logText)
                .map { it.value.trim() }
                .distinct()
                .take(12)
                .toList()

        return ZiCodeReport(
            status = "failure",
            summary = failureLine ?: "构建失败，请查看完整日志",
            failingStep = null,
            errorSummary = failureLine ?: "Unknown error",
            fileHints = fileHints,
            nextReads = fileHints.take(5),
            artifacts = emptyList(),
            pagesUrl = null,
            deploymentStatus = null
        )
    }

    private fun parseGitHubError(raw: String): String {
        if (raw.isBlank()) return ""
        return runCatching {
            val obj = gson.fromJson(raw, JsonObject::class.java)
            val message = obj.get("message")?.asString?.trim().orEmpty()
            val doc = obj.get("documentation_url")?.asString?.trim().orEmpty()
            if (message.isBlank()) "" else if (doc.isBlank()) message else "$message ($doc)"
        }.getOrDefault("")
    }

    private fun Request.Builder.withGitHubHeaders(pat: String): Request.Builder {
        return this
            .addHeader("Authorization", "Bearer ${pat.trim()}")
            .addHeader("Accept", "application/vnd.github+json")
            .addHeader("X-GitHub-Api-Version", "2022-11-28")
            .addHeader("User-Agent", "ZionChat-ZiCode")
    }

    private fun <T> runBlockingIo(block: suspend () -> T): T {
        return kotlinx.coroutines.runBlocking { withContext(Dispatchers.IO) { block() } }
    }

    private fun parseUnifiedDiffFiles(patchText: String): List<UnifiedDiffFile> {
        val lines = patchText.replace("\r\n", "\n").split("\n")
        val files = mutableListOf<UnifiedDiffFile>()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            if (!line.startsWith("diff --git ")) {
                index++
                continue
            }
            val parts = line.removePrefix("diff --git ").split(" ")
            val oldPath = parts.getOrNull(0)?.removePrefix("a/").orEmpty()
            val newPath = parts.getOrNull(1)?.removePrefix("b/").orEmpty()
            index++
            var op = "update"
            val hunkLines = mutableListOf<String>()
            while (index < lines.size && !lines[index].startsWith("diff --git ")) {
                val inner = lines[index]
                if (inner.startsWith("new file mode") || inner.startsWith("--- /dev/null")) op = "add"
                if (inner.startsWith("deleted file mode") || inner.startsWith("+++ /dev/null")) op = "delete"
                if (inner.startsWith("@@")) {
                    while (index < lines.size && !lines[index].startsWith("diff --git ")) {
                        hunkLines.add(lines[index])
                        index++
                    }
                    break
                }
                index++
            }
            val path = if (op == "add") newPath else oldPath.ifBlank { newPath }
            files.add(UnifiedDiffFile(path = path, operation = op, hunks = hunkLines))
        }
        return files.filter { it.path.isNotBlank() }
    }

    private fun applyUnifiedDiffToText(original: String, hunkLines: List<String>): String {
        if (hunkLines.isEmpty()) return original
        val sourceLines = if (original.isEmpty()) emptyList() else original.split("\n")
        val output = mutableListOf<String>()
        var sourceIndex = 0
        var i = 0

        while (i < hunkLines.size) {
            val header = hunkLines[i]
            if (!header.startsWith("@@")) {
                i++
                continue
            }
            val match = Regex("@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@").find(header)
                ?: error("无效 hunk 头: $header")
            val oldStart = match.groupValues[1].toInt().coerceAtLeast(1)
            val oldStartIndex = oldStart - 1
            while (sourceIndex < oldStartIndex && sourceIndex < sourceLines.size) {
                output.add(sourceLines[sourceIndex])
                sourceIndex++
            }

            i++
            while (i < hunkLines.size && !hunkLines[i].startsWith("@@")) {
                val opLine = hunkLines[i]
                if (opLine.isEmpty()) {
                    i++
                    continue
                }
                val op = opLine.first()
                val text = opLine.drop(1)
                when (op) {
                    ' ' -> {
                        val current = sourceLines.getOrNull(sourceIndex)
                        if (current != text) error("补丁上下文不匹配: $text")
                        output.add(current)
                        sourceIndex++
                    }
                    '-' -> {
                        val current = sourceLines.getOrNull(sourceIndex)
                        if (current != text) error("补丁删除行不匹配: $text")
                        sourceIndex++
                    }
                    '+' -> output.add(text)
                    else -> {}
                }
                i++
            }
        }

        while (sourceIndex < sourceLines.size) {
            output.add(sourceLines[sourceIndex])
            sourceIndex++
        }
        return output.joinToString("\n")
    }

    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }

    private fun encodePath(path: String): String {
        val cleaned = path.trim().removePrefix("/")
        if (cleaned.isBlank()) return ""
        return cleaned.split("/").joinToString("/") { segment -> urlEncode(segment) }
    }

    private fun JsonObject.stringOrDefault(key: String, defaultValue: String): String {
        val value = this.get(key)?.asString?.trim().orEmpty()
        return value.ifBlank { defaultValue }
    }

    private fun JsonObject.requireString(key: String): String {
        return this.stringOrDefault(key, "").ifBlank { error("参数 $key 不能为空") }
    }

    private fun JsonObject.intOrDefault(key: String, defaultValue: Int): Int {
        return this.get(key)?.asInt ?: defaultValue
    }

    private fun JsonObject.boolOrDefault(key: String, defaultValue: Boolean): Boolean {
        return this.get(key)?.asBoolean ?: defaultValue
    }

    private fun JsonObject.requireInt(key: String): Int {
        return this.get(key)?.asInt ?: error("参数 $key 不能为空")
    }

    private fun JsonObject.requireLong(key: String): Long {
        return this.get(key)?.asLong ?: error("参数 $key 不能为空")
    }

    private fun JsonObject.toAnyMap(): Map<String, Any> {
        val destination = linkedMapOf<String, Any>()
        entrySet().forEach { (rawKey, rawValue) ->
            val key = rawKey.trim()
            if (key.isBlank()) return@forEach
            destination[key] = rawValue.toAnyValue()
        }
        return destination
    }

    private fun JsonElement.toAnyValue(): Any {
        return when {
            isJsonNull -> ""
            isJsonPrimitive -> {
                val primitive = asJsonPrimitive
                when {
                    primitive.isBoolean -> primitive.asBoolean
                    primitive.isNumber -> {
                        val raw = primitive.asString
                        if (raw.contains('.') || raw.contains('e', ignoreCase = true)) primitive.asDouble else primitive.asLong
                    }
                    else -> primitive.asString
                }
            }
            isJsonArray -> asJsonArray.map { it.toAnyValue() }
            isJsonObject -> asJsonObject.toAnyMap()
            else -> toString()
        }
    }

}

data class RepoFileContent(
    val path: String,
    val sha: String,
    val size: Int,
    val content: String
)

data class RepoFileMeta(
    val path: String,
    val sha: String,
    val size: Int,
    val type: String
)

data class UnifiedDiffFile(
    val path: String,
    val operation: String,
    val hunks: List<String>
)
