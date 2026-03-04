package com.zionchat.app.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.zionchat.app.data.extractRemoteModelId
import kotlinx.coroutines.flow.collect

data class ZiCodeModelAgentResult(
    val success: Boolean,
    val finalMessage: String,
    val toolHints: List<String> = emptyList()
)

private data class AgentEnvelope(
    val type: String,
    val toolName: String? = null,
    val argsJson: String = "{}",
    val finalAnswer: String? = null
)

class ZiCodeModelAgent(
    private val chatApiClient: ChatApiClient,
    private val providerAuthManager: ProviderAuthManager,
    private val toolDispatcher: ZiCodeToolDispatcher,
    private val policyService: ZiCodePolicyService,
    private val gson: Gson = Gson()
) {
    suspend fun runTask(
        sessionId: String,
        workspace: ZiCodeWorkspace,
        settings: ZiCodeSettings,
        provider: ProviderConfig,
        model: ModelConfig,
        userPrompt: String,
        recentMessages: List<ZiCodeMessage>,
        onStatus: suspend (String) -> Unit = {},
        onStreamAnswer: suspend (String) -> Unit = {}
    ): ZiCodeModelAgentResult {
        val resolvedProvider =
            runCatching { providerAuthManager.ensureValidProvider(provider) }
                .getOrElse { error ->
                    return ZiCodeModelAgentResult(
                        success = false,
                        finalMessage = "ZiCode 模型鉴权失败：${error.message.orEmpty().ifBlank { "unknown error" }}"
                    )
                }

        val toolSpec = policyService.getToolspec().toString()
        val maxTurns = 8
        val stepSummaries = mutableListOf<String>()
        val toolHints = linkedSetOf<String>()
        var finalAnswer: String? = null
        var lastWorkflowHint: String? = null

        val modelId = extractRemoteModelId(model.id).ifBlank { model.id }
        for (turn in 1..maxTurns) {
            onStatus("ZiCode 正在规划第 $turn 轮...")
            val plannerPrompt =
                buildPlannerPrompt(
                    workspace = workspace,
                    userPrompt = userPrompt,
                    recentMessages = recentMessages,
                    toolSpec = toolSpec,
                    stepSummaries = stepSummaries
                )
            val rawBuilder = StringBuilder()
            var streamedAnswer = ""
            val streamedResult =
                runCatching {
                    chatApiClient.chatCompletionsStream(
                        provider = resolvedProvider,
                        modelId = modelId,
                        messages = listOf(
                            Message(role = "system", content = MODEL_AGENT_SYSTEM_PROMPT),
                            Message(role = "user", content = plannerPrompt)
                        ),
                        extraHeaders = model.headers,
                        reasoningEffort = model.reasoningEffort,
                        enableThinking = true,
                        maxTokens = 1800
                    ).collect { delta ->
                        val chunk = delta.content.orEmpty()
                        if (chunk.isBlank()) return@collect
                        rawBuilder.append(chunk)
                        val partial = extractFinalAnswerFromPartialEnvelope(rawBuilder.toString()) ?: return@collect
                        if (partial.length <= streamedAnswer.length) return@collect
                        streamedAnswer = partial
                        onStreamAnswer(streamedAnswer)
                    }
                }
            if (streamedResult.isFailure) {
                return ZiCodeModelAgentResult(
                    success = false,
                    finalMessage = "ZiCode 模型调用失败：${streamedResult.exceptionOrNull()?.message.orEmpty().ifBlank { "unknown error" }}"
                )
            }

            val raw = rawBuilder.toString().trim()
            if (raw.isBlank()) {
                return ZiCodeModelAgentResult(
                    success = false,
                    finalMessage = "ZiCode 模型返回为空，请重试。"
                )
            }

            val envelope = parseEnvelopeWithRetry(raw, resolvedProvider, modelId, model.headers)
            if (envelope == null) {
                return ZiCodeModelAgentResult(
                    success = false,
                    finalMessage = "ZiCode 无法解析模型决策结果，请重试。",
                    toolHints = toolHints.toList()
                )
            }

            when (envelope.type.lowercase()) {
                "final_answer" -> {
                    finalAnswer = envelope.finalAnswer?.trim().takeIf { !it.isNullOrBlank() }
                    break
                }

                "tool_call" -> {
                    val toolName = envelope.toolName?.trim().orEmpty()
                    if (toolName.isBlank()) {
                        stepSummaries += "第 $turn 轮决策缺少工具名，已跳过。"
                        continue
                    }
                    onStatus("正在执行工具：$toolName")
                    val callResult =
                        runCatching {
                            toolDispatcher.dispatch(
                                sessionId = sessionId,
                                workspace = workspace,
                                settings = settings,
                                toolName = toolName,
                                argsJson = envelope.argsJson
                            )
                        }.getOrElse { error ->
                            val friendly = buildFriendlyToolError(toolName, error.message)
                            stepSummaries += "工具 `$toolName` 调用异常：$friendly"
                            continue
                        }

                    if (callResult.userHint.isNotBlank()) {
                        toolHints += callResult.userHint
                    }
                    if (callResult.success) {
                        val summary = summarizeResult(callResult.resultJson.orEmpty())
                        stepSummaries += "工具 `$toolName` 成功：$summary"
                        if (toolName == "actions.trigger_workflow") {
                            val workflowFile = parseWorkflowFromArgs(envelope.argsJson)
                            val workflowBranch = parseRefFromArgs(envelope.argsJson, workspace.defaultBranch)
                            if (workflowFile.isNotBlank()) {
                                val healText =
                                    tryWorkflowSelfHeal(
                                        sessionId = sessionId,
                                        workspace = workspace,
                                        settings = settings,
                                        workflowFile = workflowFile,
                                        branch = workflowBranch,
                                        provider = resolvedProvider,
                                        modelId = modelId,
                                        modelHeaders = model.headers
                                    )
                                if (healText.isNotBlank()) {
                                    stepSummaries += healText
                                    lastWorkflowHint = healText
                                }
                            }
                        }
                    } else {
                        val friendly = buildFriendlyToolError(toolName, callResult.error)
                        stepSummaries += "工具 `$toolName` 失败：$friendly"
                    }
                }

                else -> {
                    stepSummaries += "第 $turn 轮返回未知类型 `${envelope.type}`，已忽略。"
                }
            }
        }

        val finalText =
            finalAnswer
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: runCatching {
                    tryForceFinalAnswer(
                        provider = resolvedProvider,
                        modelId = modelId,
                        modelHeaders = model.headers,
                        workspace = workspace,
                        userPrompt = userPrompt,
                        stepSummaries = stepSummaries
                    )
                }.getOrNull()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                ?: buildFallbackSummary(stepSummaries, lastWorkflowHint)

        return ZiCodeModelAgentResult(
            success = true,
            finalMessage = finalText,
            toolHints = toolHints.toList().takeLast(6)
        )
    }

    private suspend fun tryWorkflowSelfHeal(
        sessionId: String,
        workspace: ZiCodeWorkspace,
        settings: ZiCodeSettings,
        workflowFile: String,
        branch: String,
        provider: ProviderConfig,
        modelId: String,
        modelHeaders: List<HttpHeader>
    ): String {
        val latestRunResult =
            toolDispatcher.dispatch(
                sessionId = sessionId,
                workspace = workspace,
                settings = settings,
                toolName = "actions.get_latest_run",
                argsJson =
                    gson.toJson(
                        JsonObject().apply {
                            addProperty("workflow", workflowFile)
                            addProperty("branch", branch)
                        }
                    )
            )
        if (!latestRunResult.success) {
            return "工作流已触发，但未能读取最新 run：${latestRunResult.error.orEmpty()}"
        }
        var runId = parseRunId(latestRunResult.resultJson)
        if (runId <= 0L) {
            return "工作流已触发，但暂未拿到 run_id。"
        }

        val maxLoops = settings.maxSelfHealLoops.coerceIn(1, 10)
        repeat(maxLoops) { loopIndex ->
            val runResult =
                toolDispatcher.dispatch(
                    sessionId = sessionId,
                    workspace = workspace,
                    settings = settings,
                    toolName = "actions.get_run",
                    argsJson = gson.toJson(JsonObject().apply { addProperty("run_id", runId) })
                )
            if (!runResult.success) {
                return "自愈第 ${loopIndex + 1} 轮读取运行状态失败：${runResult.error.orEmpty()}"
            }
            val conclusion = parseRunConclusion(runResult.resultJson)
            if (conclusion == "success") {
                return "工作流 #$runId 构建成功。"
            }
            if (conclusion == "queued" || conclusion == "in_progress" || conclusion == "waiting") {
                return "工作流 #$runId 正在执行，继续观察中。"
            }

            val logsSummary =
                toolDispatcher.dispatch(
                    sessionId = sessionId,
                    workspace = workspace,
                    settings = settings,
                    toolName = "actions.get_logs_summary",
                    argsJson = gson.toJson(JsonObject().apply { addProperty("run_id", runId) })
                )
            if (!logsSummary.success) {
                return "自愈第 ${loopIndex + 1} 轮读取日志失败：${logsSummary.error.orEmpty()}"
            }
            val report = parseReport(logsSummary.resultJson)
            val patchText =
                generatePatchByModel(
                    provider = provider,
                    modelId = modelId,
                    modelHeaders = modelHeaders,
                    workflowFile = workflowFile,
                    runId = runId,
                    report = report
                )
            if (patchText.isBlank()) {
                return "工作流失败：${report.errorSummary?.ifBlank { null } ?: "未生成可用补丁"}"
            }

            val riskResult =
                toolDispatcher.dispatch(
                    sessionId = sessionId,
                    workspace = workspace,
                    settings = settings,
                    toolName = "policy.check_risk",
                    argsJson =
                        gson.toJson(
                            JsonObject().apply {
                                addProperty("patch", patchText)
                                add("touched_paths", gson.toJsonTree(report.fileHints))
                            }
                        )
                )
            if (!riskResult.success) {
                return "自愈第 ${loopIndex + 1} 轮风险评估失败：${riskResult.error.orEmpty()}"
            }
            val risk = parseRiskLevel(riskResult.resultJson)
            if (risk == "high") {
                return "自愈补丁被策略拒绝（高风险）。"
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
                return "自愈第 ${loopIndex + 1} 轮补丁应用失败：${applyResult.error.orEmpty()}"
            }

            val commitResult =
                toolDispatcher.dispatch(
                    sessionId = sessionId,
                    workspace = workspace,
                    settings = settings,
                    toolName = "repo.commit_push",
                    argsJson =
                        gson.toJson(
                            JsonObject().apply {
                                addProperty("message", "ZiCode auto-heal #${loopIndex + 1}")
                            }
                        )
                )
            if (!commitResult.success) {
                return "自愈第 ${loopIndex + 1} 轮提交失败：${commitResult.error.orEmpty()}"
            }

            val triggerResult =
                toolDispatcher.dispatch(
                    sessionId = sessionId,
                    workspace = workspace,
                    settings = settings,
                    toolName = "actions.trigger_workflow",
                    argsJson =
                        gson.toJson(
                            JsonObject().apply {
                                addProperty("workflow", workflowFile)
                                addProperty("ref", branch)
                            }
                        )
                )
            if (!triggerResult.success) {
                return "自愈第 ${loopIndex + 1} 轮重触发失败：${triggerResult.error.orEmpty()}"
            }

            val nextRun =
                toolDispatcher.dispatch(
                    sessionId = sessionId,
                    workspace = workspace,
                    settings = settings,
                    toolName = "actions.get_latest_run",
                    argsJson =
                        gson.toJson(
                            JsonObject().apply {
                                addProperty("workflow", workflowFile)
                                addProperty("branch", branch)
                            }
                        )
                )
            if (!nextRun.success) {
                return "重触发后无法读取最新 run。"
            }
            runId = parseRunId(nextRun.resultJson)
            if (runId <= 0L) {
                return "重触发后未获取到新的 run_id。"
            }
        }
        return "工作流自愈达到上限（${settings.maxSelfHealLoops} 轮）。"
    }

    private suspend fun generatePatchByModel(
        provider: ProviderConfig,
        modelId: String,
        modelHeaders: List<HttpHeader>,
        workflowFile: String,
        runId: Long,
        report: ZiCodeReport
    ): String {
        val prompt =
            buildString {
                appendLine("你是 Android/Kotlin 工程修复助手。请基于失败报告生成 unified diff 补丁。")
                appendLine("必须只输出补丁文本，不要 markdown，不要解释。")
                appendLine("若无法生成补丁，仅输出 NO_PATCH。")
                appendLine("workflow: $workflowFile")
                appendLine("run_id: $runId")
                appendLine("error_summary: ${report.errorSummary.orEmpty()}")
                appendLine("failing_step: ${report.failingStep.orEmpty()}")
                appendLine("file_hints: ${report.fileHints.joinToString(",")}")
                appendLine("next_reads: ${report.nextReads.joinToString(",")}")
            }
        val raw =
            chatApiClient.chatCompletions(
                provider = provider,
                modelId = modelId,
                messages = listOf(
                    Message(role = "system", content = "仅输出 unified diff 补丁正文或 NO_PATCH。"),
                    Message(role = "user", content = prompt)
                ),
                extraHeaders = modelHeaders,
                reasoningEffort = "none",
                enableThinking = false,
                maxTokens = 2000
            ).getOrNull().orEmpty().trim()
        val normalized = stripCodeFence(raw)
        if (normalized.equals("NO_PATCH", ignoreCase = true)) return ""
        return normalized.takeIf { it.contains("diff --git") || it.contains("@@") }.orEmpty()
    }

    private fun buildPlannerPrompt(
        workspace: ZiCodeWorkspace,
        userPrompt: String,
        recentMessages: List<ZiCodeMessage>,
        toolSpec: String,
        stepSummaries: List<String>
    ): String {
        val history =
            recentMessages
                .takeLast(10)
                .joinToString("\n") { msg ->
                    "[${msg.role}] ${msg.content.trim().take(220)}"
                }
                .ifBlank { "(empty)" }
        val summary =
            stepSummaries
                .takeLast(8)
                .joinToString("\n") { "- $it" }
                .ifBlank { "(no steps yet)" }
        return buildString {
            appendLine("用户任务：$userPrompt")
            appendLine("当前工作区：${workspace.owner}/${workspace.repo}")
            appendLine("默认分支：${workspace.defaultBranch}")
            appendLine("最近会话消息：")
            appendLine(history)
            appendLine("已执行步骤：")
            appendLine(summary)
            appendLine("可用工具规范：")
            appendLine(toolSpec)
            appendLine("请输出 JSON envelope：")
            appendLine(
                """{"type":"tool_call","tool_name":"repo.list_tree","args":{"ref":"main"}}"""
            )
            appendLine("或")
            appendLine(
                """{"type":"final_answer","final_answer":"..."}"""
            )
        }
    }

    private suspend fun parseEnvelopeWithRetry(
        raw: String,
        provider: ProviderConfig,
        modelId: String,
        modelHeaders: List<HttpHeader>
    ): AgentEnvelope? {
        parseEnvelope(raw)?.let { return it }
        val repairPrompt =
            buildString {
                appendLine("请把下面内容转换成合法 JSON envelope，不要添加说明：")
                appendLine(raw.take(1800))
            }
        val repaired =
            chatApiClient.chatCompletions(
                provider = provider,
                modelId = modelId,
                messages = listOf(
                    Message(role = "system", content = "只返回合法 JSON。"),
                    Message(role = "user", content = repairPrompt)
                ),
                extraHeaders = modelHeaders,
                reasoningEffort = "none",
                enableThinking = false,
                maxTokens = 800
            ).getOrNull().orEmpty()
        return parseEnvelope(repaired)
    }

    private fun parseEnvelope(raw: String): AgentEnvelope? {
        val trimmed = stripCodeFence(raw.trim())
        if (trimmed.isBlank()) return null
        val obj = runCatching { gson.fromJson(trimmed, JsonObject::class.java) }.getOrNull() ?: return null
        val type =
            obj.get("type")?.asString?.trim()
                ?: obj.get("action")?.asString?.trim()
                ?: obj.get("kind")?.asString?.trim()
                ?: return null
        val normalizedType =
            when (type.lowercase()) {
                "final", "answer", "finalanswer", "final_answer" -> "final_answer"
                else -> type.lowercase()
            }
        return if (normalizedType == "tool_call") {
            val toolName =
                obj.get("tool_name")?.asString?.trim()
                    ?: obj.get("tool")?.asString?.trim()
                    ?: obj.get("toolName")?.asString?.trim()
            val args =
                when {
                    obj.get("args")?.isJsonObject == true -> gson.toJson(obj.getAsJsonObject("args"))
                    obj.get("arguments")?.isJsonObject == true -> gson.toJson(obj.getAsJsonObject("arguments"))
                    obj.get("args_json") != null -> obj.get("args_json")?.asString.orEmpty().ifBlank { "{}" }
                    else -> "{}"
                }
            AgentEnvelope(type = "tool_call", toolName = toolName, argsJson = args)
        } else if (normalizedType == "final_answer") {
            val answer =
                obj.get("final_answer")?.asString?.trim()
                    ?: obj.get("answer")?.asString?.trim()
                    ?: obj.get("final")?.asString?.trim()
                    ?: obj.get("content")?.asString?.trim()
                    ?: ""
            AgentEnvelope(type = "final_answer", finalAnswer = answer)
        } else {
            null
        }
    }

    private fun parseWorkflowFromArgs(argsJson: String): String {
        val obj = runCatching { gson.fromJson(argsJson, JsonObject::class.java) }.getOrNull() ?: return ""
        return obj.get("workflow")?.asString?.trim().orEmpty()
    }

    private fun parseRefFromArgs(argsJson: String, fallback: String): String {
        val obj = runCatching { gson.fromJson(argsJson, JsonObject::class.java) }.getOrNull() ?: return fallback
        return obj.get("ref")?.asString?.trim().takeIf { !it.isNullOrBlank() } ?: fallback
    }

    private fun parseRunId(raw: String?): Long {
        val obj = runCatching { gson.fromJson(raw.orEmpty(), JsonObject::class.java) }.getOrNull() ?: return -1L
        if (obj.has("id")) return obj.get("id")?.asLong ?: -1L
        val latest = obj.getAsJsonObject("latest")
        if (latest != null && latest.has("id")) {
            return latest.get("id")?.asLong ?: -1L
        }
        val workflowRuns = obj.getAsJsonArray("workflow_runs")
        val first = workflowRuns?.firstOrNull()?.asJsonObject ?: return -1L
        return first.get("id")?.asLong ?: -1L
    }

    private fun parseRunConclusion(raw: String?): String {
        val obj = runCatching { gson.fromJson(raw.orEmpty(), JsonObject::class.java) }.getOrNull() ?: return "unknown"
        val conclusion = obj.get("conclusion")?.asString?.trim().orEmpty()
        if (conclusion.isNotBlank()) return conclusion.lowercase()
        val status = obj.get("status")?.asString?.trim().orEmpty()
        return status.lowercase().ifBlank { "unknown" }
    }

    private fun parseReport(raw: String?): ZiCodeReport {
        val obj = runCatching { gson.fromJson(raw.orEmpty(), JsonObject::class.java) }.getOrNull() ?: return ZiCodeReport(
            status = "failure",
            summary = "logs summary parse failed"
        )
        if (obj.get("report")?.isJsonObject == true) {
            return runCatching { gson.fromJson(obj.getAsJsonObject("report"), ZiCodeReport::class.java) }.getOrElse {
                ZiCodeReport(status = "failure", summary = "invalid report")
            }
        }
        return runCatching { gson.fromJson(obj, ZiCodeReport::class.java) }.getOrElse {
            ZiCodeReport(
                status = "failure",
                summary = obj.get("summary")?.asString.orEmpty(),
                errorSummary = obj.get("error_summary")?.asString
            )
        }
    }

    private fun parseRiskLevel(raw: String?): String {
        val obj = runCatching { gson.fromJson(raw.orEmpty(), JsonObject::class.java) }.getOrNull() ?: return "unknown"
        return obj.get("level")?.asString?.trim()?.lowercase().orEmpty().ifBlank { "unknown" }
    }

    private fun summarizeResult(raw: String): String {
        val clean = raw.trim()
        if (clean.isBlank()) return "ok"
        return clean.replace("\n", " ").replace(Regex("\\s+"), " ").take(180)
    }

    private suspend fun tryForceFinalAnswer(
        provider: ProviderConfig,
        modelId: String,
        modelHeaders: List<HttpHeader>,
        workspace: ZiCodeWorkspace,
        userPrompt: String,
        stepSummaries: List<String>
    ): String {
        if (stepSummaries.isEmpty()) return ""
        val lastSteps =
            stepSummaries
                .takeLast(10)
                .map { stripToolSummaryPrefix(it).take(220) }
                .filter { it.isNotBlank() }
                .takeLast(8)
        if (lastSteps.isEmpty()) return ""

        val prompt =
            buildString {
                appendLine("请基于用户任务与已执行步骤，给出简洁的最终答复。")
                appendLine("只允许输出 JSON envelope，且必须是 final_answer，不要 tool_call，不要解释。")
                appendLine("用户任务：$userPrompt")
                appendLine("工作区：${workspace.owner}/${workspace.repo}（分支：${workspace.defaultBranch}）")
                appendLine("已执行步骤：")
                lastSteps.forEach { appendLine("- $it") }
                appendLine("""{"type":"final_answer","final_answer":"..."}""")
            }
        val raw =
            chatApiClient.chatCompletions(
                provider = provider,
                modelId = modelId,
                messages = listOf(
                    Message(role = "system", content = "只返回合法 JSON，type 必须是 final_answer。"),
                    Message(role = "user", content = prompt)
                ),
                extraHeaders = modelHeaders,
                reasoningEffort = "none",
                enableThinking = false,
                maxTokens = 800
            ).getOrNull().orEmpty().trim()
        val envelope = parseEnvelope(raw) ?: return ""
        if (envelope.type.lowercase() != "final_answer") return ""
        return envelope.finalAnswer.orEmpty().trim()
    }

    private fun buildFallbackSummary(stepSummaries: List<String>, workflowHint: String?): String {
        val lastError =
            stepSummaries.lastOrNull { line ->
                line.contains("失败") || line.contains("异常")
            }?.let(::stripToolSummaryPrefix)

        if (!lastError.isNullOrBlank()) {
            return buildString {
                append("执行遇到问题：")
                append(lastError.take(220))
                appendLine()
                append("你可以直接重试；如果反复失败，请检查 PAT 是否具备仓库写入权限。")
            }.trim()
        }

        workflowHint?.takeIf { it.isNotBlank() }?.let { hint ->
            return hint.trim()
        }

        val lastStep = stepSummaries.lastOrNull()?.let(::stripToolSummaryPrefix).orEmpty()
        return if (lastStep.isNotBlank()) {
            "已执行完成。"
        } else {
            "任务已执行，但未产出可展示摘要。"
        }
    }

    private fun buildFriendlyToolError(toolName: String, rawError: String?): String {
        if (isRepositoryEmptyError(rawError)) {
            return "仓库为空，我会自动初始化首个提交后继续；若仍失败，请检查 PAT 是否有写入权限。"
        }
        val compact = rawError.orEmpty().trim().replace("\n", " ").replace(Regex("\\s+"), " ")
        return compact.ifBlank { "unknown error" }.take(160)
    }

    private fun stripToolSummaryPrefix(text: String): String {
        val trimmed = text.trim()
        return trimmed
            .replace(Regex("^工具\\s+`[^`]+`\\s+调用异常：\\s*"), "")
            .replace(Regex("^工具\\s+`[^`]+`\\s+失败：\\s*"), "")
            .replace(Regex("^工具\\s+`[^`]+`\\s+成功：\\s*"), "")
            .trim()
    }

    private fun isRepositoryEmptyError(rawError: String?): Boolean {
        val normalized = rawError.orEmpty().lowercase()
        return normalized.contains("repository is empty") ||
            normalized.contains("this repository is empty") ||
            normalized.contains("git repository is empty")
    }

    private fun extractFinalAnswerFromPartialEnvelope(raw: String): String? {
        val keyIndex = raw.indexOf("\"final_answer\"")
        if (keyIndex < 0) return null
        val colonIndex = raw.indexOf(':', startIndex = keyIndex)
        if (colonIndex < 0) return null
        var cursor = colonIndex + 1
        while (cursor < raw.length && raw[cursor].isWhitespace()) {
            cursor += 1
        }
        if (cursor >= raw.length || raw[cursor] != '"') return null
        cursor += 1
        val answer = StringBuilder()
        var escaped = false
        while (cursor < raw.length) {
            val ch = raw[cursor]
            if (escaped) {
                when (ch) {
                    '\\', '"', '/' -> answer.append(ch)
                    'b' -> answer.append('\b')
                    'f' -> answer.append('\u000C')
                    'n' -> answer.append('\n')
                    'r' -> answer.append('\r')
                    't' -> answer.append('\t')
                    'u' -> {
                        if (cursor + 4 < raw.length) {
                            val hex = raw.substring(cursor + 1, cursor + 5)
                            val parsed = hex.toIntOrNull(16)
                            if (parsed != null) {
                                answer.append(parsed.toChar())
                                cursor += 4
                            }
                        }
                    }
                    else -> answer.append(ch)
                }
                escaped = false
            } else if (ch == '\\') {
                escaped = true
            } else if (ch == '"') {
                return answer.toString()
            } else {
                answer.append(ch)
            }
            cursor += 1
        }
        return answer.toString()
    }

    private fun stripCodeFence(raw: String): String {
        var text = raw.trim()
        if (text.startsWith("```")) {
            text = text.removePrefix("```")
            val firstLineEnd = text.indexOf('\n')
            if (firstLineEnd >= 0) {
                text = text.substring(firstLineEnd + 1)
            }
            text = text.removeSuffix("```").trim()
        }
        return text
    }
}

private const val MODEL_AGENT_SYSTEM_PROMPT = """
你是 ZiCode 的模型驱动执行代理。
你必须遵守：
1) 只能通过工具完成仓库、构建与部署操作；
2) 禁止本地 shell；
3) 输出必须是 JSON envelope；
4) 需要调用工具时输出 type=tool_call；
5) 全部完成时输出 type=final_answer。
"""
