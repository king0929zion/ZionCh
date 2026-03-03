package com.zionchat.app.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

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
        ensureAiBranch(task.sessionId, task.workspace, settings, branchName)

        var latestRunId: Long? = null
        var callCount = 0

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
                latestRunId = parseRunIdFromResult(result.resultJson)
            }
        }

        if (latestRunId != null && task.workflowFile != null) {
            val healResult = runSelfHealLoop(
                sessionId = task.sessionId,
                workspace = task.workspace,
                settings = settings,
                workflowFile = task.workflowFile,
                initialRunId = latestRunId,
                autoPatchProvider = autoPatchProvider
            )
            if (!healResult.success) {
                return healResult
            }
            latestRunId = healResult.latestRunId ?: latestRunId
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
    ) {
        val session = repository.zicodeSessionsFlow.first().firstOrNull { it.id == sessionId }
        if (session?.branchName?.isNotBlank() == true) return

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

        session?.let {
            repository.upsertZiCodeSession(
                it.copy(
                    branchName = branchName,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
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

        repeat(maxLoop) { index ->
            val runResult =
                toolDispatcher.dispatch(
                    sessionId = sessionId,
                    workspace = workspace,
                    settings = settings,
                    toolName = "actions.get_run",
                    argsJson = gson.toJson(JsonObject().apply { addProperty("run_id", runId) })
                )
            if (!runResult.success) {
                return ZiCodeAgentRunSummary(
                    success = false,
                    message = runResult.error ?: "查询 run 失败",
                    totalCalls = index + 1,
                    failedCall = "actions.get_run",
                    latestRunId = runId
                )
            }

            val runObj = parseJsonObject(runResult.resultJson)
            val status = runObj.get("status")?.asString.orEmpty()
            val conclusion = runObj.get("conclusion")?.asString.orEmpty()

            if (status != "completed") {
                delay(2500)
                return@repeat
            }

            if (conclusion == "success") {
                return ZiCodeAgentRunSummary(
                    success = true,
                    message = "工作流执行成功",
                    totalCalls = index + 1,
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
            val report = parseReportFromSummary(summaryResult.resultJson)

            val patchText = autoPatchProvider?.invoke(report).orEmpty()
            if (patchText.isBlank()) {
                return ZiCodeAgentRunSummary(
                    success = false,
                    message = report.errorSummary ?: "工作流失败且未提供自动修复补丁",
                    totalCalls = index + 1,
                    failedCall = "actions.get_logs_summary",
                    latestRunId = runId
                )
            }

            val risk = policyService.checkRisk(patchText)
            if (risk.level == "high") {
                return ZiCodeAgentRunSummary(
                    success = false,
                    message = "自动修复补丁被策略拒绝（高风险）：${risk.reasons.joinToString("；")}",
                    totalCalls = index + 1,
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
            if (!applyResult.success) {
                return ZiCodeAgentRunSummary(
                    success = false,
                    message = applyResult.error ?: "自动修复补丁应用失败",
                    totalCalls = index + 1,
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
            if (!commitResult.success) {
                return ZiCodeAgentRunSummary(
                    success = false,
                    message = commitResult.error ?: "自动修复提交失败",
                    totalCalls = index + 1,
                    failedCall = "repo.commit_push",
                    latestRunId = runId
                )
            }

            val triggerResult =
                toolDispatcher.dispatch(
                    sessionId = sessionId,
                    workspace = workspace,
                    settings = settings,
                    toolName = "actions.trigger_workflow",
                    argsJson = gson.toJson(
                        JsonObject().apply {
                            addProperty("workflow", workflowFile)
                            addProperty("ref", workspace.defaultBranch)
                        }
                    )
                )
            if (!triggerResult.success) {
                return ZiCodeAgentRunSummary(
                    success = false,
                    message = triggerResult.error ?: "重触发工作流失败",
                    totalCalls = index + 1,
                    failedCall = "actions.trigger_workflow",
                    latestRunId = runId
                )
            }

            runId = fetchLatestRunId(workspace, settings.pat)
                ?: return ZiCodeAgentRunSummary(
                    success = false,
                    message = "已触发新工作流，但未获取到新的 run_id",
                    totalCalls = index + 1,
                    failedCall = "actions.get_run",
                    latestRunId = runId
                )
            delay(2500)
        }

        return ZiCodeAgentRunSummary(
            success = false,
            message = "达到最大自愈循环次数（$maxLoop）",
            totalCalls = maxLoop,
            failedCall = "actions.get_run",
            latestRunId = runId
        )
    }

    private suspend fun fetchLatestRunId(workspace: ZiCodeWorkspace, pat: String): Long? {
        val token = pat.trim().ifBlank { return null }
        val url = "https://api.github.com/repos/${workspace.owner}/${workspace.repo}/actions/runs?per_page=1"
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
}
