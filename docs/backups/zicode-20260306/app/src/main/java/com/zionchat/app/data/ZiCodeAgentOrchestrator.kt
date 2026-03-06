package com.zionchat.app.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

data class ZiCodePlannedToolCall(
    val toolName: String,
    val argsJson: String
)

data class ZiCodeAgentTask(
    val taskId: String,
    val sessionId: String,
    val workspace: ZiCodeWorkspace,
    val plannedCalls: List<ZiCodePlannedToolCall>,
    val workflowFile: String? = null
)

data class ZiCodeAgentRunSummary(
    val success: Boolean,
    val message: String,
    val totalCalls: Int,
    val failedCall: String? = null,
    val latestRunId: Long? = null
)

private data class RunCompletionState(
    val success: Boolean,
    val status: String,
    val conclusion: String,
    val message: String,
    val runJson: JsonObject? = null
)

class ZiCodeAgentOrchestrator(
    private val repository: AppRepository,
    private val toolDispatcher: ZiCodeToolDispatcher,
    private val policyService: ZiCodePolicyService,
    private val client: OkHttpClient = OkHttpClient(),
    private val gson: Gson = Gson()
) {

    suspend fun executeTask(
        task: ZiCodeAgentTask,
        settings: ZiCodeSettings,
        autoPatchProvider: (suspend (ZiCodeReport) -> String?)? = null
    ): ZiCodeAgentRunSummary {
        if (task.plannedCalls.isEmpty()) {
            return ZiCodeAgentRunSummary(success = true, message = "无工具调用，任务已完成", totalCalls = 0)
        }

        val branchName = "ai/${task.taskId.trim().ifBlank { task.sessionId.take(8) }}"
        val ensureBranchError = ensureAiBranch(task.sessionId, task.workspace, settings, branchName)
        if (ensureBranchError != null) {
            return ZiCodeAgentRunSummary(
                success = false,
                message = ensureBranchError,
                totalCalls = 0,
                failedCall = "repo.create_branch",
                latestRunId = null
            )
        }

        var latestRunId: Long? = null
        var callCount = 0
        val workflowTriggered = task.plannedCalls.any { it.toolName == "actions.trigger_workflow" }

        for (planned in task.plannedCalls) {
            if (policyService.isLocalShellTool(planned.toolName)) {
                return ZiCodeAgentRunSummary(
                    success = false,
                    message = "策略阻止了本地 shell 工具调用：${planned.toolName}",
                    totalCalls = callCount,
                    failedCall = planned.toolName,
                    latestRunId = latestRunId
                )
            }

            val result =
                toolDispatcher.dispatch(
                    sessionId = task.sessionId,
                    workspace = task.workspace,
                    settings = settings,
                    toolName = planned.toolName,
                    argsJson = planned.argsJson
                )
            callCount++

            if (!result.success) {
                return ZiCodeAgentRunSummary(
                    success = false,
                    message = result.error ?: "工具调用失败",
                    totalCalls = callCount,
                    failedCall = planned.toolName,
                    latestRunId = latestRunId
                )
            }

            if (planned.toolName == "actions.get_run") {
                val runObj = parseJsonObject(result.resultJson)
                latestRunId = parseRunIdFromResult(result.resultJson) ?: latestRunId
                latestRunId?.let { runId ->
                    val workflow = task.workflowFile.orEmpty().ifBlank { "workflow" }
                    persistRunRecord(task.sessionId, workflow, runId, runObj)
                }
            }
        }

        if (workflowTriggered && latestRunId == null) {
            latestRunId =
                waitForLatestRunId(
                    workspace = task.workspace,
                    pat = settings.pat,
                    workflowFile = task.workflowFile,
                    branch = currentSessionBranch(task.sessionId) ?: task.workspace.defaultBranch,
                    attempts = 12,
                    delayMs = 2000L
                )
        }

        if (latestRunId != null && task.workflowFile != null) {
            val healResult =
                runSelfHealLoop(
                    sessionId = task.sessionId,
                    workspace = task.workspace,
                    settings = settings,
                    workflowFile = task.workflowFile,
                    initialRunId = latestRunId,
                    autoPatchProvider = autoPatchProvider
                )
            if (!healResult.success) {
                return healResult.copy(totalCalls = callCount + healResult.totalCalls)
            }
            latestRunId = healResult.latestRunId ?: latestRunId
        }

        if (workflowTriggered && task.workflowFile != null && latestRunId == null) {
            return ZiCodeAgentRunSummary(
                success = false,
                message = "工作流已触发，但未能获取 run_id",
                totalCalls = callCount,
                failedCall = "actions.get_run",
                latestRunId = null
            )
        }

        return ZiCodeAgentRunSummary(
            success = true,
            message = "任务执行完成",
            totalCalls = callCount,
            latestRunId = latestRunId
        )
    }

    suspend fun getToolspec(): JsonObject {
        return policyService.getToolspec()
    }

    suspend fun checkRisk(patchText: String, touchedPaths: List<String>): ZiCodeRiskReport {
        return policyService.checkRisk(patchText, touchedPaths)
    }

    private suspend fun ensureAiBranch(
        sessionId: String,
        workspace: ZiCodeWorkspace,
        settings: ZiCodeSettings,
        branchName: String
    ): String? {
        val session = repository.zicodeSessionsFlow.first().firstOrNull { it.id == sessionId }
            ?: return "会话不存在，无法创建分支"
        if (session.branchName?.isNotBlank() == true) return null

        val createBranchResult =
            toolDispatcher.dispatch(
                sessionId = sessionId,
                workspace = workspace,
                settings = settings,
                toolName = "repo.create_branch",
                argsJson = gson.toJson(
                    JsonObject().apply {
                        addProperty("branch", branchName)
                        addProperty("base", workspace.defaultBranch)
                    }
                )
            )
        if (!createBranchResult.success) {
            return createBranchResult.error ?: "创建 ai 分支失败"
        }

        repository.upsertZiCodeSession(
            session.copy(
                branchName = branchName,
                updatedAt = System.currentTimeMillis()
            )
        )
        return null
    }

    private suspend fun runSelfHealLoop(
        sessionId: String,
        workspace: ZiCodeWorkspace,
        settings: ZiCodeSettings,
        workflowFile: String,
        initialRunId: Long,
        autoPatchProvider: (suspend (ZiCodeReport) -> String?)?
    ): ZiCodeAgentRunSummary {
        var runId = initialRunId
        val maxLoop = settings.maxSelfHealLoops.coerceIn(1, 10)
        var stepCount = 0

        repeat(maxLoop) { index ->
            val completion =
                waitForRunCompletion(
                    sessionId = sessionId,
                    workspace = workspace,
                    settings = settings,
                    workflowFile = workflowFile,
                    runId = runId
                )
            stepCount++

            if (!completion.success) {
                return ZiCodeAgentRunSummary(
                    success = false,
                    message = completion.message,
                    totalCalls = stepCount,
                    failedCall = "actions.get_run",
                    latestRunId = runId
                )
            }

            if (completion.conclusion == "success") {
                return ZiCodeAgentRunSummary(
                    success = true,
                    message = "工作流执行成功",
                    totalCalls = stepCount,
                    latestRunId = runId
                )
            }

            val summaryResult =
                toolDispatcher.dispatch(
                    sessionId = sessionId,
                    workspace = workspace,
                    settings = settings,
                    toolName = "actions.get_logs_summary",
                    argsJson = gson.toJson(JsonObject().apply { addProperty("run_id", runId) })
                )
            stepCount++
            if (!summaryResult.success) {
                return ZiCodeAgentRunSummary(
                    success = false,
                    message = summaryResult.error ?: "读取日志摘要失败",
                    totalCalls = stepCount,
                    failedCall = "actions.get_logs_summary",
                    latestRunId = runId
                )
            }

            val report = parseReportFromSummary(summaryResult.resultJson)
            val patchText = autoPatchProvider?.invoke(report).orEmpty()
            if (patchText.isBlank()) {
                return ZiCodeAgentRunSummary(
                    success = false,
                    message = report.errorSummary ?: "工作流失败且未提供自动修复补丁",
                    totalCalls = stepCount,
                    failedCall = "actions.get_logs_summary",
                    latestRunId = runId
                )
            }

            val riskResult =
                toolDispatcher.dispatch(
                    sessionId = sessionId,
                    workspace = workspace,
                    settings = settings,
                    toolName = "policy.check_risk",
                    argsJson = gson.toJson(
                        JsonObject().apply {
                            addProperty("patch", patchText)
                            add("touched_paths", gson.toJsonTree(report.fileHints))
                        }
                    )
                )
            stepCount++
            if (!riskResult.success) {
                return ZiCodeAgentRunSummary(
                    success = false,
                    message = riskResult.error ?: "风险评估失败",
                    totalCalls = stepCount,
                    failedCall = "policy.check_risk",
                    latestRunId = runId
                )
            }
            val risk = parseRisk(riskResult.resultJson)
            if (risk.level == "high") {
                return ZiCodeAgentRunSummary(
                    success = false,
                    message = "自动修复补丁被策略拒绝（高风险）：${risk.reasons.joinToString("；")}",
                    totalCalls = stepCount,
                    failedCall = "policy.check_risk",
                    latestRunId = runId
                )
            }

            val applyResult =
                toolDispatcher.dispatch(
                    sessionId = sessionId,
                    workspace = workspace,
                    settings = settings,
                    toolName = "repo.apply_patch",
                    argsJson = gson.toJson(JsonObject().apply { addProperty("patch", patchText) })
                )
            stepCount++
            if (!applyResult.success) {
                return ZiCodeAgentRunSummary(
                    success = false,
                    message = applyResult.error ?: "自动修复补丁应用失败",
                    totalCalls = stepCount,
                    failedCall = "repo.apply_patch",
                    latestRunId = runId
                )
            }

            val commitResult =
                toolDispatcher.dispatch(
                    sessionId = sessionId,
                    workspace = workspace,
                    settings = settings,
                    toolName = "repo.commit_push",
                    argsJson = gson.toJson(
                        JsonObject().apply {
                            addProperty("message", "ZiCode auto-fix attempt ${index + 1}")
                        }
                    )
                )
            stepCount++
            if (!commitResult.success) {
                return ZiCodeAgentRunSummary(
                    success = false,
                    message = commitResult.error ?: "自动修复提交失败",
                    totalCalls = stepCount,
                    failedCall = "repo.commit_push",
                    latestRunId = runId
                )
            }

            val refBranch = currentSessionBranch(sessionId) ?: workspace.defaultBranch
            val triggerResult =
                toolDispatcher.dispatch(
                    sessionId = sessionId,
                    workspace = workspace,
                    settings = settings,
                    toolName = "actions.trigger_workflow",
                    argsJson = gson.toJson(
                        JsonObject().apply {
                            addProperty("workflow", workflowFile)
                            addProperty("ref", refBranch)
                        }
                    )
                )
            stepCount++
            if (!triggerResult.success) {
                return ZiCodeAgentRunSummary(
                    success = false,
                    message = triggerResult.error ?: "重触发工作流失败",
                    totalCalls = stepCount,
                    failedCall = "actions.trigger_workflow",
                    latestRunId = runId
                )
            }

            runId =
                waitForLatestRunId(
                    workspace = workspace,
                    pat = settings.pat,
                    workflowFile = workflowFile,
                    branch = refBranch,
                    attempts = 20,
                    delayMs = 2500L
                )
                    ?: return ZiCodeAgentRunSummary(
                        success = false,
                        message = "已触发新工作流，但未获取到新的 run_id",
                        totalCalls = stepCount,
                        failedCall = "actions.get_run",
                        latestRunId = runId
                    )
        }

        return ZiCodeAgentRunSummary(
            success = false,
            message = "达到最大自愈循环次数（$maxLoop）",
            totalCalls = stepCount,
            failedCall = "actions.get_run",
            latestRunId = runId
        )
    }

    private suspend fun waitForRunCompletion(
        sessionId: String,
        workspace: ZiCodeWorkspace,
        settings: ZiCodeSettings,
        workflowFile: String,
        runId: Long,
        pollAttempts: Int = 240,
        pollDelayMs: Long = 5000L
    ): RunCompletionState {
        repeat(pollAttempts) {
            val runResult =
                toolDispatcher.dispatch(
                    sessionId = sessionId,
                    workspace = workspace,
                    settings = settings,
                    toolName = "actions.get_run",
                    argsJson = gson.toJson(JsonObject().apply { addProperty("run_id", runId) })
                )
            if (!runResult.success) {
                return RunCompletionState(
                    success = false,
                    status = "error",
                    conclusion = "",
                    message = runResult.error ?: "查询 run 失败",
                    runJson = null
                )
            }

            val runObj = parseJsonObject(runResult.resultJson)
            persistRunRecord(sessionId, workflowFile, runId, runObj)

            val status = runObj.get("status")?.asString.orEmpty()
            val conclusion = runObj.get("conclusion")?.asString.orEmpty()
            if (status == "completed") {
                return RunCompletionState(
                    success = true,
                    status = status,
                    conclusion = conclusion,
                    message = if (conclusion.isNotBlank()) conclusion else "completed",
                    runJson = runObj
                )
            }
            delay(pollDelayMs)
        }
        return RunCompletionState(
            success = false,
            status = "timeout",
            conclusion = "",
            message = "等待工作流完成超时",
            runJson = null
        )
    }

    private suspend fun persistRunRecord(
        sessionId: String,
        workflowFile: String,
        runId: Long,
        runObj: JsonObject
    ) {
        val status = runObj.get("status")?.asString.orEmpty().ifBlank { "unknown" }
        val conclusion = runObj.get("conclusion")?.asString.orEmpty()
        val summary = if (conclusion.isBlank()) status else "$status/$conclusion"
        repository.upsertZiCodeRun(
            ZiCodeRunRecord(
                sessionId = sessionId,
                workflow = workflowFile,
                runId = runId,
                status = if (conclusion.isBlank()) status else conclusion,
                summary = summary,
                runUrl = runObj.get("html_url")?.asString,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun currentSessionBranch(sessionId: String): String? {
        return repository.zicodeSessionsFlow.first().firstOrNull { it.id == sessionId }?.branchName?.trim()?.takeIf { it.isNotBlank() }
    }

    private suspend fun waitForLatestRunId(
        workspace: ZiCodeWorkspace,
        pat: String,
        workflowFile: String?,
        branch: String?,
        attempts: Int,
        delayMs: Long
    ): Long? {
        repeat(attempts.coerceAtLeast(1)) {
            val runId = fetchLatestRunId(workspace, pat, workflowFile, branch)
            if (runId != null) return runId
            delay(delayMs)
        }
        return null
    }

    private suspend fun fetchLatestRunId(
        workspace: ZiCodeWorkspace,
        pat: String,
        workflowFile: String? = null,
        branch: String? = null
    ): Long? {
        val token = pat.trim().ifBlank { return null }
        val workflowSegment = workflowFile?.trim()?.takeIf { it.isNotBlank() }?.let { "/workflows/${urlEncode(it)}" }.orEmpty()
        val branchQuery = branch?.trim()?.takeIf { it.isNotBlank() }?.let { "&branch=${urlEncode(it)}" }.orEmpty()
        val url =
            "https://api.github.com/repos/${workspace.owner}/${workspace.repo}/actions$workflowSegment/runs?per_page=1$branchQuery"
        return withContext(Dispatchers.IO) {
            runCatching {
                val request =
                    Request.Builder()
                        .url(url)
                        .get()
                        .addHeader("Authorization", "Bearer $token")
                        .addHeader("Accept", "application/vnd.github+json")
                        .addHeader("X-GitHub-Api-Version", "2022-11-28")
                        .addHeader("User-Agent", "ZionChat-ZiCode")
                        .build()
                client.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) return@use null
                    val obj = gson.fromJson(raw, JsonObject::class.java) ?: return@use null
                    val runs = obj.getAsJsonArray("workflow_runs") ?: return@use null
                    runs.firstOrNull()?.asJsonObject?.get("id")?.asLong
                }
            }.getOrNull()
        }
    }

    private fun parseRunIdFromResult(raw: String?): Long? {
        val obj = parseJsonObject(raw)
        return obj.get("id")?.asLong ?: obj.get("run_id")?.asLong
    }

    private fun parseJsonObject(raw: String?): JsonObject {
        val text = raw?.trim().orEmpty().ifBlank { "{}" }
        return runCatching { gson.fromJson(text, JsonObject::class.java) }.getOrElse { JsonObject() }
    }

    private fun parseRisk(raw: String?): ZiCodeRiskReport {
        val obj = parseJsonObject(raw)
        val reasons =
            obj.getAsJsonArray("reasons")
                ?.mapNotNull { item -> runCatching { item.asString.trim() }.getOrNull()?.takeIf { it.isNotBlank() } }
                .orEmpty()
        return ZiCodeRiskReport(
            level = obj.get("level")?.asString.orEmpty().ifBlank { "low" },
            reasons = reasons.ifEmpty { listOf("未命中高风险规则") },
            changedFiles = obj.get("changedFiles")?.asInt ?: obj.get("changed_files")?.asInt ?: 0,
            changedLines = obj.get("changedLines")?.asInt ?: obj.get("changed_lines")?.asInt ?: 0
        )
    }

    private fun parseReportFromSummary(raw: String?): ZiCodeReport {
        val obj = parseJsonObject(raw)
        val reportObj = obj.getAsJsonObject("report")
        if (reportObj == null) {
            return ZiCodeReport(
                status = "error",
                summary = "未解析到 report",
                failingStep = null,
                errorSummary = "Missing report",
                fileHints = emptyList(),
                nextReads = emptyList(),
                artifacts = emptyList(),
                pagesUrl = null,
                deploymentStatus = null
            )
        }
        return runCatching {
            gson.fromJson(reportObj, ZiCodeReport::class.java)
        }.getOrElse {
            ZiCodeReport(
                status = "error",
                summary = "report 结构解析失败",
                failingStep = null,
                errorSummary = it.message,
                fileHints = emptyList(),
                nextReads = emptyList(),
                artifacts = emptyList(),
                pagesUrl = null,
                deploymentStatus = null
            )
        }
    }

    private fun urlEncode(value: String): String {
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }
}
