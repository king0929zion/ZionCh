package com.zionchat.app.autosoul.runtime

import android.content.Context
import android.provider.Settings
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
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var runJob: Job? = null
    private var appContext: Context? = null
    private var lastScript: String = ""

    private val _state = MutableStateFlow(AutoSoulRuntimeState())
    val state: StateFlow<AutoSoulRuntimeState> = _state.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    fun start(context: Context, script: String): Result<Unit> {
        return runCatching {
            val ctx = context.applicationContext
            appContext = ctx
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
                        val statusText = "正在执行 ${actionName} (${stepNo}/${steps.size})"
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
                        appendLog("Step $stepNo/${steps.size}: ${step.action} ${step.args}")
                        delay(180L)

                        val ok =
                            runCatching {
                                executor.execute(step) { line -> appendLog(line) }
                            }.getOrDefault(false)
                        if (!ok) {
                            failedError = "步骤执行失败：${step.action}"
                            appendLog(failedError)
                            break
                        }
                        AutoSoulUiStatus.setRunning("已完成 $stepNo/${steps.size}")
                        AutoSoulFloatingOverlay.updateState(AutoSoulUiStatus.state.value)
                        delay(320L)
                    }

                    if (failedError == null && isActiveRun()) {
                        AutoSoulUiStatus.setStopped("执行完成")
                        appendLog("执行完成")
                        _state.value =
                            _state.value.copy(
                                running = false,
                                currentStep = steps.size,
                                statusText = "执行完成",
                                lastError = null
                            )
                    } else if (failedError != null) {
                        AutoSoulUiStatus.setStopped("执行失败")
                        _state.value =
                            _state.value.copy(
                                running = false,
                                statusText = "执行失败",
                                lastError = failedError
                            )
                    } else {
                        AutoSoulUiStatus.setStopped("已停止")
                        _state.value =
                            _state.value.copy(
                                running = false,
                                statusText = "已停止"
                            )
                    }
                    AutoSoulFloatingOverlay.updateState(AutoSoulUiStatus.state.value)
                    AutoSoulForegroundService.setRunning(ctx, false)
                }
            }
        }
    }

    fun stop(reason: String = "用户终止") {
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
            _state.value =
                _state.value.copy(
                    running = false,
                    statusText = "已停止"
                )
        }
    }

    fun retryLast(): Result<Unit> {
        val ctx = appContext ?: return Result.failure(IllegalStateException("上下文不可用"))
        if (lastScript.isBlank()) return Result.failure(IllegalStateException("没有可重试脚本"))
        return start(ctx, lastScript)
    }

    fun bindOverlayActions(context: Context) {
        appContext = context.applicationContext
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

    private fun appendLog(line: String) {
        val trimmed = line.trim()
        if (trimmed.isBlank()) return
        val old = _logs.value
        _logs.value = (old + "${System.currentTimeMillis()} | $trimmed").takeLast(180)
    }
}

