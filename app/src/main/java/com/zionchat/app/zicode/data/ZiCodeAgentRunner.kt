package com.zionchat.app.zicode.data

import com.zionchat.app.data.AppRepository
import com.zionchat.app.data.ChatApiClient
import com.zionchat.app.data.Message
import com.zionchat.app.data.ModelConfig
import com.zionchat.app.data.ProviderConfig
import com.zionchat.app.data.extractRemoteModelId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class ZiCodeAgentRunner(
    private val repository: ZiCodeRepository,
    private val gitHubService: ZiCodeGitHubService,
    private val appRepository: AppRepository,
    private val chatApiClient: ChatApiClient
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()

    fun enqueue(
        sessionId: String,
        repoOwner: String,
        repoName: String,
        turnId: String,
        prompt: String
    ) {
        activeJobs[turnId]?.cancel()
        activeJobs[turnId] =
            scope.launch {
                runTurn(
                    sessionId = sessionId,
                    repoOwner = repoOwner,
                    repoName = repoName,
                    turnId = turnId,
                    prompt = prompt
                )
            }
    }

    private suspend fun runTurn(
        sessionId: String,
        repoOwner: String,
        repoName: String,
        turnId: String,
        prompt: String
    ) {
        val normalizedPrompt = prompt.trim()
        val tools = mutableListOf<ZiCodeToolCallState>()

        suspend fun sync(status: ZiCodeRunStatus = ZiCodeRunStatus.RUNNING) {
            updateTurn(sessionId, turnId, tools.toList(), status)
        }

        suspend fun addTool(label: String, toolName: String, group: String, summary: String, inputSummary: String = ""): Int {
            tools += ZiCodeToolCallState(label = label, toolName = toolName, group = group, status = ZiCodeToolStatus.RUNNING, summary = summary, inputSummary = inputSummary)
            sync()
            return tools.lastIndex
        }

        suspend fun finishTool(index: Int, status: ZiCodeToolStatus, summary: String, detail: String = "", result: String = "") {
            tools[index] =
                tools[index].copy(
                    status = status,
                    summary = summary,
                    detailLog = detail.trim(),
                    resultSummary = result.trim(),
                    finishedAt = System.currentTimeMillis()
                )
            sync()
        }

        try {
            val goalIndex = addTool("理解目标", "agent.goal_analyze", "Agent", "正在拆解任务。", normalizedPrompt)
            delay(120)
            finishTool(goalIndex, ZiCodeToolStatus.SUCCESS, "任务已拆解，开始接入 GitHub 与模型。", "任务：$normalizedPrompt\n仓库：$repoOwner/$repoName", "进入执行阶段")

            val authIndex = addTool("检查 GitHub 连接", "github.auth", "GitHub", "正在校验 GitHub Token。")
            val token = repository.settingsFlow.first().githubToken.trim()
            if (token.isBlank()) {
                finishTool(authIndex, ZiCodeToolStatus.FAILED, "缺少 GitHub Token。", "请先在 ZiCode 设置页配置 GitHub Token。", "GitHub 未连接")
                failTurn(sessionId, turnId, tools, "ZiCode 还没有拿到 GitHub Token。先去设置页完成连接，然后我就能继续执行仓库任务。")
                return
            }
            val viewerResult = gitHubService.fetchViewer(token)
            val viewer = viewerResult.getOrNull()
            if (viewer == null) {
                finishTool(authIndex, ZiCodeToolStatus.FAILED, "GitHub Token 校验失败。", viewerResult.exceptionOrNull()?.message.orEmpty(), "认证失败")
                failTurn(sessionId, turnId, tools, "我没能通过当前 GitHub Token 完成认证。请检查 Token 是否有效，并确认它有访问仓库的权限。")
                return
            }
            repository.updateViewer(viewer)
            finishTool(authIndex, ZiCodeToolStatus.SUCCESS, "GitHub 连接可用。", "账号：${viewer.displayName ?: viewer.login}\n登录名：@${viewer.login}", "GitHub 已接通")

            val modelIndex = addTool("检查 Agent 模型", "agent.model_resolve", "Agent", "正在读取 ZiCode 默认模型。")
            val resolvedModelResult = resolveZiCodeModel()
            val resolvedModel = resolvedModelResult.getOrNull()
            if (resolvedModel == null) {
                finishTool(modelIndex, ZiCodeToolStatus.FAILED, "ZiCode 默认模型不可用。", resolvedModelResult.exceptionOrNull()?.message.orEmpty(), "模型未就绪")
                failTurn(sessionId, turnId, tools, "ZiCode 默认模型还没有配置完成。请先去“设置 -> 默认模型”里设置 ZiCode Agent 模型，然后再继续执行。")
                return
            }
            finishTool(modelIndex, ZiCodeToolStatus.SUCCESS, "ZiCode 默认模型已锁定。", "模型：${resolvedModel.model.displayName}\nProvider：${resolvedModel.provider.name}\n远端模型：${resolvedModel.remoteModelId}", "推理链路已接通")

            val repoIndex = addTool("读取仓库信息", "github.repo_get", "GitHub", "正在拉取仓库元数据。")
            val repoResult = gitHubService.fetchRepo(token, repoOwner, repoName)
            val repo = repoResult.getOrNull()
            if (repo == null) {
                finishTool(repoIndex, ZiCodeToolStatus.FAILED, "仓库读取失败。", repoResult.exceptionOrNull()?.message.orEmpty(), "仓库不可访问")
                failTurn(sessionId, turnId, tools, "我没有拿到 `$repoOwner/$repoName` 的仓库信息。请确认 Token 对这个仓库有访问权限。")
                return
            }
            finishTool(repoIndex, ZiCodeToolStatus.SUCCESS, "仓库信息已加载。", "仓库：${repo.fullName}\n默认分支：${repo.defaultBranch}\n可见性：${if (repo.privateRepo) "Private" else "Public"}\n最近更新：${formatTime(repo.updatedAt)}", "仓库上下文已就绪")

            val rootIndex = addTool("扫描根目录", "github.contents_list", "Contents", "正在读取仓库顶层目录。")
            val rootEntries = gitHubService.listDirectory(token, repoOwner, repoName, "").getOrNull().orEmpty()
            finishTool(
                rootIndex,
                if (rootEntries.isNotEmpty()) ZiCodeToolStatus.SUCCESS else ZiCodeToolStatus.FAILED,
                if (rootEntries.isNotEmpty()) "已读取 ${rootEntries.size} 个顶层条目。" else "顶层目录暂不可用。",
                if (rootEntries.isNotEmpty()) rootEntries.take(14).joinToString("\n") { "- ${it.path} [${it.type}]" } else "当前没有拿到根目录条目。",
                if (rootEntries.isNotEmpty()) "目录结构已同步" else "目录缺失"
            )

            val branchIndex = addTool("同步分支", "github.branch_list", "Branches", "正在拉取分支列表。")
            val branches = gitHubService.listBranches(token, repoOwner, repoName).getOrNull().orEmpty()
            finishTool(
                branchIndex,
                if (branches.isNotEmpty()) ZiCodeToolStatus.SUCCESS else ZiCodeToolStatus.FAILED,
                if (branches.isNotEmpty()) "已获取 ${branches.size} 个分支。" else "分支列表暂不可用。",
                if (branches.isNotEmpty()) branches.take(12).joinToString("\n") { "- ${it.name}${if (it.protectedBranch) " · protected" else ""}" } else "当前没有拿到分支列表。",
                if (branches.isNotEmpty()) "分支信息已同步" else "分支缺失"
            )

            val commitIndex = addTool("读取提交历史", "github.commit_list", "Commits", "正在拉取最近提交。")
            val commits = gitHubService.listCommits(token, repoOwner, repoName, repo.defaultBranch, 8).getOrNull().orEmpty()
            finishTool(
                commitIndex,
                if (commits.isNotEmpty()) ZiCodeToolStatus.SUCCESS else ZiCodeToolStatus.FAILED,
                if (commits.isNotEmpty()) "已获取 ${commits.size} 条最近提交。" else "最近提交暂不可用。",
                if (commits.isNotEmpty()) commits.joinToString("\n") { "- ${it.sha.take(7)} · ${it.message}" } else "当前没有拿到提交历史。",
                if (commits.isNotEmpty()) "提交历史已同步" else "提交数据缺失"
            )

            finishRunWithExtendedContext(
                sessionId = sessionId,
                turnId = turnId,
                tools = tools,
                token = token,
                repoOwner = repoOwner,
                repoName = repoName,
                prompt = normalizedPrompt,
                repo = repo,
                rootEntries = rootEntries,
                branches = branches,
                commits = commits,
                resolvedModel = resolvedModel
            )
        } catch (throwable: Throwable) {
            failTurn(sessionId, turnId, tools, throwable.message?.trim().orEmpty().ifBlank { "ZiCode 这次执行被中断了，请稍后再试一次。" })
        } finally {
            activeJobs.remove(turnId)
        }
    }

    private suspend fun resolveZiCodeModel(): Result<ResolvedZiCodeModel> {
        return runCatching {
            val selectedId =
                appRepository.defaultZiCodeModelIdFlow.first()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("还没有设置 ZiCode 默认模型。")
            val model =
                appRepository.modelsFlow.first().firstOrNull { candidate ->
                    candidate.enabled && (candidate.id == selectedId || extractRemoteModelId(candidate.id) == selectedId)
                } ?: throw IllegalStateException("ZiCode 默认模型不存在，或已被禁用。")
            val providerId =
                model.providerId?.trim()?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("ZiCode 默认模型没有关联可用的 Provider。")
            val provider =
                appRepository.providersFlow.first().firstOrNull { it.id == providerId }
                    ?: throw IllegalStateException("ZiCode 默认模型对应的 Provider 不存在。")
            ResolvedZiCodeModel(
                provider = provider,
                model = model,
                remoteModelId = extractRemoteModelId(model.id).ifBlank { model.id },
                enableThinking = appRepository.chatThinkingEnabledFlow.first()
            )
        }
    }

    private suspend fun updateTurn(
        sessionId: String,
        turnId: String,
        tools: List<ZiCodeToolCallState>,
        status: ZiCodeRunStatus
    ) {
        repository.updateTurn(sessionId, turnId) { turn ->
            turn.copy(status = status, toolCalls = tools)
        }
    }

    private suspend fun failTurn(
        sessionId: String,
        turnId: String,
        tools: List<ZiCodeToolCallState>,
        message: String
    ) {
        repository.updateTurn(sessionId, turnId) { turn ->
            turn.copy(
                response = message,
                status = ZiCodeRunStatus.FAILED,
                toolCalls = tools,
                failureMessage = message
            )
        }
    }

    private suspend fun finishRunWithExtendedContext(
        sessionId: String,
        turnId: String,
        tools: MutableList<ZiCodeToolCallState>,
        token: String,
        repoOwner: String,
        repoName: String,
        prompt: String,
        repo: ZiCodeRemoteRepo,
        rootEntries: List<ZiCodeRepoNode>,
        branches: List<ZiCodeGitHubBranch>,
        commits: List<ZiCodeGitHubCommit>,
        resolvedModel: ResolvedZiCodeModel
    ) {
        suspend fun sync() = updateTurn(sessionId, turnId, tools.toList(), ZiCodeRunStatus.RUNNING)

        suspend fun addTool(label: String, toolName: String, group: String, summary: String, inputSummary: String = ""): Int {
            tools += ZiCodeToolCallState(label = label, toolName = toolName, group = group, status = ZiCodeToolStatus.RUNNING, summary = summary, inputSummary = inputSummary)
            sync()
            return tools.lastIndex
        }

        suspend fun finishTool(index: Int, status: ZiCodeToolStatus, summary: String, detail: String = "", result: String = "") {
            tools[index] =
                tools[index].copy(
                    status = status,
                    summary = summary,
                    detailLog = detail.trim(),
                    resultSummary = result.trim(),
                    finishedAt = System.currentTimeMillis()
                )
            sync()
        }

        val readmeNode = rootEntries.firstOrNull { it.type == "file" && it.name.startsWith("README", ignoreCase = true) }
        val readmeIndex = addTool("读取 README", "github.file_read", "Contents", "正在提取项目说明。", readmeNode?.path.orEmpty().ifBlank { "README*" })
        val readmeResult = readmeNode?.let { gitHubService.readFile(token, repoOwner, repoName, it.path) }
        val readmePreview = readmeResult?.getOrNull()
        finishTool(readmeIndex, if (readmePreview != null) ZiCodeToolStatus.SUCCESS else ZiCodeToolStatus.FAILED, if (readmePreview != null) "README 已读取。" else "README 暂不可用。", readmePreview?.content?.take(680) ?: readmeResult?.exceptionOrNull()?.message.orEmpty(), if (readmePreview != null) "项目说明已加入上下文" else "无 README")

        val workflowIndex = addTool("同步工作流", "github.workflow_list", "Actions", "正在读取 GitHub Actions 工作流。")
        val workflows = gitHubService.listWorkflows(token, repoOwner, repoName).getOrNull().orEmpty()
        finishTool(workflowIndex, if (workflows.isNotEmpty()) ZiCodeToolStatus.SUCCESS else ZiCodeToolStatus.FAILED, if (workflows.isNotEmpty()) "检测到 ${workflows.size} 个工作流。" else "没有拿到工作流列表。", if (workflows.isNotEmpty()) workflows.joinToString("\n") { "- ${it.name} · ${it.path}" } else "仓库里没有检测到可见的 Actions 工作流。", if (workflows.isNotEmpty()) "工作流上下文已同步" else "无可见工作流")

        var createdBranch: ZiCodeGitHubBranch? = null
        extractRequestedBranchName(prompt)?.let { branchName ->
            val sourceSha = branches.firstOrNull { it.name.equals(repo.defaultBranch, true) }?.sha ?: branches.firstOrNull()?.sha
            if (!sourceSha.isNullOrBlank() && wantsCreateBranch(prompt)) {
                val index = addTool("创建分支", "github.branch_create", "Branches", "正在创建新分支。", branchName)
                val result = gitHubService.createBranch(token, repoOwner, repoName, branchName, sourceSha)
                createdBranch = result.getOrNull()
                finishTool(index, if (createdBranch != null) ZiCodeToolStatus.SUCCESS else ZiCodeToolStatus.FAILED, if (createdBranch != null) "分支 `$branchName` 已创建。" else "分支创建失败。", result.exceptionOrNull()?.message.orEmpty(), createdBranch?.sha ?: "创建失败")
            }
        }

        var dispatchedWorkflowName: String? = null
        if (wantsWorkflowDispatch(prompt) && workflows.isNotEmpty()) {
            resolveWorkflowDispatchTarget(prompt, workflows)?.let { workflow ->
                val index = addTool("触发工作流", "github.workflow_dispatch", "Actions", "正在触发工作流执行。", workflow.name)
                val result = gitHubService.dispatchWorkflow(token, repoOwner, repoName, workflow.id.toString(), createdBranch?.name ?: repo.defaultBranch)
                if (result.isSuccess) {
                    dispatchedWorkflowName = workflow.name
                    delay(1200)
                }
                finishTool(index, if (result.isSuccess) ZiCodeToolStatus.SUCCESS else ZiCodeToolStatus.FAILED, if (result.isSuccess) "工作流 `${workflow.name}` 已触发。" else "工作流触发失败。", result.exceptionOrNull()?.message.orEmpty(), if (result.isSuccess) createdBranch?.name ?: repo.defaultBranch else "触发失败")
            }
        }

        val runIndex = addTool("读取运行记录", "github.workflow_runs", "Actions", "正在同步工作流运行状态。")
        val workflowRuns = gitHubService.listWorkflowRuns(token, repoOwner, repoName).getOrNull().orEmpty()
        finishTool(runIndex, if (workflowRuns.isNotEmpty()) ZiCodeToolStatus.SUCCESS else ZiCodeToolStatus.FAILED, if (workflowRuns.isNotEmpty()) "已获取 ${workflowRuns.size} 条运行记录。" else "当前没有工作流运行记录。", if (workflowRuns.isNotEmpty()) workflowRuns.take(8).joinToString("\n") { "- ${it.name} · ${it.status}${it.conclusion?.let { c -> " · $c" } ?: ""}" } else "还没有可见的 workflow run。", if (workflowRuns.isNotEmpty()) "Actions 运行状态已同步" else "无运行记录")

        val traceIndex = addTool("展开运行日志", "github.workflow_trace", "Actions", "正在读取最新运行作业详情。", workflowRuns.firstOrNull()?.id?.toString().orEmpty())
        val workflowTrace = workflowRuns.firstOrNull()?.let { gitHubService.readWorkflowRunTrace(token, repoOwner, repoName, it.id).getOrNull().orEmpty() }.orEmpty()
        finishTool(traceIndex, if (workflowTrace.isNotBlank()) ZiCodeToolStatus.SUCCESS else ZiCodeToolStatus.FAILED, if (workflowTrace.isNotBlank()) "运行详情已展开。" else "运行详情暂不可用。", workflowTrace.take(900).ifBlank { "没有拿到作业步骤。" }, if (workflowTrace.isNotBlank()) "运行日志已加入上下文" else "日志缺失")

        val releaseIndex = addTool("同步发布", "github.release_list", "Release", "正在读取 Release 列表。")
        val releases = gitHubService.listReleases(token, repoOwner, repoName).getOrNull().orEmpty()
        finishTool(releaseIndex, if (releases.isNotEmpty()) ZiCodeToolStatus.SUCCESS else ZiCodeToolStatus.FAILED, if (releases.isNotEmpty()) "检测到 ${releases.size} 个 Release。" else "当前没有 Release 数据。", if (releases.isNotEmpty()) releases.take(6).joinToString("\n") { "- ${it.tagName}${it.name.takeIf { name -> name.isNotBlank() }?.let { name -> " · $name" } ?: ""}" } else "仓库里还没有可见 Release。", if (releases.isNotEmpty()) "发布历史已同步" else "无发布数据")

        val pagesIndex = addTool("检查 Pages", "github.pages_get", "Pages", "正在检查 GitHub Pages 状态。")
        val pagesInfo = gitHubService.fetchPagesInfo(token, repoOwner, repoName).getOrNull()
        val pagesDetail =
            when {
                pagesInfo == null -> "没有拿到 Pages 信息。"
                pagesInfo.status == "not_configured" -> "当前仓库没有启用 GitHub Pages。"
                else -> buildString {
                    appendLine("状态：${pagesInfo.status}")
                    pagesInfo.htmlUrl?.let { appendLine("地址：$it") }
                    pagesInfo.sourceBranch?.let { appendLine("来源分支：$it") }
                    pagesInfo.sourcePath?.let { append("来源路径：$it") }
                }
            }
        finishTool(pagesIndex, if (pagesInfo != null && pagesInfo.status != "not_configured") ZiCodeToolStatus.SUCCESS else ZiCodeToolStatus.FAILED, if (pagesInfo == null) "GitHub Pages 状态不可用。" else if (pagesInfo.status == "not_configured") "GitHub Pages 尚未配置。" else "GitHub Pages 状态已同步。", pagesDetail, if (pagesInfo == null) "Pages 状态缺失" else if (pagesInfo.status == "not_configured") "Pages 未启用" else "Pages 已接入上下文")

        val summaryIndex = addTool("生成 Agent 结论", "agent.summary_build", "Agent", "正在使用 ZiCode 默认模型整理结论。")
        val fallback = buildFallbackSummary(repo, prompt, rootEntries, branches, commits, workflows, workflowRuns, workflowTrace, releases, pagesInfo, readmePreview, createdBranch, dispatchedWorkflowName)
        val modelResult =
            chatApiClient.chatCompletions(
                provider = resolvedModel.provider,
                modelId = resolvedModel.remoteModelId,
                messages = listOf(
                    Message(role = "system", content = "You are ZiCode, a GitHub execution agent. Respond in the user's language. Be concise, concrete, and never invent repository facts."),
                    Message(role = "user", content = buildModelContext(repo, prompt, rootEntries, branches, commits, workflows, workflowRuns, workflowTrace, releases, pagesInfo, readmePreview, createdBranch, dispatchedWorkflowName))
                ),
                extraHeaders = resolvedModel.model.headers,
                reasoningEffort = resolvedModel.model.reasoningEffort,
                enableThinking = resolvedModel.enableThinking,
                maxTokens = 1400
            )
        val response = modelResult.getOrNull()?.trim().takeIf { !it.isNullOrBlank() } ?: fallback
        finishTool(summaryIndex, if (modelResult.isSuccess) ZiCodeToolStatus.SUCCESS else ZiCodeToolStatus.FAILED, if (modelResult.isSuccess) "ZiCode 结果已整理完成。" else "模型总结失败，已退回本地总结。", response.take(900), if (modelResult.isSuccess) "默认模型已返回结论" else "已启用本地回退总结")

        repository.updateTurn(sessionId, turnId) { turn ->
            turn.copy(
                response = response,
                status = ZiCodeRunStatus.SUCCESS,
                toolCalls = tools,
                resultLink = workflowRuns.firstOrNull()?.htmlUrl?.takeIf { it.isNotBlank() } ?: pagesInfo?.htmlUrl?.takeIf { it.isNotBlank() } ?: repo.homepageUrl?.takeIf { it.isNotBlank() } ?: repo.htmlUrl,
                resultLabel = when {
                    workflowRuns.firstOrNull()?.htmlUrl?.isNotBlank() == true -> "Open workflow run"
                    pagesInfo?.htmlUrl?.isNotBlank() == true -> "Open GitHub Pages"
                    !repo.homepageUrl.isNullOrBlank() -> "Open project homepage"
                    else -> "Open GitHub repo"
                },
                failureMessage = null
            )
        }
    }

    private fun wantsCreateBranch(prompt: String): Boolean {
        return prompt.contains("create branch", ignoreCase = true) ||
            prompt.contains("new branch", ignoreCase = true) ||
            prompt.contains("创建分支") ||
            prompt.contains("新建分支")
    }

    private fun wantsWorkflowDispatch(prompt: String): Boolean {
        return prompt.contains("run workflow", ignoreCase = true) ||
            prompt.contains("trigger workflow", ignoreCase = true) ||
            prompt.contains("dispatch workflow", ignoreCase = true) ||
            prompt.contains("触发工作流") ||
            prompt.contains("运行工作流")
    }

    private fun extractRequestedBranchName(prompt: String): String? {
        val patterns =
            listOf(
                Regex("""(?i)(?:create|new)\s+branch\s+([A-Za-z0-9._/\-]+)"""),
                Regex("""(?:创建|新建)分支[:：\s]+([A-Za-z0-9._/\-]+)""")
            )
        return patterns.asSequence()
            .mapNotNull { it.find(prompt)?.groupValues?.getOrNull(1) }
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
    }

    private fun resolveWorkflowDispatchTarget(
        prompt: String,
        workflows: List<ZiCodeGitHubWorkflow>
    ): ZiCodeGitHubWorkflow? {
        val lowerPrompt = prompt.lowercase()
        return workflows.firstOrNull { workflow ->
            workflow.name.lowercase() in lowerPrompt || workflow.path.lowercase() in lowerPrompt
        } ?: workflows.firstOrNull()
    }

    private fun buildModelContext(
        repo: ZiCodeRemoteRepo,
        prompt: String,
        rootEntries: List<ZiCodeRepoNode>,
        branches: List<ZiCodeGitHubBranch>,
        commits: List<ZiCodeGitHubCommit>,
        workflows: List<ZiCodeGitHubWorkflow>,
        workflowRuns: List<ZiCodeGitHubWorkflowRun>,
        workflowTrace: String,
        releases: List<ZiCodeGitHubRelease>,
        pagesInfo: ZiCodeGitHubPagesInfo?,
        readmePreview: ZiCodeFilePreview?,
        createdBranch: ZiCodeGitHubBranch?,
        dispatchedWorkflowName: String?
    ): String {
        return buildString {
            appendLine("User goal:")
            appendLine(prompt)
            appendLine()
            appendLine("Repository: ${repo.fullName}")
            appendLine("Visibility: ${if (repo.privateRepo) "private" else "public"}")
            appendLine("Default branch: ${repo.defaultBranch}")
            appendLine("Updated at: ${formatTime(repo.updatedAt)}")
            appendLine()
            appendLine("Root tree:")
            if (rootEntries.isEmpty()) appendLine("- No entries.") else rootEntries.take(18).forEach { appendLine("- ${it.path} [${it.type}]") }
            appendLine()
            appendLine("Branches:")
            if (branches.isEmpty()) appendLine("- No branch data.") else branches.take(12).forEach { appendLine("- ${it.name}${if (it.protectedBranch) " (protected)" else ""}") }
            createdBranch?.let { appendLine("- Created branch: ${it.name}") }
            appendLine()
            appendLine("Recent commits:")
            if (commits.isEmpty()) appendLine("- No commits.") else commits.take(8).forEach { appendLine("- ${it.sha.take(7)} · ${it.message}") }
            appendLine()
            appendLine("Workflows:")
            if (workflows.isEmpty()) appendLine("- No visible workflows.") else workflows.forEach { appendLine("- ${it.name} · ${it.path}") }
            dispatchedWorkflowName?.let { appendLine("- Dispatched in this run: $it") }
            appendLine()
            appendLine("Workflow runs:")
            if (workflowRuns.isEmpty()) appendLine("- No runs.") else workflowRuns.take(8).forEach { appendLine("- ${it.name} · ${it.status}${it.conclusion?.let { c -> " · $c" } ?: ""}") }
            if (workflowTrace.isNotBlank()) {
                appendLine()
                appendLine("Latest workflow trace:")
                appendLine(workflowTrace.take(1600))
            }
            appendLine()
            appendLine("Releases:")
            if (releases.isEmpty()) appendLine("- No releases.") else releases.take(6).forEach { appendLine("- ${it.tagName}${it.name.takeIf { name -> name.isNotBlank() }?.let { name -> " · $name" } ?: ""}") }
            appendLine()
            appendLine("Pages:")
            if (pagesInfo == null) appendLine("- No pages data.") else {
                appendLine("- Status: ${pagesInfo.status}")
                pagesInfo.htmlUrl?.let { appendLine("- URL: $it") }
                pagesInfo.sourceBranch?.let { appendLine("- Source branch: $it") }
                pagesInfo.sourcePath?.let { appendLine("- Source path: $it") }
            }
            readmePreview?.content?.takeIf { it.isNotBlank() }?.let {
                appendLine()
                appendLine("README excerpt:")
                appendLine(it.take(1800))
            }
            appendLine()
            appendLine("Respond with what ZiCode already executed, what the current GitHub state means, and the best next actions.")
        }
    }

    private fun buildFallbackSummary(
        repo: ZiCodeRemoteRepo,
        prompt: String,
        rootEntries: List<ZiCodeRepoNode>,
        branches: List<ZiCodeGitHubBranch>,
        commits: List<ZiCodeGitHubCommit>,
        workflows: List<ZiCodeGitHubWorkflow>,
        workflowRuns: List<ZiCodeGitHubWorkflowRun>,
        workflowTrace: String,
        releases: List<ZiCodeGitHubRelease>,
        pagesInfo: ZiCodeGitHubPagesInfo?,
        readmePreview: ZiCodeFilePreview?,
        createdBranch: ZiCodeGitHubBranch?,
        dispatchedWorkflowName: String?
    ): String {
        val folders = rootEntries.filter { it.type == "dir" }.take(6).joinToString("、") { it.name }
        val files = rootEntries.filter { it.type == "file" }.take(6).joinToString("、") { it.name }
        return buildString {
            appendLine("我已经围绕“$prompt”完成了对 `${repo.fullName}` 的 GitHub Agent 首轮执行。")
            appendLine()
            appendLine("这轮实际执行：")
            appendLine("- 校验了 GitHub Token 与 ZiCode 默认模型。")
            appendLine("- 读取了仓库、根目录、分支、提交、工作流、运行记录、Release 和 Pages 状态。")
            createdBranch?.let { appendLine("- 已创建分支：`${it.name}`。") }
            dispatchedWorkflowName?.let { appendLine("- 已触发工作流：`$it`。") }
            appendLine()
            appendLine("仓库现状：")
            appendLine("- 默认分支：`${repo.defaultBranch}`，最近更新于 ${formatTime(repo.updatedAt)}。")
            appendLine("- 顶层目录：${folders.ifBlank { "暂未识别到明显目录。" }}")
            appendLine("- 顶层文件：${files.ifBlank { "暂未识别到明显文件。" }}")
            if (branches.isNotEmpty()) appendLine("- 当前分支：${branches.take(6).joinToString("、") { it.name }}")
            if (commits.isNotEmpty()) appendLine("- 最近提交：${commits.take(3).joinToString("；") { "${it.sha.take(7)} ${it.message}" }}")
            if (workflows.isNotEmpty()) appendLine("- 工作流：${workflows.take(4).joinToString("、") { it.name }}")
            if (workflowRuns.isNotEmpty()) appendLine("- 最新运行：${workflowRuns.first().name} · ${workflowRuns.first().status}${workflowRuns.first().conclusion?.let { c -> " · $c" } ?: ""}")
            if (releases.isNotEmpty()) appendLine("- 最新发布：${releases.first().tagName}")
            pagesInfo?.let { appendLine("- Pages：${if (it.status == "not_configured") "未配置" else it.status}") }
            readmePreview?.content?.replace(Regex("\\s+"), " ")?.take(160)?.takeIf { it.isNotBlank() }?.let { appendLine("- README 摘要：$it") }
            if (workflowTrace.isNotBlank()) {
                appendLine()
                appendLine("运行细节：")
                appendLine(workflowTrace.take(420))
            }
            appendLine()
            appendLine("下一步建议：")
            appendLine("- 如果你要继续改代码，我下一轮可以直接锁定具体目录和文件。")
            appendLine("- 如果你要继续 CI / 发布，我会优先围绕工作流和 Release 入口继续执行。")
            appendLine("- 如果你要开始交付任务，我已经具备继续读取文件树、分支和运行记录的上下文。")
        }.trim()
    }

    private fun formatTime(timestamp: Long): String {
        if (timestamp <= 0L) return "未知时间"
        return formatter.format(timestamp)
    }

    private data class ResolvedZiCodeModel(
        val provider: ProviderConfig,
        val model: ModelConfig,
        val remoteModelId: String,
        val enableThinking: Boolean
    )

    companion object {
        private val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
    }
}
