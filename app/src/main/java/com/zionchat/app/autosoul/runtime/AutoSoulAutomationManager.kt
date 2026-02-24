package com.zionchat.app.autosoul.runtime

import android.content.Context
import android.provider.Settings
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.zionchat.app.autosoul.AutoSoulAccessibilityService
import com.zionchat.app.autosoul.AutoSoulAccessibilityStatus
import com.zionchat.app.autosoul.overlay.AutoSoulFloatingOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AutoSoulRuntimeState(
    val running: Boolean = false,
    val totalSteps: Int = 0,
    val currentStep: Int = 0,
    val statusText: String = "等待任务...",
    val lastError: String? = null
)

object AutoSoulAutomationManager {
    private const val LOG_PREFS_NAME = "autosoul_runtime_prefs"
    private const val LOG_PREFS_KEY = "autosoul_logs_json"
    private const val MAX_LOG_SIZE = 260

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val gson = Gson()
    private val logsLock = Any()
    private var runJob: Job? = null
    private var appContext: Context? = null
    private var lastScript: String = ""
    private var logsHydrated = false

    private val _state = MutableStateFlow(AutoSoulRuntimeState())
    val state: StateFlow<AutoSoulRuntimeState> = _state.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    fun start(
        context: Context,
        script: String,
        keepOverlayVisibleAfterFinish: Boolean = false
    ): Result<Unit> {
        return runCatching {
            val ctx = context.applicationContext
            appContext = ctx
            hydrateLogsFromStorageIfNeeded(ctx)
            if (!AutoSoulAccessibilityStatus.isServiceEnabled(ctx)) {
                error("无障碍服务未开启")
            }
            val steps = AutoSoulScriptParser.parse(script).getOrThrow()
            lastScript = script

            scope.launch {
                runJob?.cancelAndJoin()
                runJob = launch {
                    AutoSoulForegroundService.setRunning(ctx, true)
                    if (Settings.canDrawOverlays(ctx)) {
                        AutoSoulFloatingOverlay.show(ctx)
                    }
                    appendLog("AutoSoul 已启动，共 ${steps.size} 步")
                    _state.value = AutoSoulRuntimeState(running = true, totalSteps = steps.size, currentStep = 0, statusText = "准备执行...")

                    val executor =
                        AutoSoulActionExecutor(
                            context = ctx,
                            serviceProvider = { AutoSoulAccessibilityService.instance }
                        )
                    AutoSoulUiStatus.setRunning("正在准备自动化...")
                    AutoSoulFloatingOverlay.updateState(AutoSoulUiStatus.state.value)

                    var failedError: String? = null
                    for ((index, step) in steps.withIndex()) {
                        if (!isActiveRun()) break
                        val stepNo = index + 1
                        val actionName = step.action
                        val maxRetry = resolveStepRetryLimit(step.args)
                        val totalAttempt = maxRetry + 1
                        var attempt = 1
                        var stepSucceeded = false
                        var nonRetryableError: String? = null

                        while (attempt <= totalAttempt && isActiveRun()) {
                            val statusText =
                                if (attempt == 1) {
                                    "正在执行 ${actionName} (${stepNo}/${steps.size})"
                                } else {
                                    "AI重试 ${actionName}（${attempt}/${totalAttempt}）"
                                }
                            AutoSoulUiStatus.setRecognizing(statusText)
                            AutoSoulFloatingOverlay.updateState(AutoSoulUiStatus.state.value)
                            _state.value =
                                _state.value.copy(
                                    running = true,
                                    totalSteps = steps.size,
                                    currentStep = stepNo,
                                    statusText = statusText,
                                    lastError = null
                                )
                            appendLog(
                                if (attempt == 1) {
                                    "Step $stepNo/${steps.size}: ${step.action} ${step.args}"
                                } else {
                                    "Step $stepNo/${steps.size}: AI重试第 ${attempt - 1}/$maxRetry 次 -> ${step.action} ${step.args}"
                                }
                            )
                            delay(180L)

                            val executionResult =
                                runCatching {
                                    executor.execute(step) { line -> appendLog(line) }
                                }
                            val failure = executionResult.exceptionOrNull()
                            val ok = executionResult.getOrDefault(false)
                            appendLog(
                                "Step $stepNo/${steps.size} 尝试 $attempt/$totalAttempt 执行结果：${if (ok) "成功" else "失败"}"
                            )
                            if (failure is AutoSoulNonRetryableActionException) {
                                nonRetryableError = failure.message?.trim()?.ifBlank { null }
                                nonRetryableError?.let { detail ->
                                    appendLog("Step $stepNo/${steps.size} 不可重试错误：$detail")
                                }
                                break
                            }
                            if (ok) {
                                stepSucceeded = true
                                break
                            }
                            if (attempt < totalAttempt && isActiveRun()) {
                                val retryDelay = (360L + (attempt - 1) * 240L).coerceAtMost(1200L)
                                AutoSoulUiStatus.setThinking("准备 AI 重试 ${actionName}（${attempt}/${maxRetry}）")
                                AutoSoulFloatingOverlay.updateState(AutoSoulUiStatus.state.value)
                                appendLog("AI重试准备中：${retryDelay}ms 后继续")
                                delay(retryDelay)
                            }
                            attempt++
                        }

                        if (!stepSucceeded) {
                            failedError =
                                if (!nonRetryableError.isNullOrBlank()) {
                                    "步骤执行失败：${step.action}（$nonRetryableError）"
                                } else {
                                    "步骤执行失败：${step.action}（已重试 ${maxRetry} 次）"
                                }
                            appendLog(failedError)
                            break
                        }
                        if (stepNo < steps.size) {
                            AutoSoulUiStatus.setRunning("准备下一步...")
                            AutoSoulFloatingOverlay.updateState(AutoSoulUiStatus.state.value)
                            delay(220L)
                        }
                    }

                    if (failedError == null && isActiveRun()) {
                        val successStatusText = "任务结束"
                        AutoSoulUiStatus.setStopped(successStatusText)
                        _state.value =
                            _state.value.copy(
                                running = false,
                                currentStep = steps.size,
                                statusText = successStatusText,
                                lastError = null
                            )
                    } else if (failedError != null) {
                        AutoSoulUiStatus.setStopped("执行失败")
                        appendLog("执行失败")
                        _state.value =
                            _state.value.copy(
                                running = false,
                                statusText = "执行失败",
                                lastError = failedError
                            )
                    } else {
                        AutoSoulUiStatus.setStopped("已停止")
                        appendLog("执行已停止")
                        _state.value =
                            _state.value.copy(
                                running = false,
                                statusText = "已停止"
                            )
                    }
                    AutoSoulFloatingOverlay.updateState(AutoSoulUiStatus.state.value)
                    AutoSoulForegroundService.setRunning(ctx, false)
                    if (!keepOverlayVisibleAfterFinish) {
                        delay(240L)
                        AutoSoulFloatingOverlay.hide()
                    }
                }
            }
        }
    }

    fun stop(
        reason: String = "用户终止",
        hideOverlay: Boolean = true
    ) {
        scope.launch {
            appendLog(reason)
            runJob?.cancelAndJoin()
            runJob = null
            val ctx = appContext
            if (ctx != null) {
                AutoSoulForegroundService.setRunning(ctx, false)
            }
            AutoSoulUiStatus.setStopped("已停止")
            AutoSoulFloatingOverlay.updateState(AutoSoulUiStatus.state.value)
            if (hideOverlay) {
                AutoSoulFloatingOverlay.hide()
            }
            _state.value =
                _state.value.copy(
                    running = false,
                    statusText = "已停止"
                )
        }
    }

    fun closeOverlay() {
        AutoSoulFloatingOverlay.hide()
    }

    fun retryLast(): Result<Unit> {
        val ctx = appContext ?: return Result.failure(IllegalStateException("上下文不可用"))
        if (lastScript.isBlank()) return Result.failure(IllegalStateException("没有可重试脚本"))
        return start(ctx, lastScript)
    }

    fun bindOverlayActions(context: Context) {
        val appCtx = context.applicationContext
        appContext = appCtx
        hydrateLogsFromStorageIfNeeded(appCtx)
        AutoSoulFloatingOverlay.setActionCallbacks(
            onStop = { stop("悬浮窗终止") },
            onSend = {
                runCatching { retryLast().getOrThrow() }
                if (_state.value.running.not() && lastScript.isBlank()) {
                    AutoSoulUiStatus.setRunning("等待任务...")
                    AutoSoulFloatingOverlay.updateState(AutoSoulUiStatus.state.value)
                }
            }
        )
    }

    private fun isActiveRun(): Boolean {
        return runJob?.isActive == true
    }

    private fun resolveStepRetryLimit(args: Map<String, String>): Int {
        val raw =
            args["ai_retry"]
                ?: args["retries"]
                ?: args["retry"]
                ?: args["max_retry"]
                ?: args["max_retries"]
        return raw?.trim()?.toIntOrNull()?.coerceIn(0, 4) ?: 2
    }

    private fun appendLog(line: String) {
        val trimmed = line.trim()
        if (trimmed.isBlank()) return
        synchronized(logsLock) {
            val old = _logs.value
            val updated = (old + "${System.currentTimeMillis()} | $trimmed").takeLast(MAX_LOG_SIZE)
            _logs.value = updated
            appContext?.let { persistLogs(it, updated) }
        }
    }

    private fun hydrateLogsFromStorageIfNeeded(context: Context) {
        if (logsHydrated) return
        synchronized(logsLock) {
            if (logsHydrated) return
            val prefs = context.getSharedPreferences(LOG_PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(LOG_PREFS_KEY, "").orEmpty()
            val loaded = parsePersistedLogs(raw)
            if (loaded.isNotEmpty()) {
                _logs.value = loaded
            }
            logsHydrated = true
        }
    }

    private fun parsePersistedLogs(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        val jsonArray =
            runCatching {
                JsonParser.parseString(raw).takeIf { it.isJsonArray }?.asJsonArray
            }.getOrNull() ?: return emptyList()
        return jsonArray
            .mapNotNull { element ->
                when {
                    element == null || element.isJsonNull -> null
                    element.isJsonPrimitive && element.asJsonPrimitive.isString -> {
                        element.asString.trim().takeIf { it.isNotBlank() }
                    }
                    else -> element.toString().trim().takeIf { it.isNotBlank() }
                }
            }
            .takeLast(MAX_LOG_SIZE)
    }

    private fun persistLogs(context: Context, logs: List<String>) {
        runCatching {
            val prefs = context.getSharedPreferences(LOG_PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(LOG_PREFS_KEY, gson.toJson(logs)).apply()
        }
    }
}
