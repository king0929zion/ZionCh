package com.zionchat.app.autosoul.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AutoSoulStatusMode {
    RUNNING,
    RECOGNIZING,
    THINKING,
    STOPPED
}

data class AutoSoulStatusState(
    val mode: AutoSoulStatusMode = AutoSoulStatusMode.RUNNING,
    val text: String = "等待任务...",
    val stopped: Boolean = false
)

object AutoSoulUiStatus {
    private val _state = MutableStateFlow(AutoSoulStatusState())
    val state: StateFlow<AutoSoulStatusState> = _state.asStateFlow()

    fun setRunning(text: String) {
        _state.value = AutoSoulStatusState(mode = AutoSoulStatusMode.RUNNING, text = text, stopped = false)
    }

    fun setRecognizing(text: String) {
        _state.value = AutoSoulStatusState(mode = AutoSoulStatusMode.RECOGNIZING, text = text, stopped = false)
    }

    fun setThinking(text: String) {
        _state.value = AutoSoulStatusState(mode = AutoSoulStatusMode.THINKING, text = text, stopped = false)
    }

    fun setStopped(text: String = "已停止") {
        _state.value = AutoSoulStatusState(mode = AutoSoulStatusMode.STOPPED, text = text, stopped = true)
    }
}

