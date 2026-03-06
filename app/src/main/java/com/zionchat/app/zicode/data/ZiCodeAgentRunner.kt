package com.zionchat.app.zicode.data

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
    private val gitHubService: ZiCodeGitHubService
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
        val tools = mutableListOf(
            ZiCodeToolCallState(
                label = "理解目标",
                toolName = "agent.goal_analyze",
                status = ZiCodeToolStatus.RUNNING,
                summary = "正在拆解任务并选择 GitHub 能力。",
                detailLog = "收到任务：$normalizedPrompt"
            )
        )
        updateTurn(sessionId, turnId, tools = tools, status = ZiCodeRunStatus.RUNNING)
        delay(260)

        tools[0] =
            tools[0].copy(
                status = ZiCodeToolStatus.SUCCESS,
                summary = "已完成任务拆解，进入仓库检查阶段。",
                detailLog = buildString {
                    appendLine("任务：$normalizedPrompt")
                    appendLine("仓库：$repoOwner/$repoName")
                    appendLine("执行策略：先读取仓库元信息，再扫描目录与关键文件。")
                }.trim(),
                finishedAt = System.currentTimeMillis()
            )
        updateTurn(sessionId, turnId, tools = tools, status = ZiCodeRunStatus.RUNNING)

        val token = repository.settingsFlow.first().githubToken.trim()
        if (token.isBlank()) {
            failTurn(
                sessionId = sessionId,
                turnId = turnId,
                tools = tools + listOf(
                    ZiCodeToolCallState(
                        label = "检查 GitHub 连接",
                        toolName = "github.auth",
                        status = ZiCodeToolStatus.FAILED,
                        summary = "缺少 GitHub Token。",
                        detailLog = "请先在 ZiCode 设置页配置 GitHub Token，之后才能读取仓库和文件。",
                        finishedAt = System.currentTimeMillis()
                    )
                ),
                message = "当前还没有 GitHub Token。我已经保留了这次会话，但要继续执行仓库任务，需要先去 ZiCode 设置里填入 Token。"
            )
            return
        }

        val repoToolIndex =
            tools.addRunningTool(
                label = "读取仓库信息",
                toolName = "github.repo_get",
                summary = "正在拉取仓库元数据。"
            )
        updateTurn(sessionId, turnId, tools = tools, status = ZiCodeRunStatus.RUNNING)
        val repoResult = gitHubService.fetchRepo(token, repoOwner, repoName)
        val repo = repoResult.getOrNull()
        if (repo == null) {
            tools[repoToolIndex] =
                tools[repoToolIndex].copy(
                    status = ZiCodeToolStatus.FAILED,
                    summary = "仓库读取失败。",
                    detailLog = repoResult.exceptionOrNull()?.message.orEmpty().ifBlank { "未知错误" },
                    finishedAt = System.currentTimeMillis()
                )
            failTurn(
                sessionId = sessionId,
                turnId = turnId,
                tools = tools,
                message = "我没有拿到仓库 `$repoOwner/$repoName` 的信息。请确认 Token 对这个仓库有访问权限。"
            )
            return
        }
        tools[repoToolIndex] =
            tools[repoToolIndex].copy(
                status = ZiCodeToolStatus.SUCCESS,
                summary = "仓库信息已加载。",
                detailLog = buildString {
                    appendLine("仓库：${repo.fullName}")
                    appendLine("默认分支：${repo.defaultBranch}")
                    appendLine("可见性：${if (repo.privateRepo) "Private" else "Public"}")
                    repo.homepageUrl?.takeIf { it.isNotBlank() }?.let { appendLine("主页：$it") }
                    appendLine("最近更新：${formatTime(repo.updatedAt)}")
                }.trim(),
                finishedAt = System.currentTimeMillis()
            )
        updateTurn(sessionId, turnId, tools = tools, status = ZiCodeRunStatus.RUNNING)

        val rootToolIndex =
            tools.addRunningTool(
                label = "扫描根目录文件",
                toolName = "github.contents_list",
                summary = "正在查看仓库顶层结构。"
            )
        updateTurn(sessionId, turnId, tools = tools, status = ZiCodeRunStatus.RUNNING)
        val rootEntriesResult = gitHubService.listDirectory(token, repoOwner, repoName, "")
        val rootEntries = rootEntriesResult.getOrNull()
        if (rootEntries == null) {
            tools[rootToolIndex] =
                tools[rootToolIndex].copy(
                    status = ZiCodeToolStatus.FAILED,
                    summary = "根目录扫描失败。",
                    detailLog = rootEntriesResult.exceptionOrNull()?.message.orEmpty().ifBlank { "未知错误" },
                    finishedAt = System.currentTimeMillis()
                )
            failTurn(
                sessionId = sessionId,
                turnId = turnId,
                tools = tools,
                message = "我已经连接到仓库，但暂时没能读取根目录文件。请稍后重试一次。"
            )
            return
        }
        tools[rootToolIndex] =
            tools[rootToolIndex].copy(
                status = ZiCodeToolStatus.SUCCESS,
                summary = "已扫描 ${rootEntries.size} 个顶层条目。",
                detailLog = buildString {
                    appendLine("根目录共 ${rootEntries.size} 个条目。")
                    rootEntries.take(12).forEach { entry ->
                        append("- ")
                        append(entry.path)
                        append(" [")
                        append(entry.type)
                        appendLine("]")
                    }
                }.trim(),
                finishedAt = System.currentTimeMillis()
            )
        updateTurn(sessionId, turnId, tools = tools, status = ZiCodeRunStatus.RUNNING)

        val shouldInspectReadme =
            normalizedPrompt.contains("readme", ignoreCase = true) ||
                normalizedPrompt.contains("说明") ||
                normalizedPrompt.contains("结构") ||
                normalizedPrompt.contains("架构") ||
                normalizedPrompt.contains("项目")
        val readmeNode =
            rootEntries.firstOrNull { entry ->
                entry.type == "file" && entry.name.startsWith("README", ignoreCase = true)
            }
        var readmePreview: ZiCodeFilePreview? = null
        if (shouldInspectReadme && readmeNode != null) {
            val readmeToolIndex =
                tools.addRunningTool(
                    label = "读取 README",
                    toolName = "github.file_read",
                    summary = "正在提取项目说明。"
                )
            updateTurn(sessionId, turnId, tools = tools, status = ZiCodeRunStatus.RUNNING)
            val readmeResult = gitHubService.readFile(token, repoOwner, repoName, readmeNode.path)
            readmePreview = readmeResult.getOrNull()
            tools[readmeToolIndex] =
                if (readmePreview != null) {
                    tools[readmeToolIndex].copy(
                        status = ZiCodeToolStatus.SUCCESS,
                        summary = "README 已读取。",
                        detailLog = readmePreview.content.take(560),
                        finishedAt = System.currentTimeMillis()
                    )
                } else {
                    tools[readmeToolIndex].copy(
                        status = ZiCodeToolStatus.FAILED,
                        summary = "README 读取失败。",
                        detailLog = readmeResult.exceptionOrNull()?.message.orEmpty().ifBlank { "未知错误" },
                        finishedAt = System.currentTimeMillis()
                    )
                }
            updateTurn(sessionId, turnId, tools = tools, status = ZiCodeRunStatus.RUNNING)
        }

        val shouldInspectWorkflows =
            normalizedPrompt.contains("workflow", ignoreCase = true) ||
                normalizedPrompt.contains("action", ignoreCase = true) ||
                normalizedPrompt.contains("构建") ||
                normalizedPrompt.contains("发布") ||
                normalizedPrompt.contains("部署") ||
                normalizedPrompt.contains("ci", ignoreCase = true)
        var workflowEntries: List<ZiCodeRepoNode> = emptyList()
        if (shouldInspectWorkflows) {
            val workflowToolIndex =
                tools.addRunningTool(
                    label = "检查 GitHub Actions",
                    toolName = "github.workflow_list",
                    summary = "正在查找工作流文件。"
                )
            updateTurn(sessionId, turnId, tools = tools, status = ZiCodeRunStatus.RUNNING)
            val workflowResult = gitHubService.listDirectory(token, repoOwner, repoName, ".github/workflows")
            workflowEntries = workflowResult.getOrNull().orEmpty()
            tools[workflowToolIndex] =
                if (workflowResult.isSuccess) {
                    tools[workflowToolIndex].copy(
                        status = ZiCodeToolStatus.SUCCESS,
                        summary = "找到 ${workflowEntries.size} 个工作流文件。",
                        detailLog =
                            if (workflowEntries.isEmpty()) {
                                "仓库里暂时没有检测到 .github/workflows 目录内容。"
                            } else {
                                workflowEntries.joinToString(separator = "\n") { entry -> "- ${entry.path}" }
                            },
                        finishedAt = System.currentTimeMillis()
                    )
                } else {
                    tools[workflowToolIndex].copy(
                        status = ZiCodeToolStatus.FAILED,
                        summary = "工作流读取失败。",
                        detailLog = workflowResult.exceptionOrNull()?.message.orEmpty().ifBlank { "未知错误" },
                        finishedAt = System.currentTimeMillis()
                    )
                }
            updateTurn(sessionId, turnId, tools = tools, status = ZiCodeRunStatus.RUNNING)
        }

        val summaryToolIndex =
            tools.addRunningTool(
                label = "生成执行结论",
                toolName = "agent.summary_build",
                summary = "正在整理下一步建议。"
            )
        updateTurn(sessionId, turnId, tools = tools, status = ZiCodeRunStatus.RUNNING)
        delay(220)
        val response = buildAgentSummary(repo, rootEntries, workflowEntries, readmePreview, normalizedPrompt)
        tools[summaryToolIndex] =
            tools[summaryToolIndex].copy(
                status = ZiCodeToolStatus.SUCCESS,
                summary = "结果已经整理完成。",
                detailLog = response.take(600),
                finishedAt = System.currentTimeMillis()
            )

        repository.updateTurn(sessionId, turnId) { turn ->
            turn.copy(
                response = response,
                status = ZiCodeRunStatus.SUCCESS,
                toolCalls = tools,
                resultLink = repo.homepageUrl?.takeIf { it.isNotBlank() } ?: repo.htmlUrl,
                resultLabel = if (!repo.homepageUrl.isNullOrBlank()) "打开项目主页" else "打开 GitHub 仓库",
                failureMessage = null
            )
        }
        activeJobs.remove(turnId)
    }

    private suspend fun updateTurn(
        sessionId: String,
        turnId: String,
        tools: List<ZiCodeToolCallState>,
        status: ZiCodeRunStatus
    ) {
        repository.updateTurn(sessionId, turnId) { turn ->
            turn.copy(
                status = status,
                toolCalls = tools
            )
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
        activeJobs.remove(turnId)
    }

    private fun MutableList<ZiCodeToolCallState>.addRunningTool(
        label: String,
        toolName: String,
        summary: String
    ): Int {
        add(
            ZiCodeToolCallState(
                label = label,
                toolName = toolName,
                status = ZiCodeToolStatus.RUNNING,
                summary = summary
            )
        )
        return lastIndex
    }

    private fun buildAgentSummary(
        repo: ZiCodeRemoteRepo,
        rootEntries: List<ZiCodeRepoNode>,
        workflows: List<ZiCodeRepoNode>,
        readmePreview: ZiCodeFilePreview?,
        prompt: String
    ): String {
        val topDirs = rootEntries.filter { it.type == "dir" }.take(6).joinToString("、") { it.name }
        val topFiles = rootEntries.filter { it.type == "file" }.take(6).joinToString("、") { it.name }
        val readmeSnippet =
            readmePreview?.content
                ?.replace(Regex("\\s+"), " ")
                ?.take(180)
                ?.takeIf { it.isNotBlank() }
        val workflowSnippet =
            if (workflows.isEmpty()) {
                "当前没有检测到明显的 GitHub Actions 工作流文件。"
            } else {
                "当前仓库可见工作流：${workflows.take(5).joinToString("、") { it.name }}"
            }

        return buildString {
            appendLine("我已经围绕 `$prompt` 完成了对 `${repo.fullName}` 的首轮 Agent 检查。")
            appendLine()
            appendLine("仓库现状：")
            append("- 默认分支是 `${repo.defaultBranch}`，最近一次更新在 ")
            append(formatTime(repo.updatedAt))
            appendLine("。")
            append("- 顶层目录：")
            appendLine(topDirs.ifBlank { "暂无明显目录。" })
            append("- 顶层文件：")
            appendLine(topFiles.ifBlank { "暂无明显文件。" })
            appendLine()
            appendLine("执行判断：")
            appendLine("- 这个仓库已经具备作为 ZiCode 项目会话入口的基础结构，我现在可以继续沿着文件浏览、README 分析和工作流检查往下做。")
            append("- GitHub Actions：")
            appendLine(workflowSnippet)
            readmeSnippet?.let {
                appendLine("- README 摘要：$it")
            }
            repo.homepageUrl?.takeIf { it.isNotBlank() }?.let {
                appendLine("- 项目主页：$it")
            }
            appendLine()
            appendLine("下一步我建议：")
            appendLine("- 如果你要我继续拆任务，我可以直接围绕这个仓库做更细的文件级检查。")
            appendLine("- 如果你要做发布或构建，我下一轮会优先检查 `.github/workflows` 与部署入口。")
            appendLine("- 如果你要做代码交付，我会先锁定相关目录，再给出具体执行路径。")
        }.trim()
    }

    private fun formatTime(timestamp: Long): String {
        if (timestamp <= 0L) return "未知时间"
        return formatter.format(timestamp)
    }

    companion object {
        private val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
    }
}
