package com.zionchat.app.zicode.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
        val useChinese = prefersChinese()
        fun t(zh: String, en: String): String = tr(useChinese, zh, en)
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
            val goalIndex = addTool(t("理解目标", "Analyze goal"), "agent.goal_analyze", "ZiCode", t("正在拆解任务。", "Breaking down the request."), normalizedPrompt)
            delay(120)
            finishTool(
                goalIndex,
                ZiCodeToolStatus.SUCCESS,
                t("任务已拆解，开始接入 GitHub 与模型。", "Goal parsed. Moving into GitHub and model execution."),
                "${t("任务", "Goal")}: $normalizedPrompt\n${t("仓库", "Repository")}: $repoOwner/$repoName",
                t("进入执行阶段", "Execution started")
            )

            val authIndex = addTool(t("检查 GitHub 连接", "Check GitHub access"), "github.auth", "GitHub", t("正在校验 GitHub Token。", "Validating the GitHub token."))
            val token = repository.settingsFlow.first().githubToken.trim()
            if (token.isBlank()) {
                finishTool(
                    authIndex,
                    ZiCodeToolStatus.FAILED,
                    t("缺少 GitHub Token。", "GitHub token is missing."),
                    t("请先在 ZiCode 设置页配置 GitHub Token。", "Configure a GitHub token in ZiCode Settings first."),
                    t("GitHub 未连接", "GitHub is not connected")
                )
                failTurn(sessionId, turnId, tools, t("ZiCode 还没有拿到 GitHub Token。先去设置页完成连接，然后我就能继续执行仓库任务。", "ZiCode cannot continue until a GitHub token is configured in Settings."))
                return
            }
            val viewerResult = gitHubService.fetchViewer(token)
            val viewer = viewerResult.getOrNull()
            if (viewer == null) {
                finishTool(
                    authIndex,
                    ZiCodeToolStatus.FAILED,
                    t("GitHub Token 校验失败。", "GitHub token validation failed."),
                    viewerResult.exceptionOrNull()?.message.orEmpty(),
                    t("认证失败", "Authentication failed")
                )
                failTurn(sessionId, turnId, tools, t("我没能通过当前 GitHub Token 完成认证。请检查 Token 是否有效，并确认它有访问仓库的权限。", "Authentication failed with the current GitHub token. Verify that the token is valid and has repository access."))
                return
            }
            repository.updateViewer(viewer)
            finishTool(
                authIndex,
                ZiCodeToolStatus.SUCCESS,
                t("GitHub 连接可用。", "GitHub access is ready."),
                "${t("账号", "Account")}: ${viewer.displayName ?: viewer.login}\n${t("登录名", "Login")}: @${viewer.login}",
                t("GitHub 已接通", "GitHub connected")
            )

            val modelIndex = addTool(t("检查 ZiCode 模型", "Resolve ZiCode model"), "agent.model_resolve", "ZiCode", t("正在读取 ZiCode 默认模型。", "Resolving the ZiCode default model."))
            val resolvedModelResult = resolveZiCodeModel(useChinese)
            val resolvedModel = resolvedModelResult.getOrNull()
            if (resolvedModel == null) {
                finishTool(
                    modelIndex,
                    ZiCodeToolStatus.FAILED,
                    t("ZiCode 默认模型不可用。", "The ZiCode default model is unavailable."),
                    resolvedModelResult.exceptionOrNull()?.message.orEmpty(),
                    t("模型未就绪", "Model is not ready")
                )
                failTurn(sessionId, turnId, tools, t("ZiCode 默认模型还没有配置完成。请先去“设置 -> 默认模型”里设置 ZiCode 模型，然后再继续执行。", "ZiCode needs its own default model before GitHub execution can run. Set it in Settings -> Default model first."))
                return
            }
            finishTool(
                modelIndex,
                ZiCodeToolStatus.SUCCESS,
                t("ZiCode 默认模型已锁定。", "The ZiCode default model is locked in."),
                "${t("模型", "Model")}: ${resolvedModel.model.displayName}\n${t("提供商", "Provider")}: ${resolvedModel.provider.name}\n${t("远端模型", "Remote model")}: ${resolvedModel.remoteModelId}",
                t("推理链路已接通", "Model pipeline connected")
            )

            val repoIndex = addTool(t("读取仓库信息", "Load repository"), "github.repo_get", "GitHub", t("正在拉取仓库元数据。", "Loading repository metadata."))
            val repoResult = gitHubService.fetchRepo(token, repoOwner, repoName)
            val repo = repoResult.getOrNull()
            if (repo == null) {
                finishTool(
                    repoIndex,
                    ZiCodeToolStatus.FAILED,
                    t("仓库读取失败。", "Repository read failed."),
                    repoResult.exceptionOrNull()?.message.orEmpty(),
                    t("仓库不可访问", "Repository is not accessible")
                )
                failTurn(sessionId, turnId, tools, t("我没有拿到 `$repoOwner/$repoName` 的仓库信息。请确认 Token 对这个仓库有访问权限。", "ZiCode could not read `$repoOwner/$repoName`. Confirm that the token can access this repository."))
                return
            }
            finishTool(
                repoIndex,
                ZiCodeToolStatus.SUCCESS,
                t("仓库信息已加载。", "Repository metadata loaded."),
                "${t("仓库", "Repository")}: ${repo.fullName}\n${t("默认分支", "Default branch")}: ${repo.defaultBranch}\n${t("可见性", "Visibility")}: ${if (repo.privateRepo) t("私有", "Private") else t("公开", "Public")}\n${t("最近更新", "Updated at")}: ${formatTime(repo.updatedAt, useChinese)}",
                t("仓库上下文已就绪", "Repository context is ready")
            )

            val rootIndex = addTool(t("扫描根目录", "Scan root tree"), "github.contents_list", "Contents", t("正在读取仓库顶层目录。", "Reading the repository root tree."))
            val rootEntries = gitHubService.listDirectory(token, repoOwner, repoName, "").getOrNull().orEmpty()
            finishTool(
                rootIndex,
                if (rootEntries.isNotEmpty()) ZiCodeToolStatus.SUCCESS else ZiCodeToolStatus.FAILED,
                if (rootEntries.isNotEmpty()) t("已读取 ${rootEntries.size} 个顶层条目。", "Loaded ${rootEntries.size} root entries.") else t("顶层目录暂不可用。", "The root tree is unavailable right now."),
                if (rootEntries.isNotEmpty()) rootEntries.take(14).joinToString("\n") { "- ${it.path} [${it.type}]" } else t("当前没有拿到根目录条目。", "No root entries were returned."),
                if (rootEntries.isNotEmpty()) t("目录结构已同步", "Tree structure synced") else t("目录缺失", "Tree data missing")
            )

            val branchIndex = addTool(t("同步分支", "Sync branches"), "github.branch_list", "Branches", t("正在拉取分支列表。", "Loading repository branches."))
            val branches = gitHubService.listBranches(token, repoOwner, repoName).getOrNull().orEmpty()
            finishTool(
                branchIndex,
                if (branches.isNotEmpty()) ZiCodeToolStatus.SUCCESS else ZiCodeToolStatus.FAILED,
                if (branches.isNotEmpty()) t("已获取 ${branches.size} 个分支。", "Loaded ${branches.size} branches.") else t("分支列表暂不可用。", "Branch data is unavailable right now."),
                if (branches.isNotEmpty()) branches.take(12).joinToString("\n") { "- ${it.name}${if (it.protectedBranch) " · ${t("保护", "protected")}" else ""}" } else t("当前没有拿到分支列表。", "No branch list was returned."),
                if (branches.isNotEmpty()) t("分支信息已同步", "Branch context synced") else t("分支缺失", "Branch data missing")
            )

            val commitIndex = addTool(t("读取提交历史", "Read commit history"), "github.commit_list", "Commits", t("正在拉取最近提交。", "Loading recent commits."))
            val commits = gitHubService.listCommits(token, repoOwner, repoName, repo.defaultBranch, 8).getOrNull().orEmpty()
            finishTool(
                commitIndex,
                if (commits.isNotEmpty()) ZiCodeToolStatus.SUCCESS else ZiCodeToolStatus.FAILED,
                if (commits.isNotEmpty()) t("已获取 ${commits.size} 条最近提交。", "Loaded ${commits.size} recent commits.") else t("最近提交暂不可用。", "Commit history is unavailable right now."),
                if (commits.isNotEmpty()) commits.joinToString("\n") { "- ${it.sha.take(7)} · ${it.message}" } else t("当前没有拿到提交历史。", "No commit history was returned."),
                if (commits.isNotEmpty()) t("提交历史已同步", "Commit history synced") else t("提交数据缺失", "Commit data missing")
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
                resolvedModel = resolvedModel,
                useChinese = useChinese
            )
        } catch (throwable: Throwable) {
            failTurn(sessionId, turnId, tools, throwable.message?.trim().orEmpty().ifBlank { t("ZiCode 这次执行被中断了，请稍后再试一次。", "ZiCode was interrupted this time. Try again in a moment.") })
        } finally {
            activeJobs.remove(turnId)
        }
    }

    private suspend fun resolveZiCodeModel(useChinese: Boolean): Result<ResolvedZiCodeModel> {
        return runCatching {
            val selectedId =
                appRepository.defaultZiCodeModelIdFlow.first()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException(tr(useChinese, "还没有设置 ZiCode 默认模型。", "ZiCode default model has not been configured yet."))
            val model =
                appRepository.modelsFlow.first().firstOrNull { candidate ->
                    candidate.enabled && (candidate.id == selectedId || extractRemoteModelId(candidate.id) == selectedId)
                } ?: throw IllegalStateException(tr(useChinese, "ZiCode 默认模型不存在，或已被禁用。", "The ZiCode default model was not found or is disabled."))
            val providerId =
                model.providerId?.trim()?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException(tr(useChinese, "ZiCode 默认模型没有关联可用的 Provider。", "The ZiCode default model does not have a valid provider."))
            val provider =
                appRepository.providersFlow.first().firstOrNull { it.id == providerId }
                    ?: throw IllegalStateException(tr(useChinese, "ZiCode 默认模型对应的 Provider 不存在。", "The provider for the ZiCode default model does not exist."))
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
        resolvedModel: ResolvedZiCodeModel,
        useChinese: Boolean
    ) {
        fun t(zh: String, en: String): String = tr(useChinese, zh, en)
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
        val readmeIndex = addTool(t("读取 README", "Read README"), "github.file_read", "Contents", t("正在提取项目说明。", "Extracting project guidance."), readmeNode?.path.orEmpty().ifBlank { "README*" })
        val readmeResult = readmeNode?.let { gitHubService.readFile(token, repoOwner, repoName, it.path) }
        val readmePreview = readmeResult?.getOrNull()
        finishTool(
            readmeIndex,
            if (readmePreview != null) ZiCodeToolStatus.SUCCESS else ZiCodeToolStatus.FAILED,
            if (readmePreview != null) t("README 已读取。", "README loaded.") else t("README 暂不可用。", "README is unavailable right now."),
            readmePreview?.content?.take(680) ?: readmeResult?.exceptionOrNull()?.message.orEmpty(),
            if (readmePreview != null) t("项目说明已加入上下文", "README added to context") else t("无 README", "No README")
        )

        val issuesIndex = addTool(t("同步 Issues", "Sync issues"), "github.issue_list", "Issues", t("正在读取仓库 Issues。", "Loading repository issues."))
        val issues = gitHubService.listIssues(token, repoOwner, repoName).getOrNull().orEmpty()
        finishTool(
            issuesIndex,
            if (issues.isNotEmpty()) ZiCodeToolStatus.SUCCESS else ZiCodeToolStatus.FAILED,
            if (issues.isNotEmpty()) t("已获取 ${issues.size} 条 Issue。", "Loaded ${issues.size} issues.") else t("当前没有可见 Issue。", "No visible issues were returned."),
            if (issues.isNotEmpty()) {
                issues.take(8).joinToString("\n") { issue ->
                    "- #${issue.number} · ${issue.title} · ${issue.state}"
                }
            } else {
                t("仓库里没有读取到可见 Issue。", "No visible issues were found in this repository.")
            },
            if (issues.isNotEmpty()) t("Issue 上下文已同步", "Issue context synced") else t("无 Issue 数据", "No issue data")
        )

        var createdIssue: ZiCodeGitHubIssue? = null

        val pullRequestIndex = addTool(t("同步 Pull Requests", "Sync pull requests"), "github.pull_request_list", "Pull Requests", t("正在读取当前 PR 列表。", "Loading current pull requests."))
        val pullRequests = gitHubService.listPullRequests(token, repoOwner, repoName).getOrNull().orEmpty()
        finishTool(
            pullRequestIndex,
            if (pullRequests.isNotEmpty()) ZiCodeToolStatus.SUCCESS else ZiCodeToolStatus.FAILED,
            if (pullRequests.isNotEmpty()) t("已获取 ${pullRequests.size} 个 PR。", "Loaded ${pullRequests.size} pull requests.") else t("当前没有可见 PR。", "No visible pull requests were returned."),
            if (pullRequests.isNotEmpty()) {
                pullRequests.take(8).joinToString("\n") { pullRequest ->
                    "- #${pullRequest.number} · ${pullRequest.title} · ${pullRequest.state}${if (pullRequest.draft) " · draft" else ""}"
                }
            } else {
                t("仓库里没有读取到可见 PR。", "No visible pull requests were found in this repository.")
            },
            if (pullRequests.isNotEmpty()) t("PR 上下文已同步", "Pull request context synced") else t("无 PR 数据", "No pull request data")
        )

        val workflowIndex = addTool(t("同步工作流", "Sync workflows"), "github.workflow_list", "Actions", t("正在读取 GitHub Actions 工作流。", "Loading GitHub Actions workflows."))
        val workflows = gitHubService.listWorkflows(token, repoOwner, repoName).getOrNull().orEmpty()
        finishTool(
            workflowIndex,
            if (workflows.isNotEmpty()) ZiCodeToolStatus.SUCCESS else ZiCodeToolStatus.FAILED,
            if (workflows.isNotEmpty()) t("检测到 ${workflows.size} 个工作流。", "Detected ${workflows.size} workflows.") else t("没有拿到工作流列表。", "Workflow data was not returned."),
            if (workflows.isNotEmpty()) workflows.joinToString("\n") { "- ${it.name} · ${it.path}" } else t("仓库里没有检测到可见的 Actions 工作流。", "No visible Actions workflows were found in the repository."),
            if (workflows.isNotEmpty()) t("工作流上下文已同步", "Workflow context synced") else t("无可见工作流", "No visible workflows")
        )

        val planningIndex = addTool(t("规划执行", "Plan execution"), "zicode.execution_plan", "ZiCode", t("正在让 ZiCode 决定下一步动作。", "ZiCode is deciding the next actions."))
        val executionPlanResult =
            planExecution(
                resolvedModel = resolvedModel,
                repo = repo,
                prompt = prompt,
                rootEntries = rootEntries,
                branches = branches,
                issues = issues,
                pullRequests = pullRequests,
                workflows = workflows,
                readmePreview = readmePreview,
                useChinese = useChinese
            )
        val executionPlan = executionPlanResult.getOrNull() ?: ZiCodeExecutionPlan()
        finishTool(
            planningIndex,
            if (executionPlanResult.isSuccess) ZiCodeToolStatus.SUCCESS else ZiCodeToolStatus.FAILED,
            if (executionPlanResult.isSuccess) t("ZiCode 已生成执行计划。", "ZiCode generated an execution plan.") else t("执行计划生成失败，已退回规则判断。", "Execution planning failed, so ZiCode fell back to rule-based decisions."),
            executionPlanResult.exceptionOrNull()?.message.orEmpty().ifBlank { buildExecutionPlanSummary(executionPlan, useChinese) },
            if (executionPlan.hasActions()) t("计划已写入上下文", "Execution plan added to context") else t("本轮只同步上下文", "Context-only pass")
        )

        if (executionPlan.createIssue || wantsCreateIssue(prompt)) {
            val issueTitle = executionPlan.issueTitle?.takeIf { it.isNotBlank() } ?: extractRequestedIssueTitle(prompt) ?: t("ZiCode 任务：", "ZiCode task: ") + prompt.take(72)
            val index = addTool(t("创建 Issue", "Create issue"), "github.issue_create", "Issues", t("正在创建新的 Issue。", "Creating a new issue."), issueTitle)
            val result =
                gitHubService.createIssue(
                    token = token,
                    owner = repoOwner,
                    repo = repoName,
                    title = issueTitle,
                    body = buildString {
                        appendLine(t("由 ZiCode 根据以下目标创建：", "Created by ZiCode for the following goal:"))
                        appendLine(prompt)
                        appendLine()
                        appendLine("${t("仓库", "Repository")}: ${repo.fullName}")
                    }
                )
            createdIssue = result.getOrNull()
            finishTool(
                index,
                if (createdIssue != null) ZiCodeToolStatus.SUCCESS else ZiCodeToolStatus.FAILED,
                if (createdIssue != null) t("Issue #${createdIssue.number} 已创建。", "Issue #${createdIssue.number} was created.") else t("Issue 创建失败。", "Issue creation failed."),
                result.exceptionOrNull()?.message.orEmpty(),
                createdIssue?.htmlUrl?.takeIf { it.isNotBlank() } ?: t("Issue 未创建", "Issue was not created")
            )
        }

        var createdBranch: ZiCodeGitHubBranch? = null
        val requestedBranchName = executionPlan.branchName?.takeIf { it.isNotBlank() } ?: extractRequestedBranchName(prompt)
        requestedBranchName?.let { branchName ->
            val sourceSha = branches.firstOrNull { it.name.equals(repo.defaultBranch, true) }?.sha ?: branches.firstOrNull()?.sha
            if (!sourceSha.isNullOrBlank() && (executionPlan.branchName?.isNotBlank() == true || wantsCreateBranch(prompt))) {
                val index = addTool(t("创建分支", "Create branch"), "github.branch_create", "Branches", t("正在创建新分支。", "Creating a new branch."), branchName)
                val result = gitHubService.createBranch(token, repoOwner, repoName, branchName, sourceSha)
                createdBranch = result.getOrNull()
                finishTool(
                    index,
                    if (createdBranch != null) ZiCodeToolStatus.SUCCESS else ZiCodeToolStatus.FAILED,
                    if (createdBranch != null) t("分支 `$branchName` 已创建。", "Branch `$branchName` was created.") else t("分支创建失败。", "Branch creation failed."),
                    result.exceptionOrNull()?.message.orEmpty(),
                    createdBranch?.sha ?: t("创建失败", "Creation failed")
                )
            }
        }

        val fallbackTargetFilePath = extractTargetFilePath(prompt)
        val deleteFilePath = executionPlan.deleteFilePath?.takeIf { it.isNotBlank() } ?: fallbackTargetFilePath?.takeIf { wantsDeleteFile(prompt) }
        val writeFilePath = executionPlan.writeFilePath?.takeIf { it.isNotBlank() } ?: fallbackTargetFilePath?.takeIf { wantsWriteFile(prompt) }

        if (createdBranch == null && (writeFilePath != null || deleteFilePath != null || wantsFileMutation(prompt))) {
            val defaultBranch = branches.firstOrNull { it.name.equals(repo.defaultBranch, ignoreCase = true) }
            if (defaultBranch?.protectedBranch == true && defaultBranch.sha.isNotBlank()) {
                val autoBranchName = buildAutoBranchName()
                val index = addTool(t("准备工作分支", "Prepare working branch"), "github.branch_prepare", "Branches", t("默认分支受保护，正在准备工作分支。", "The default branch is protected, so ZiCode is preparing a working branch."), autoBranchName)
                val result = gitHubService.createBranch(token, repoOwner, repoName, autoBranchName, defaultBranch.sha)
                createdBranch = result.getOrNull()
                finishTool(
                    index,
                    if (createdBranch != null) ZiCodeToolStatus.SUCCESS else ZiCodeToolStatus.FAILED,
                    if (createdBranch != null) t("已切到工作分支 `${createdBranch?.name}`。", "Working branch `${createdBranch?.name}` is ready.") else t("工作分支创建失败。", "Failed to create a working branch."),
                    result.exceptionOrNull()?.message.orEmpty(),
                    createdBranch?.name ?: t("未创建分支", "Branch not created")
                )
            }
        }

        var latestFileCommit: ZiCodeGitHubCommit? = null
        if (!deleteFilePath.isNullOrBlank()) {
            val deleteIndex = addTool(t("删除文件", "Delete file"), "github.file_delete", "Contents", t("正在删除仓库文件。", "Deleting repository file."), deleteFilePath)
            val nodeResult = gitHubService.fetchNode(token, repoOwner, repoName, deleteFilePath)
            val node = nodeResult.getOrNull()?.takeIf { it.type == "file" }
            if (node?.sha.isNullOrBlank()) {
                finishTool(
                    deleteIndex,
                    ZiCodeToolStatus.FAILED,
                    t("删除失败。", "Delete failed."),
                    nodeResult.exceptionOrNull()?.message.orEmpty().ifBlank { t("没有找到可删除的文件。", "No deletable file was found.") },
                    t("文件未删除", "File was not deleted")
                )
            } else {
                val branchName = createdBranch?.name ?: repo.defaultBranch
                val result =
                    gitHubService.deleteFile(
                        token = token,
                        owner = repoOwner,
                        repo = repoName,
                        path = deleteFilePath,
                        sha = node.sha.orEmpty(),
                        message = buildDeleteCommitMessage(deleteFilePath, useChinese),
                        branch = branchName
                    )
                latestFileCommit = result.getOrNull()
                finishTool(
                    deleteIndex,
                    if (latestFileCommit != null) ZiCodeToolStatus.SUCCESS else ZiCodeToolStatus.FAILED,
                    if (latestFileCommit != null) t("文件 `${deleteFilePath}` 已删除。", "File `${deleteFilePath}` was deleted.") else t("删除失败。", "Delete failed."),
                    result.exceptionOrNull()?.message.orEmpty(),
                    latestFileCommit?.sha ?: t("文件未删除", "File was not deleted")
                )
            }
        }
        if (!writeFilePath.isNullOrBlank()) {
            val writeIndex = addTool(t("写入文件", "Write file"), "github.file_write", "Contents", t("正在写入仓库文件。", "Writing repository file."), writeFilePath)
            val nodeResult = gitHubService.fetchNode(token, repoOwner, repoName, writeFilePath)
            val existingNode = nodeResult.getOrNull()
            val existingFileNode = existingNode?.takeIf { it.type == "file" }
            val existingPreview =
                if (existingFileNode != null) {
                    gitHubService.readFile(token, repoOwner, repoName, writeFilePath).getOrNull()
                } else {
                    null
                }
            val content =
                executionPlan.writeFileContent?.takeIf { it.isNotBlank() }
                    ?: extractEmbeddedFileContent(prompt)?.takeIf { it.isNotBlank() }
                    ?: generateFileContent(
                        resolvedModel = resolvedModel,
                        repo = repo,
                        prompt = prompt,
                        path = writeFilePath,
                        existingPreview = existingPreview,
                        readmePreview = readmePreview,
                        useChinese = useChinese
                    ).getOrNull()
            if (content.isNullOrBlank()) {
                finishTool(
                    writeIndex,
                    ZiCodeToolStatus.FAILED,
                    t("文件内容生成失败。", "Failed to generate file content."),
                    t("这轮没有从提示词里拿到可直接写入的内容，模型也没有成功补全文件内容。", "No writable content was found in the prompt, and the model did not generate file content successfully."),
                    t("文件未写入", "File was not written")
                )
            } else {
                val branchName = createdBranch?.name ?: repo.defaultBranch
                val result =
                    gitHubService.createOrUpdateFile(
                        token = token,
                        owner = repoOwner,
                        repo = repoName,
                        path = writeFilePath,
                        content = content,
                        message = buildWriteCommitMessage(writeFilePath, existingFileNode != null, useChinese),
                        branch = branchName,
                        sha = existingFileNode?.sha
                    )
                latestFileCommit = result.getOrNull()
                finishTool(
                    writeIndex,
                    if (latestFileCommit != null) ZiCodeToolStatus.SUCCESS else ZiCodeToolStatus.FAILED,
                    if (latestFileCommit != null) {
                        if (existingFileNode != null) {
                            t("文件 `${writeFilePath}` 已更新。", "File `${writeFilePath}` was updated.")
                        } else {
                            t("文件 `${writeFilePath}` 已创建。", "File `${writeFilePath}` was created.")
                        }
                    } else {
                        t("文件写入失败。", "File write failed.")
                    },
                    result.exceptionOrNull()?.message.orEmpty(),
                    latestFileCommit?.sha ?: t("文件未写入", "File was not written")
                )
            }
        }

        val prHeadBranch =
            createdBranch?.name ?: requestedBranchName?.takeIf { requested ->
                branches.any { it.name.equals(requested, ignoreCase = true) }
            }
        var createdPullRequest: ZiCodeGitHubPullRequest? = null
        if (executionPlan.createPullRequest || wantsCreatePullRequest(prompt)) {
            if (prHeadBranch.isNullOrBlank()) {
                val index = addTool(t("创建 Pull Request", "Create pull request"), "github.pull_request_create", "Pull Requests", t("正在准备创建 PR。", "Preparing to create a pull request."))
                finishTool(
                    index,
                    ZiCodeToolStatus.FAILED,
                    t("当前没有可用工作分支。", "No working branch is available."),
                    t("这轮没有新建分支，也没有在提示词里识别到可直接发起 PR 的现有分支。", "This turn did not create a branch and no existing PR source branch could be inferred from the prompt."),
                    t("PR 未创建", "Pull request was not created")
                )
            } else {
                val pullRequestTitle = executionPlan.pullRequestTitle?.takeIf { it.isNotBlank() } ?: extractRequestedPullRequestTitle(prompt) ?: t("ZiCode 更新：", "ZiCode update: ") + prHeadBranch
                val index = addTool(t("创建 Pull Request", "Create pull request"), "github.pull_request_create", "Pull Requests", t("正在创建新的 PR。", "Creating a new pull request."), pullRequestTitle)
                val result =
                    gitHubService.createPullRequest(
                        token = token,
                        owner = repoOwner,
                        repo = repoName,
                        title = pullRequestTitle,
                        head = prHeadBranch,
                        base = repo.defaultBranch,
                        body = buildString {
                            appendLine(t("由 ZiCode 根据以下目标创建：", "Created by ZiCode for the following goal:"))
                            appendLine(prompt)
                            appendLine()
                            appendLine("${t("来源分支", "Source branch")}: $prHeadBranch")
                            appendLine("${t("目标分支", "Base branch")}: ${repo.defaultBranch}")
                        }
                    )
                createdPullRequest = result.getOrNull()
                finishTool(
                    index,
                    if (createdPullRequest != null) ZiCodeToolStatus.SUCCESS else ZiCodeToolStatus.FAILED,
                    if (createdPullRequest != null) t("PR #${createdPullRequest.number} 已创建。", "Pull request #${createdPullRequest.number} was created.") else t("PR 创建失败。", "Pull request creation failed."),
                    result.exceptionOrNull()?.message.orEmpty(),
                    createdPullRequest?.htmlUrl?.takeIf { it.isNotBlank() } ?: t("PR 未创建", "Pull request was not created")
                )
            }
        }

        var dispatchedWorkflowName: String? = null
        if ((executionPlan.dispatchWorkflow || wantsWorkflowDispatch(prompt)) && workflows.isNotEmpty()) {
            resolveWorkflowDispatchTarget(executionPlan.workflowName?.takeIf { it.isNotBlank() } ?: prompt, workflows)?.let { workflow ->
                val index = addTool(t("触发工作流", "Dispatch workflow"), "github.workflow_dispatch", "Actions", t("正在触发工作流执行。", "Dispatching workflow execution."), workflow.name)
                val result = gitHubService.dispatchWorkflow(token, repoOwner, repoName, workflow.id.toString(), createdBranch?.name ?: repo.defaultBranch)
                if (result.isSuccess) {
                    dispatchedWorkflowName = workflow.name
                    delay(1200)
                }
                finishTool(
                    index,
                    if (result.isSuccess) ZiCodeToolStatus.SUCCESS else ZiCodeToolStatus.FAILED,
                    if (result.isSuccess) t("工作流 `${workflow.name}` 已触发。", "Workflow `${workflow.name}` was dispatched.") else t("工作流触发失败。", "Workflow dispatch failed."),
                    result.exceptionOrNull()?.message.orEmpty(),
                    if (result.isSuccess) createdBranch?.name ?: repo.defaultBranch else t("触发失败", "Dispatch failed")
                )
            }
        }

        val runIndex = addTool(t("读取运行记录", "Read workflow runs"), "github.workflow_runs", "Actions", t("正在同步工作流运行状态。", "Syncing workflow run status."))
        var workflowRuns = gitHubService.listWorkflowRuns(token, repoOwner, repoName).getOrNull().orEmpty()
        finishTool(
            runIndex,
            if (workflowRuns.isNotEmpty()) ZiCodeToolStatus.SUCCESS else ZiCodeToolStatus.FAILED,
            if (workflowRuns.isNotEmpty()) t("已获取 ${workflowRuns.size} 条运行记录。", "Loaded ${workflowRuns.size} workflow runs.") else t("当前没有工作流运行记录。", "No workflow runs are visible right now."),
            if (workflowRuns.isNotEmpty()) workflowRuns.take(8).joinToString("\n") { "- ${it.name} · ${it.status}${it.conclusion?.let { c -> " · $c" } ?: ""}" } else t("还没有可见的 workflow run。", "No visible workflow run was returned."),
            if (workflowRuns.isNotEmpty()) t("Actions 运行状态已同步", "Actions run status synced") else t("无运行记录", "No workflow runs")
        )

        if (workflowRuns.isNotEmpty() && (executionPlan.cancelWorkflow || wantsCancelWorkflow(prompt))) {
            resolveWorkflowRunTarget(executionPlan.targetRunName?.takeIf { it.isNotBlank() } ?: prompt, workflowRuns)?.let { workflowRun ->
                val index = addTool(t("取消运行", "Cancel workflow run"), "github.workflow_cancel", "Actions", t("正在取消运行中的工作流。", "Cancelling the workflow run."), workflowRun.name)
                val result = gitHubService.cancelWorkflowRun(token, repoOwner, repoName, workflowRun.id)
                if (result.isSuccess) {
                    delay(1000)
                    workflowRuns = gitHubService.listWorkflowRuns(token, repoOwner, repoName).getOrNull().orEmpty()
                }
                finishTool(
                    index,
                    if (result.isSuccess) ZiCodeToolStatus.SUCCESS else ZiCodeToolStatus.FAILED,
                    if (result.isSuccess) t("运行 `${workflowRun.name}` 已请求取消。", "Workflow run `${workflowRun.name}` was asked to cancel.") else t("取消运行失败。", "Failed to cancel the workflow run."),
                    result.exceptionOrNull()?.message.orEmpty(),
                    if (result.isSuccess) workflowRun.id.toString() else t("取消失败", "Cancel failed")
                )
            }
        }

        if (workflowRuns.isNotEmpty() && (executionPlan.rerunWorkflow || wantsRerunWorkflow(prompt))) {
            resolveWorkflowRunTarget(executionPlan.targetRunName?.takeIf { it.isNotBlank() } ?: prompt, workflowRuns)?.let { workflowRun ->
                val index = addTool(t("重跑工作流", "Rerun workflow"), "github.workflow_rerun", "Actions", t("正在重新运行工作流。", "Rerunning the workflow."), workflowRun.name)
                val result = gitHubService.rerunWorkflowRun(token, repoOwner, repoName, workflowRun.id)
                if (result.isSuccess) {
                    delay(1200)
                    workflowRuns = gitHubService.listWorkflowRuns(token, repoOwner, repoName).getOrNull().orEmpty()
                }
                finishTool(
                    index,
                    if (result.isSuccess) ZiCodeToolStatus.SUCCESS else ZiCodeToolStatus.FAILED,
                    if (result.isSuccess) t("工作流 `${workflowRun.name}` 已重跑。", "Workflow `${workflowRun.name}` was rerun.") else t("重跑工作流失败。", "Failed to rerun the workflow."),
                    result.exceptionOrNull()?.message.orEmpty(),
                    if (result.isSuccess) workflowRun.id.toString() else t("重跑失败", "Rerun failed")
                )
            }
        }

        val traceIndex = addTool(t("展开运行日志", "Expand workflow trace"), "github.workflow_trace", "Actions", t("正在读取最新运行作业详情。", "Reading the latest workflow job trace."), workflowRuns.firstOrNull()?.id?.toString().orEmpty())
        val workflowTrace = workflowRuns.firstOrNull()?.let { gitHubService.readWorkflowRunTrace(token, repoOwner, repoName, it.id).getOrNull().orEmpty() }.orEmpty()
        finishTool(
            traceIndex,
            if (workflowTrace.isNotBlank()) ZiCodeToolStatus.SUCCESS else ZiCodeToolStatus.FAILED,
            if (workflowTrace.isNotBlank()) t("运行详情已展开。", "Workflow trace expanded.") else t("运行详情暂不可用。", "Workflow trace is unavailable right now."),
            workflowTrace.take(900).ifBlank { t("没有拿到作业步骤。", "No job steps were returned.") },
            if (workflowTrace.isNotBlank()) t("运行日志已加入上下文", "Workflow trace added to context") else t("日志缺失", "Trace missing")
        )

        val artifactsIndex = addTool(t("读取构建产物", "Read artifacts"), "github.artifact_list", "Artifacts", t("正在同步最近构建产物。", "Syncing recent workflow artifacts."), workflowRuns.firstOrNull()?.id?.toString().orEmpty())
        val artifacts = gitHubService.listArtifacts(token, repoOwner, repoName, workflowRuns.firstOrNull()?.id).getOrNull().orEmpty()
        finishTool(
            artifactsIndex,
            if (artifacts.isNotEmpty()) ZiCodeToolStatus.SUCCESS else ZiCodeToolStatus.FAILED,
            if (artifacts.isNotEmpty()) t("已获取 ${artifacts.size} 个构建产物。", "Loaded ${artifacts.size} workflow artifacts.") else t("当前没有可见构建产物。", "No visible workflow artifacts were returned."),
            if (artifacts.isNotEmpty()) {
                artifacts.take(8).joinToString("\n") { artifact ->
                    "- ${artifact.name} · ${artifact.sizeInBytes} bytes${if (artifact.expired) " · expired" else ""}"
                }
            } else {
                t("最近一次运行没有暴露可见产物。", "The latest run did not expose visible artifacts.")
            },
            if (artifacts.isNotEmpty()) t("构建产物已同步", "Artifacts synced") else t("无构建产物", "No artifacts")
        )

        val releaseIndex = addTool(t("同步发布", "Sync releases"), "github.release_list", "Release", t("正在读取 Release 列表。", "Loading releases."))
        var releases = gitHubService.listReleases(token, repoOwner, repoName).getOrNull().orEmpty()
        finishTool(
            releaseIndex,
            if (releases.isNotEmpty()) ZiCodeToolStatus.SUCCESS else ZiCodeToolStatus.FAILED,
            if (releases.isNotEmpty()) t("检测到 ${releases.size} 个 Release。", "Detected ${releases.size} releases.") else t("当前没有 Release 数据。", "No release data is available right now."),
            if (releases.isNotEmpty()) releases.take(6).joinToString("\n") { "- ${it.tagName}${it.name.takeIf { name -> name.isNotBlank() }?.let { name -> " · $name" } ?: ""}" } else t("仓库里还没有可见 Release。", "No visible releases were found in the repository."),
            if (releases.isNotEmpty()) t("发布历史已同步", "Release history synced") else t("无发布数据", "No release data")
        )

        var createdRelease: ZiCodeGitHubRelease? = null
        if (executionPlan.createRelease || wantsCreateRelease(prompt)) {
            val tagName = executionPlan.releaseTag?.takeIf { it.isNotBlank() } ?: extractReleaseTag(prompt) ?: buildAutoReleaseTag()
            val releaseName = executionPlan.releaseName?.takeIf { it.isNotBlank() } ?: extractReleaseName(prompt) ?: tagName
            val index = addTool(t("创建 Release", "Create release"), "github.release_create", "Release", t("正在创建新的 Release。", "Creating a new release."), tagName)
            val result =
                gitHubService.createRelease(
                    token = token,
                    owner = repoOwner,
                    repo = repoName,
                    tagName = tagName,
                    releaseName = releaseName,
                    body =
                        buildString {
                            appendLine(t("由 ZiCode 依据以下目标创建：", "Created by ZiCode for the following goal:"))
                            appendLine(prompt)
                            latestFileCommit?.let {
                                appendLine()
                                appendLine("${t("最近提交", "Latest commit")}: ${it.sha.take(7)}")
                            }
                        }.trim(),
                    targetCommitish = createdBranch?.name ?: repo.defaultBranch
                )
            createdRelease = result.getOrNull()
            val release = createdRelease
            if (release != null) {
                releases = listOf(release) + releases.filterNot { it.id == release.id }
            }
            finishTool(
                index,
                if (createdRelease != null) ZiCodeToolStatus.SUCCESS else ZiCodeToolStatus.FAILED,
                if (createdRelease != null) t("Release `${createdRelease?.tagName}` 已创建。", "Release `${createdRelease?.tagName}` was created.") else t("Release 创建失败。", "Release creation failed."),
                result.exceptionOrNull()?.message.orEmpty(),
                createdRelease?.htmlUrl ?: t("Release 未创建", "Release was not created")
            )
        }

        val pagesIndex = addTool(t("检查 Pages", "Check Pages"), "github.pages_get", "Pages", t("正在检查 GitHub Pages 状态。", "Checking GitHub Pages status."))
        val pagesInfo = gitHubService.fetchPagesInfo(token, repoOwner, repoName).getOrNull()
        val pagesDetail =
            when {
                pagesInfo == null -> t("没有拿到 Pages 信息。", "Pages data was not returned.")
                pagesInfo.status == "not_configured" -> t("当前仓库没有启用 GitHub Pages。", "GitHub Pages is not configured for this repository.")
                else -> buildString {
                    appendLine("${t("状态", "Status")}: ${pagesInfo.status}")
                    pagesInfo.htmlUrl?.let { appendLine("${t("地址", "URL")}: $it") }
                    pagesInfo.sourceBranch?.let { appendLine("${t("来源分支", "Source branch")}: $it") }
                    pagesInfo.sourcePath?.let { append("${t("来源路径", "Source path")}: $it") }
                }
            }
        finishTool(
            pagesIndex,
            if (pagesInfo != null && pagesInfo.status != "not_configured") ZiCodeToolStatus.SUCCESS else ZiCodeToolStatus.FAILED,
            if (pagesInfo == null) t("GitHub Pages 状态不可用。", "GitHub Pages status is unavailable.") else if (pagesInfo.status == "not_configured") t("GitHub Pages 尚未配置。", "GitHub Pages has not been configured.") else t("GitHub Pages 状态已同步。", "GitHub Pages status synced."),
            pagesDetail,
            if (pagesInfo == null) t("Pages 状态缺失", "Pages status missing") else if (pagesInfo.status == "not_configured") t("Pages 未启用", "Pages not configured") else t("Pages 已接入上下文", "Pages added to context")
        )

        val summaryIndex = addTool(t("整理结果", "Build summary"), "agent.summary_build", "ZiCode", t("正在使用 ZiCode 默认模型整理结论。", "Using the ZiCode default model to build the summary."))
        val fallback =
            buildFallbackSummary(
                repo = repo,
                prompt = prompt,
                rootEntries = rootEntries,
                branches = branches,
                commits = commits,
                issues = issues,
                pullRequests = pullRequests,
                workflows = workflows,
                workflowRuns = workflowRuns,
                workflowTrace = workflowTrace,
                artifacts = artifacts,
                releases = releases,
                pagesInfo = pagesInfo,
                readmePreview = readmePreview,
                latestFileCommit = latestFileCommit,
                createdBranch = createdBranch,
                createdIssue = createdIssue,
                createdPullRequest = createdPullRequest,
                createdRelease = createdRelease,
                dispatchedWorkflowName = dispatchedWorkflowName,
                useChinese = useChinese
            )
        val modelResult =
            chatApiClient.chatCompletions(
                provider = resolvedModel.provider,
                modelId = resolvedModel.remoteModelId,
                messages = listOf(
                    Message(role = "system", content = "You are ZiCode. Use the GitHub context to execute and summarize repository work. Respond in the user's language. Be concise, concrete, and never invent repository facts."),
                    Message(
                        role = "user",
                        content = buildModelContext(
                            repo = repo,
                            prompt = prompt,
                            rootEntries = rootEntries,
                            branches = branches,
                            commits = commits,
                            issues = issues,
                            pullRequests = pullRequests,
                            workflows = workflows,
                            workflowRuns = workflowRuns,
                            workflowTrace = workflowTrace,
                            artifacts = artifacts,
                            releases = releases,
                            pagesInfo = pagesInfo,
                            readmePreview = readmePreview,
                            latestFileCommit = latestFileCommit,
                            createdBranch = createdBranch,
                            createdIssue = createdIssue,
                            createdPullRequest = createdPullRequest,
                            createdRelease = createdRelease,
                            dispatchedWorkflowName = dispatchedWorkflowName,
                            useChinese = useChinese
                        )
                    )
                ),
                extraHeaders = resolvedModel.model.headers,
                reasoningEffort = resolvedModel.model.reasoningEffort,
                enableThinking = resolvedModel.enableThinking,
                maxTokens = 1400
            )
        val response = modelResult.getOrNull()?.trim().takeIf { !it.isNullOrBlank() } ?: fallback
        finishTool(
            summaryIndex,
            if (modelResult.isSuccess) ZiCodeToolStatus.SUCCESS else ZiCodeToolStatus.FAILED,
            if (modelResult.isSuccess) t("ZiCode 结果已整理完成。", "ZiCode summary is ready.") else t("模型总结失败，已退回本地总结。", "Model summarization failed, so ZiCode used a local fallback summary."),
            response.take(900),
            if (modelResult.isSuccess) t("默认模型已返回结论", "Default model returned a summary") else t("已启用本地回退总结", "Fallback summary enabled")
        )

        repository.updateTurn(sessionId, turnId) { turn ->
            val primaryResultLink =
                createdRelease?.htmlUrl?.takeIf { it.isNotBlank() }
                    ?: createdPullRequest?.htmlUrl?.takeIf { it.isNotBlank() }
                    ?: createdIssue?.htmlUrl?.takeIf { it.isNotBlank() }
                    ?: workflowRuns.firstOrNull()?.htmlUrl?.takeIf { it.isNotBlank() }
                    ?: pagesInfo?.htmlUrl?.takeIf { it.isNotBlank() }
                    ?: repo.homepageUrl?.takeIf { it.isNotBlank() }
                    ?: repo.htmlUrl
            turn.copy(
                response = response,
                status = ZiCodeRunStatus.SUCCESS,
                toolCalls = tools,
                resultLink = primaryResultLink,
                resultLabel = when {
                    createdRelease?.htmlUrl?.isNotBlank() == true -> t("打开 Release", "Open release")
                    createdPullRequest?.htmlUrl?.isNotBlank() == true -> t("打开 Pull Request", "Open pull request")
                    createdIssue?.htmlUrl?.isNotBlank() == true -> t("打开 Issue", "Open issue")
                    workflowRuns.firstOrNull()?.htmlUrl?.isNotBlank() == true -> t("打开工作流运行", "Open workflow run")
                    pagesInfo?.htmlUrl?.isNotBlank() == true -> t("打开 GitHub Pages", "Open GitHub Pages")
                    !repo.homepageUrl.isNullOrBlank() -> t("打开项目主页", "Open project homepage")
                    else -> t("打开 GitHub 仓库", "Open GitHub repo")
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

    private fun wantsCreateIssue(prompt: String): Boolean {
        return prompt.contains("create issue", ignoreCase = true) ||
            prompt.contains("open issue", ignoreCase = true) ||
            prompt.contains("new issue", ignoreCase = true) ||
            prompt.contains("创建 issue", ignoreCase = true) ||
            prompt.contains("新建 issue", ignoreCase = true) ||
            prompt.contains("创建Issue") ||
            prompt.contains("新建Issue") ||
            prompt.contains("创建问题")
    }

    private fun wantsCreatePullRequest(prompt: String): Boolean {
        return prompt.contains("create pr", ignoreCase = true) ||
            prompt.contains("open pr", ignoreCase = true) ||
            prompt.contains("create pull request", ignoreCase = true) ||
            prompt.contains("open pull request", ignoreCase = true) ||
            prompt.contains("创建 pr", ignoreCase = true) ||
            prompt.contains("新建 pr", ignoreCase = true) ||
            prompt.contains("创建PR") ||
            prompt.contains("新建PR") ||
            prompt.contains("创建拉取请求") ||
            prompt.contains("新建拉取请求")
    }

    private fun wantsWriteFile(prompt: String): Boolean {
        return prompt.contains("write file", ignoreCase = true) ||
            prompt.contains("update file", ignoreCase = true) ||
            prompt.contains("edit file", ignoreCase = true) ||
            prompt.contains("add file", ignoreCase = true) ||
            prompt.contains("create file", ignoreCase = true) ||
            prompt.contains("save file", ignoreCase = true) ||
            prompt.contains("写入文件") ||
            prompt.contains("新增文件") ||
            prompt.contains("更新文件") ||
            prompt.contains("修改文件") ||
            prompt.contains("创建文件")
    }

    private fun wantsDeleteFile(prompt: String): Boolean {
        return prompt.contains("delete file", ignoreCase = true) ||
            prompt.contains("remove file", ignoreCase = true) ||
            prompt.contains("drop file", ignoreCase = true) ||
            prompt.contains("删除文件") ||
            prompt.contains("移除文件") ||
            prompt.contains("删掉文件")
    }

    private fun wantsFileMutation(prompt: String): Boolean {
        return wantsWriteFile(prompt) || wantsDeleteFile(prompt)
    }

    private fun wantsRerunWorkflow(prompt: String): Boolean {
        return prompt.contains("rerun workflow", ignoreCase = true) ||
            prompt.contains("rerun run", ignoreCase = true) ||
            prompt.contains("rerun action", ignoreCase = true) ||
            prompt.contains("重新运行工作流") ||
            prompt.contains("重跑工作流") ||
            prompt.contains("重新跑工作流")
    }

    private fun wantsCancelWorkflow(prompt: String): Boolean {
        return prompt.contains("cancel workflow", ignoreCase = true) ||
            prompt.contains("cancel run", ignoreCase = true) ||
            prompt.contains("stop workflow", ignoreCase = true) ||
            prompt.contains("取消工作流") ||
            prompt.contains("取消运行") ||
            prompt.contains("停止工作流")
    }

    private fun wantsCreateRelease(prompt: String): Boolean {
        return prompt.contains("create release", ignoreCase = true) ||
            prompt.contains("publish release", ignoreCase = true) ||
            prompt.contains("new release", ignoreCase = true) ||
            prompt.contains("创建 release", ignoreCase = true) ||
            prompt.contains("创建发布") ||
            prompt.contains("发布版本") ||
            prompt.contains("发版")
    }

    private suspend fun planExecution(
        resolvedModel: ResolvedZiCodeModel,
        repo: ZiCodeRemoteRepo,
        prompt: String,
        rootEntries: List<ZiCodeRepoNode>,
        branches: List<ZiCodeGitHubBranch>,
        issues: List<ZiCodeGitHubIssue>,
        pullRequests: List<ZiCodeGitHubPullRequest>,
        workflows: List<ZiCodeGitHubWorkflow>,
        readmePreview: ZiCodeFilePreview?,
        useChinese: Boolean
    ): Result<ZiCodeExecutionPlan> {
        return runCatching {
            val result =
                chatApiClient.chatCompletions(
                    provider = resolvedModel.provider,
                    modelId = resolvedModel.remoteModelId,
                    messages = listOf(
                        Message(
                            role = "system",
                            content = if (useChinese) {
                                "你是 ZiCode。请只返回 JSON 对象，不要解释，不要 Markdown。根据用户目标和 GitHub 上下文决定下一步动作。仅在确实需要时填写字段；不需要的字段用 null 或 false。"
                            } else {
                                "You are ZiCode. Return JSON only, with no explanation and no Markdown. Decide the next GitHub actions from the user goal and repository context. Fill fields only when needed; otherwise use null or false."
                            }
                        ),
                        Message(
                            role = "user",
                            content =
                                buildString {
                                    appendLine("${tr(useChinese, "仓库", "Repository")}: ${repo.fullName}")
                                    appendLine("${tr(useChinese, "默认分支", "Default branch")}: ${repo.defaultBranch}")
                                    appendLine("${tr(useChinese, "用户目标", "User goal")}: $prompt")
                                    appendLine()
                                    appendLine(tr(useChinese, "根目录", "Root tree"))
                                    rootEntries.take(12).forEach { appendLine("- ${it.path} [${it.type}]") }
                                    appendLine()
                                    appendLine(tr(useChinese, "分支", "Branches"))
                                    branches.take(8).forEach { appendLine("- ${it.name}${if (it.protectedBranch) " · protected" else ""}") }
                                    appendLine()
                                    appendLine(tr(useChinese, "Issues", "Issues"))
                                    issues.take(6).forEach { appendLine("- #${it.number} ${it.title}") }
                                    appendLine()
                                    appendLine(tr(useChinese, "Pull Requests", "Pull requests"))
                                    pullRequests.take(6).forEach { appendLine("- #${it.number} ${it.title}") }
                                    appendLine()
                                    appendLine(tr(useChinese, "工作流", "Workflows"))
                                    workflows.take(6).forEach { appendLine("- ${it.name}") }
                                    readmePreview?.content?.takeIf { it.isNotBlank() }?.let {
                                        appendLine()
                                        appendLine("README:")
                                        appendLine(it.take(1000))
                                    }
                                    appendLine()
                                    appendLine(
                                        if (useChinese) {
                                            """
                                            只返回 JSON，对象字段固定为：
                                            {
                                              "branchName": string|null,
                                              "createIssue": boolean,
                                              "issueTitle": string|null,
                                              "createPullRequest": boolean,
                                              "pullRequestTitle": string|null,
                                              "writeFilePath": string|null,
                                              "writeFileContent": string|null,
                                              "deleteFilePath": string|null,
                                              "dispatchWorkflow": boolean,
                                              "workflowName": string|null,
                                              "rerunWorkflow": boolean,
                                              "cancelWorkflow": boolean,
                                              "targetRunName": string|null,
                                              "createRelease": boolean,
                                              "releaseTag": string|null,
                                              "releaseName": string|null
                                            }
                                            """.trimIndent()
                                        } else {
                                            """
                                            Return JSON only with this exact schema:
                                            {
                                              "branchName": string|null,
                                              "createIssue": boolean,
                                              "issueTitle": string|null,
                                              "createPullRequest": boolean,
                                              "pullRequestTitle": string|null,
                                              "writeFilePath": string|null,
                                              "writeFileContent": string|null,
                                              "deleteFilePath": string|null,
                                              "dispatchWorkflow": boolean,
                                              "workflowName": string|null,
                                              "rerunWorkflow": boolean,
                                              "cancelWorkflow": boolean,
                                              "targetRunName": string|null,
                                              "createRelease": boolean,
                                              "releaseTag": string|null,
                                              "releaseName": string|null
                                            }
                                            """.trimIndent()
                                        }
                                    )
                                }
                        )
                    ),
                    extraHeaders = resolvedModel.model.headers,
                    reasoningEffort = resolvedModel.model.reasoningEffort,
                    enableThinking = resolvedModel.enableThinking,
                    maxTokens = 1200
                ).getOrThrow()
            parseExecutionPlan(stripMarkdownCodeFences(result))
        }
    }

    private fun parseExecutionPlan(raw: String): ZiCodeExecutionPlan {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ZiCodeExecutionPlan()
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        if (start < 0 || end <= start) return ZiCodeExecutionPlan()
        val json = JsonParser.parseString(trimmed.substring(start, end + 1)).asJsonObject
        return ZiCodeExecutionPlan(
            branchName = json.stringOrNull("branchName"),
            createIssue = json.booleanOrFalse("createIssue"),
            issueTitle = json.stringOrNull("issueTitle"),
            createPullRequest = json.booleanOrFalse("createPullRequest"),
            pullRequestTitle = json.stringOrNull("pullRequestTitle"),
            writeFilePath = json.stringOrNull("writeFilePath"),
            writeFileContent = json.stringOrNull("writeFileContent"),
            deleteFilePath = json.stringOrNull("deleteFilePath"),
            dispatchWorkflow = json.booleanOrFalse("dispatchWorkflow"),
            workflowName = json.stringOrNull("workflowName"),
            rerunWorkflow = json.booleanOrFalse("rerunWorkflow"),
            cancelWorkflow = json.booleanOrFalse("cancelWorkflow"),
            targetRunName = json.stringOrNull("targetRunName"),
            createRelease = json.booleanOrFalse("createRelease"),
            releaseTag = json.stringOrNull("releaseTag"),
            releaseName = json.stringOrNull("releaseName")
        )
    }

    private fun buildExecutionPlanSummary(
        plan: ZiCodeExecutionPlan,
        useChinese: Boolean
    ): String {
        fun t(zh: String, en: String): String = tr(useChinese, zh, en)
        if (!plan.hasActions()) {
            return t("本轮只同步仓库上下文，暂不主动发起写入、发布或工作流动作。", "This pass only synchronizes repository context and does not proactively write files, ship releases, or trigger workflows.")
        }
        return buildString {
            plan.branchName?.let { appendLine("- ${t("创建分支", "Create branch")}: $it") }
            if (plan.createIssue) appendLine("- ${t("创建 Issue", "Create issue")}: ${plan.issueTitle ?: t("自动命名", "auto title")}")
            if (plan.createPullRequest) appendLine("- ${t("创建 PR", "Create pull request")}: ${plan.pullRequestTitle ?: t("自动命名", "auto title")}")
            plan.writeFilePath?.let { appendLine("- ${t("写入文件", "Write file")}: $it") }
            plan.deleteFilePath?.let { appendLine("- ${t("删除文件", "Delete file")}: $it") }
            if (plan.dispatchWorkflow) appendLine("- ${t("触发工作流", "Dispatch workflow")}: ${plan.workflowName ?: t("自动选择", "auto select")}")
            if (plan.rerunWorkflow) appendLine("- ${t("重跑工作流", "Rerun workflow")}: ${plan.targetRunName ?: t("最新运行", "latest run")}")
            if (plan.cancelWorkflow) appendLine("- ${t("取消工作流", "Cancel workflow")}: ${plan.targetRunName ?: t("最新运行", "latest run")}")
            if (plan.createRelease) appendLine("- ${t("创建 Release", "Create release")}: ${plan.releaseTag ?: t("自动版本号", "auto tag")}")
        }.trim()
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

    private fun extractRequestedIssueTitle(prompt: String): String? {
        val patterns =
            listOf(
                Regex("""(?i)(?:create|open|new)\s+issue[:：\s"]+([^\n"]+)"""),
                Regex("""(?:创建|新建)(?:\s*Issue|\s*issue|问题)[:：\s]+([^\n]+)""")
            )
        return patterns.asSequence()
            .mapNotNull { it.find(prompt)?.groupValues?.getOrNull(1) }
            .map { it.trim().trim('"', '“', '”') }
            .firstOrNull { it.isNotBlank() }
            ?.take(120)
    }

    private fun extractRequestedPullRequestTitle(prompt: String): String? {
        val patterns =
            listOf(
                Regex("""(?i)(?:create|open)\s+(?:pr|pull request)[:：\s"]+([^\n"]+)"""),
                Regex("""(?:创建|新建)(?:\s*PR|\s*pr|拉取请求)[:：\s]+([^\n]+)""")
            )
        return patterns.asSequence()
            .mapNotNull { it.find(prompt)?.groupValues?.getOrNull(1) }
            .map { it.trim().trim('"', '“', '”') }
            .firstOrNull { it.isNotBlank() }
            ?.take(120)
    }

    private fun extractTargetFilePath(prompt: String): String? {
        val patterns =
            listOf(
                Regex("""`([^`\n]+(?:\.[^`\n]+|/[^`\n]+))`"""),
                Regex("""(?i)(?:create|update|edit|write|delete|remove)\s+(?:the\s+)?file\s+([A-Za-z0-9._/\-]+)"""),
                Regex("""(?:创建|更新|修改|写入|删除|移除)(?:这个)?文件[:：\s]+([A-Za-z0-9._/\-]+)"""),
                Regex("""(?i)(?:file|path)[:：\s]+([A-Za-z0-9._/\-]+)"""),
                Regex("""(?:文件|路径)[:：\s]+([A-Za-z0-9._/\-]+)""")
            )
        return patterns.asSequence()
            .mapNotNull { it.find(prompt)?.groupValues?.getOrNull(1) }
            .map { it.trim().trim('"', '\'', '`') }
            .firstOrNull { candidate ->
                candidate.isNotBlank() && !candidate.contains(' ') && (candidate.contains('/') || candidate.contains('.'))
            }
    }

    private fun extractEmbeddedFileContent(prompt: String): String? {
        return Regex("""(?s)```(?:[A-Za-z0-9_+\-.#]+)?\r?\n(.*?)\r?\n```""")
            .find(prompt)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim('\r', '\n')
            ?.takeIf { it.isNotBlank() }
    }

    private fun extractReleaseTag(prompt: String): String? {
        val patterns =
            listOf(
                Regex("""(?i)(?:tag|release)\s*[:：\s]+(v?[A-Za-z0-9._\-]+)"""),
                Regex("""(?:标签|版本号|发布标签)[:：\s]+(v?[A-Za-z0-9._\-]+)""")
            )
        return patterns.asSequence()
            .mapNotNull { it.find(prompt)?.groupValues?.getOrNull(1) }
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
    }

    private fun extractReleaseName(prompt: String): String? {
        val patterns =
            listOf(
                Regex("""(?i)(?:release name|release title)[:：\s]+([^\n]+)"""),
                Regex("""(?:发布名称|发布标题)[:：\s]+([^\n]+)""")
            )
        return patterns.asSequence()
            .mapNotNull { it.find(prompt)?.groupValues?.getOrNull(1) }
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?.take(120)
    }

    private fun resolveWorkflowRunTarget(
        prompt: String,
        workflowRuns: List<ZiCodeGitHubWorkflowRun>
    ): ZiCodeGitHubWorkflowRun? {
        val lowerPrompt = prompt.lowercase()
        return workflowRuns.firstOrNull { workflowRun ->
            workflowRun.name.lowercase() in lowerPrompt ||
                workflowRun.branch?.lowercase()?.let { it in lowerPrompt } == true
        } ?: workflowRuns.firstOrNull()
    }

    private fun buildAutoBranchName(): String {
        return "zicode/${autoTagFormatter.format(System.currentTimeMillis())}"
    }

    private fun buildAutoReleaseTag(): String {
        return "zicode-${autoTagFormatter.format(System.currentTimeMillis())}"
    }

    private fun buildWriteCommitMessage(
        path: String,
        existed: Boolean,
        useChinese: Boolean
    ): String {
        return if (existed) {
            tr(useChinese, "通过 ZiCode 更新 $path", "Update $path via ZiCode")
        } else {
            tr(useChinese, "通过 ZiCode 创建 $path", "Create $path via ZiCode")
        }
    }

    private fun buildDeleteCommitMessage(
        path: String,
        useChinese: Boolean
    ): String {
        return tr(useChinese, "通过 ZiCode 删除 $path", "Delete $path via ZiCode")
    }

    private suspend fun generateFileContent(
        resolvedModel: ResolvedZiCodeModel,
        repo: ZiCodeRemoteRepo,
        prompt: String,
        path: String,
        existingPreview: ZiCodeFilePreview?,
        readmePreview: ZiCodeFilePreview?,
        useChinese: Boolean
    ): Result<String> {
        return runCatching {
            val result =
                chatApiClient.chatCompletions(
                    provider = resolvedModel.provider,
                    modelId = resolvedModel.remoteModelId,
                    messages = listOf(
                        Message(
                            role = "system",
                            content = if (useChinese) {
                                "你是 ZiCode。请只输出目标文件的最终内容，不要解释，不要 Markdown 代码块，不要额外前后缀。"
                            } else {
                                "You are ZiCode. Return only the final file contents. No explanation, no Markdown fences, and no extra wrapper text."
                            }
                        ),
                        Message(
                            role = "user",
                            content =
                                buildString {
                                    appendLine("${tr(useChinese, "仓库", "Repository")}: ${repo.fullName}")
                                    appendLine("${tr(useChinese, "目标文件", "Target file")}: $path")
                                    appendLine()
                                    appendLine("${tr(useChinese, "用户目标", "User goal")}:")
                                    appendLine(prompt)
                                    existingPreview?.content?.takeIf { it.isNotBlank() }?.let {
                                        appendLine()
                                        appendLine("${tr(useChinese, "当前文件内容", "Current file contents")}:")
                                        appendLine(it.take(2000))
                                    }
                                    readmePreview?.content?.takeIf { it.isNotBlank() }?.let {
                                        appendLine()
                                        appendLine("README:")
                                        appendLine(it.take(1200))
                                    }
                                }
                        )
                    ),
                    extraHeaders = resolvedModel.model.headers,
                    reasoningEffort = resolvedModel.model.reasoningEffort,
                    enableThinking = resolvedModel.enableThinking,
                    maxTokens = 2200
                ).getOrThrow()
            stripMarkdownCodeFences(result).trim()
        }
    }

    private fun stripMarkdownCodeFences(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return trimmed
        val lines = trimmed.lines()
        if (lines.size < 3) return trimmed.removePrefix("```").removeSuffix("```").trim()
        return lines.drop(1).dropLast(1).joinToString("\n").trim()
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
        issues: List<ZiCodeGitHubIssue>,
        pullRequests: List<ZiCodeGitHubPullRequest>,
        workflows: List<ZiCodeGitHubWorkflow>,
        workflowRuns: List<ZiCodeGitHubWorkflowRun>,
        workflowTrace: String,
        artifacts: List<ZiCodeGitHubArtifact>,
        releases: List<ZiCodeGitHubRelease>,
        pagesInfo: ZiCodeGitHubPagesInfo?,
        readmePreview: ZiCodeFilePreview?,
        latestFileCommit: ZiCodeGitHubCommit?,
        createdBranch: ZiCodeGitHubBranch?,
        createdIssue: ZiCodeGitHubIssue?,
        createdPullRequest: ZiCodeGitHubPullRequest?,
        createdRelease: ZiCodeGitHubRelease?,
        dispatchedWorkflowName: String?,
        useChinese: Boolean
    ): String {
        fun t(zh: String, en: String): String = tr(useChinese, zh, en)
        return buildString {
            appendLine(t("用户目标：", "User goal:"))
            appendLine(prompt)
            appendLine()
            appendLine("${t("仓库", "Repository")}: ${repo.fullName}")
            appendLine("${t("可见性", "Visibility")}: ${if (repo.privateRepo) t("私有", "private") else t("公开", "public")}")
            appendLine("${t("默认分支", "Default branch")}: ${repo.defaultBranch}")
            appendLine("${t("最近更新", "Updated at")}: ${formatTime(repo.updatedAt, useChinese)}")
            appendLine()
            appendLine(t("根目录结构：", "Root tree:"))
            if (rootEntries.isEmpty()) appendLine("- ${t("没有条目。", "No entries.")}") else rootEntries.take(18).forEach { appendLine("- ${it.path} [${it.type}]") }
            appendLine()
            appendLine(t("分支：", "Branches:"))
            if (branches.isEmpty()) appendLine("- ${t("没有分支数据。", "No branch data.")}") else branches.take(12).forEach { appendLine("- ${it.name}${if (it.protectedBranch) " (${t("protected", "protected")})" else ""}") }
            createdBranch?.let { appendLine("- ${t("本轮新建分支", "Created branch in this run")}: ${it.name}") }
            appendLine()
            appendLine(t("最近提交：", "Recent commits:"))
            if (commits.isEmpty()) appendLine("- ${t("没有提交数据。", "No commits.")}") else commits.take(8).forEach { appendLine("- ${it.sha.take(7)} · ${it.message}") }
            latestFileCommit?.let { appendLine("- ${t("本轮最新提交", "Latest commit in this run")}: ${it.sha.take(7)} · ${it.message}") }
            appendLine()
            appendLine(t("Issues：", "Issues:"))
            if (issues.isEmpty()) appendLine("- ${t("没有可见 Issue。", "No visible issues.")}") else issues.take(8).forEach { appendLine("- #${it.number} · ${it.title} · ${it.state}") }
            createdIssue?.let { appendLine("- ${t("本轮新建 Issue", "Created issue in this run")}: #${it.number} · ${it.title}") }
            appendLine()
            appendLine(t("Pull Requests：", "Pull requests:"))
            if (pullRequests.isEmpty()) appendLine("- ${t("没有可见 PR。", "No visible pull requests.")}") else pullRequests.take(8).forEach { appendLine("- #${it.number} · ${it.title} · ${it.state}${if (it.draft) " · draft" else ""}") }
            createdPullRequest?.let { appendLine("- ${t("本轮新建 PR", "Created pull request in this run")}: #${it.number} · ${it.title}") }
            appendLine()
            appendLine(t("工作流：", "Workflows:"))
            if (workflows.isEmpty()) appendLine("- ${t("没有可见工作流。", "No visible workflows.")}") else workflows.forEach { appendLine("- ${it.name} · ${it.path}") }
            dispatchedWorkflowName?.let { appendLine("- ${t("本轮已触发", "Dispatched in this run")}: $it") }
            appendLine()
            appendLine(t("工作流运行：", "Workflow runs:"))
            if (workflowRuns.isEmpty()) appendLine("- ${t("没有运行记录。", "No runs.")}") else workflowRuns.take(8).forEach { appendLine("- ${it.name} · ${it.status}${it.conclusion?.let { c -> " · $c" } ?: ""}") }
            if (workflowTrace.isNotBlank()) {
                appendLine()
                appendLine(t("最新运行轨迹：", "Latest workflow trace:"))
                appendLine(workflowTrace.take(1600))
            }
            appendLine()
            appendLine(t("构建产物：", "Artifacts:"))
            if (artifacts.isEmpty()) appendLine("- ${t("没有可见产物。", "No visible artifacts.")}") else artifacts.take(8).forEach { appendLine("- ${it.name} · ${it.sizeInBytes} bytes${if (it.expired) " · expired" else ""}") }
            appendLine()
            appendLine(t("发布：", "Releases:"))
            if (releases.isEmpty()) appendLine("- ${t("没有 Release。", "No releases.")}") else releases.take(6).forEach { appendLine("- ${it.tagName}${it.name.takeIf { name -> name.isNotBlank() }?.let { name -> " · $name" } ?: ""}") }
            createdRelease?.let { appendLine("- ${t("本轮新建 Release", "Created release in this run")}: ${it.tagName}") }
            appendLine()
            appendLine(t("Pages：", "Pages:"))
            if (pagesInfo == null) appendLine("- ${t("没有 Pages 数据。", "No pages data.")}") else {
                appendLine("- ${t("状态", "Status")}: ${pagesInfo.status}")
                pagesInfo.htmlUrl?.let { appendLine("- ${t("地址", "URL")}: $it") }
                pagesInfo.sourceBranch?.let { appendLine("- ${t("来源分支", "Source branch")}: $it") }
                pagesInfo.sourcePath?.let { appendLine("- ${t("来源路径", "Source path")}: $it") }
            }
            readmePreview?.content?.takeIf { it.isNotBlank() }?.let {
                appendLine()
                appendLine(t("README 摘要：", "README excerpt:"))
                appendLine(it.take(1800))
            }
            appendLine()
            appendLine(t("请根据以上上下文说明 ZiCode 已执行了什么、当前 GitHub 状态意味着什么，以及下一步最合适的动作。", "Respond with what ZiCode already executed, what the current GitHub state means, and the best next actions."))
        }
    }

    private fun buildFallbackSummary(
        repo: ZiCodeRemoteRepo,
        prompt: String,
        rootEntries: List<ZiCodeRepoNode>,
        branches: List<ZiCodeGitHubBranch>,
        commits: List<ZiCodeGitHubCommit>,
        issues: List<ZiCodeGitHubIssue>,
        pullRequests: List<ZiCodeGitHubPullRequest>,
        workflows: List<ZiCodeGitHubWorkflow>,
        workflowRuns: List<ZiCodeGitHubWorkflowRun>,
        workflowTrace: String,
        artifacts: List<ZiCodeGitHubArtifact>,
        releases: List<ZiCodeGitHubRelease>,
        pagesInfo: ZiCodeGitHubPagesInfo?,
        readmePreview: ZiCodeFilePreview?,
        latestFileCommit: ZiCodeGitHubCommit?,
        createdBranch: ZiCodeGitHubBranch?,
        createdIssue: ZiCodeGitHubIssue?,
        createdPullRequest: ZiCodeGitHubPullRequest?,
        createdRelease: ZiCodeGitHubRelease?,
        dispatchedWorkflowName: String?,
        useChinese: Boolean
    ): String {
        fun t(zh: String, en: String): String = tr(useChinese, zh, en)
        val listSeparator = if (useChinese) "、" else ", "
        val commitSeparator = if (useChinese) "；" else "; "
        val folders = rootEntries.filter { it.type == "dir" }.take(6).joinToString(listSeparator) { it.name }
        val files = rootEntries.filter { it.type == "file" }.take(6).joinToString(listSeparator) { it.name }
        return buildString {
            appendLine(t("我已经围绕“$prompt”完成了对 `${repo.fullName}` 的 ZiCode 首轮执行。", "I completed a first ZiCode pass on `${repo.fullName}` for \"$prompt\"."))
            appendLine()
            appendLine(t("这轮实际执行：", "Executed in this turn:"))
            appendLine("- ${t("校验了 GitHub Token 与 ZiCode 默认模型。", "Validated the GitHub token and the ZiCode default model.")}")
            appendLine("- ${t("读取了仓库、根目录、分支、提交、Issues、PR、工作流、运行记录、构建产物、Release 和 Pages 状态。", "Read repository metadata, root contents, branches, commits, issues, pull requests, workflows, workflow runs, artifacts, releases, and Pages status.")}")
            createdBranch?.let { appendLine("- ${t("已创建分支", "Created branch")}: `${it.name}`${if (useChinese) "。" else "."}") }
            createdIssue?.let { appendLine("- ${t("已创建 Issue", "Created issue")}: `#${it.number} ${it.title}`${if (useChinese) "。" else "."}") }
            createdPullRequest?.let { appendLine("- ${t("已创建 PR", "Created pull request")}: `#${it.number} ${it.title}`${if (useChinese) "。" else "."}") }
            createdRelease?.let { appendLine("- ${t("已创建 Release", "Created release")}: `${it.tagName}`${if (useChinese) "。" else "."}") }
            dispatchedWorkflowName?.let { appendLine("- ${t("已触发工作流", "Dispatched workflow")}: `$it`${if (useChinese) "。" else "."}") }
            appendLine()
            appendLine(t("仓库现状：", "Current repository state:"))
            appendLine(
                if (useChinese) {
                    "- 默认分支：`${repo.defaultBranch}`，最近更新于 ${formatTime(repo.updatedAt, true)}。"
                } else {
                    "- Default branch: `${repo.defaultBranch}`, updated at ${formatTime(repo.updatedAt, false)}."
                }
            )
            appendLine("- ${t("顶层目录", "Top-level folders")}: ${folders.ifBlank { t("暂未识别到明显目录。", "No obvious folders were identified.") }}")
            appendLine("- ${t("顶层文件", "Top-level files")}: ${files.ifBlank { t("暂未识别到明显文件。", "No obvious files were identified.") }}")
            if (branches.isNotEmpty()) appendLine("- ${t("当前分支", "Branches")}: ${branches.take(6).joinToString(listSeparator) { it.name }}")
            if (commits.isNotEmpty()) appendLine("- ${t("最近提交", "Recent commits")}: ${commits.take(3).joinToString(commitSeparator) { "${it.sha.take(7)} ${it.message}" }}")
            latestFileCommit?.let { appendLine("- ${t("本轮提交", "Latest commit in this run")}: ${it.sha.take(7)} ${it.message}") }
            if (issues.isNotEmpty()) appendLine("- ${t("最近 Issues", "Recent issues")}: ${issues.take(3).joinToString(listSeparator) { "#${it.number} ${it.title}" }}")
            if (pullRequests.isNotEmpty()) appendLine("- ${t("当前 PR", "Current pull requests")}: ${pullRequests.take(3).joinToString(listSeparator) { "#${it.number} ${it.title}" }}")
            if (workflows.isNotEmpty()) appendLine("- ${t("工作流", "Workflows")}: ${workflows.take(4).joinToString(listSeparator) { it.name }}")
            if (workflowRuns.isNotEmpty()) appendLine("- ${t("最新运行", "Latest run")}: ${workflowRuns.first().name} · ${workflowRuns.first().status}${workflowRuns.first().conclusion?.let { c -> " · $c" } ?: ""}")
            if (artifacts.isNotEmpty()) appendLine("- ${t("最近产物", "Recent artifacts")}: ${artifacts.take(3).joinToString(listSeparator) { it.name }}")
            if (releases.isNotEmpty()) appendLine("- ${t("最新发布", "Latest release")}: ${releases.first().tagName}")
            pagesInfo?.let { appendLine("- ${t("Pages", "Pages")}: ${if (it.status == "not_configured") t("未配置", "Not configured") else it.status}") }
            readmePreview?.content?.replace(Regex("\\s+"), " ")?.take(160)?.takeIf { it.isNotBlank() }?.let { appendLine("- README ${t("摘要", "excerpt")}: $it") }
            if (workflowTrace.isNotBlank()) {
                appendLine()
                appendLine(t("运行细节：", "Workflow trace:"))
                appendLine(workflowTrace.take(420))
            }
            appendLine()
            appendLine(t("下一步建议：", "Suggested next actions:"))
            appendLine("- ${t("如果你要继续改代码，我下一轮可以直接锁定具体目录和文件。", "If you want to continue editing code, ZiCode can now lock onto specific directories and files next.")}")
            appendLine("- ${t("如果你要继续 CI / 发布，我会优先围绕工作流、产物和 Release 入口继续执行。", "If you want to continue CI or shipping, ZiCode should continue from workflows, artifacts, and releases.")}")
            appendLine("- ${t("如果你要开始交付任务，我已经具备继续读取文件树、分支、Issue、PR 和运行记录的上下文。", "If you want to execute delivery work now, ZiCode already has context for files, branches, issues, pull requests, and workflow runs.")}")
        }.trim()
    }

    private suspend fun prefersChinese(): Boolean {
        val configured = appRepository.appLanguageFlow.first().trim().lowercase(Locale.ROOT)
        return when (configured) {
            "zh" -> true
            "en" -> false
            else -> Locale.getDefault().language.lowercase(Locale.ROOT).startsWith("zh")
        }
    }

    private fun formatTime(timestamp: Long, useChinese: Boolean): String {
        if (timestamp <= 0L) return tr(useChinese, "未知时间", "Unknown time")
        return formatter.format(timestamp)
    }

    private fun tr(useChinese: Boolean, zh: String, en: String): String {
        return if (useChinese) zh else en
    }

    private fun JsonObject.stringOrNull(key: String): String? {
        val element = get(key) ?: return null
        if (element.isJsonNull) return null
        return runCatching { element.asString.trim().takeIf { it.isNotBlank() } }.getOrNull()
    }

    private fun JsonObject.booleanOrFalse(key: String): Boolean {
        val element = get(key) ?: return false
        if (element.isJsonNull) return false
        return runCatching { element.asBoolean }.getOrDefault(false)
    }

    private data class ResolvedZiCodeModel(
        val provider: ProviderConfig,
        val model: ModelConfig,
        val remoteModelId: String,
        val enableThinking: Boolean
    )

    private data class ZiCodeExecutionPlan(
        val branchName: String? = null,
        val createIssue: Boolean = false,
        val issueTitle: String? = null,
        val createPullRequest: Boolean = false,
        val pullRequestTitle: String? = null,
        val writeFilePath: String? = null,
        val writeFileContent: String? = null,
        val deleteFilePath: String? = null,
        val dispatchWorkflow: Boolean = false,
        val workflowName: String? = null,
        val rerunWorkflow: Boolean = false,
        val cancelWorkflow: Boolean = false,
        val targetRunName: String? = null,
        val createRelease: Boolean = false,
        val releaseTag: String? = null,
        val releaseName: String? = null
    ) {
        fun hasActions(): Boolean {
            return !branchName.isNullOrBlank() ||
                createIssue ||
                createPullRequest ||
                !writeFilePath.isNullOrBlank() ||
                !deleteFilePath.isNullOrBlank() ||
                dispatchWorkflow ||
                rerunWorkflow ||
                cancelWorkflow ||
                createRelease
        }
    }

    companion object {
        private val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
        private val autoTagFormatter = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
    }
}
