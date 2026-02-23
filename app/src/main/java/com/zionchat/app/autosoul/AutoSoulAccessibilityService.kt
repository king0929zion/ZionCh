package com.zionchat.app.autosoul

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AutoSoulAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    fun performTap(x: Float, y: Float, onDone: ((Boolean) -> Unit)? = null) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            onDone?.invoke(false)
            return
        }
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 70L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val dispatched =
            dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        onDone?.invoke(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        onDone?.invoke(false)
                    }
                },
                null
            )
        if (!dispatched) onDone?.invoke(false)
    }

    fun performLongPress(x: Float, y: Float, onDone: ((Boolean) -> Unit)? = null) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            onDone?.invoke(false)
            return
        }
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 650L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val dispatched =
            dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        onDone?.invoke(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        onDone?.invoke(false)
                    }
                },
                null
            )
        if (!dispatched) onDone?.invoke(false)
    }

    fun performSwipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long,
        onDone: ((Boolean) -> Unit)? = null
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            onDone?.invoke(false)
            return
        }
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs.coerceAtLeast(50L))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val dispatched =
            dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        onDone?.invoke(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        onDone?.invoke(false)
                    }
                },
                null
            )
        if (!dispatched) onDone?.invoke(false)
    }

    fun findNodeByText(text: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        return root.findAccessibilityNodeInfosByText(text).firstOrNull()
    }

    fun clickNode(node: AccessibilityNodeInfo?): Boolean {
        var current = node ?: return false
        while (true) {
            if (current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            val parent = current.parent ?: return false
            current = parent
        }
    }

    fun setText(node: AccessibilityNodeInfo?, text: String): Boolean {
        val target = node ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    companion object {
        @Volatile
        var instance: AutoSoulAccessibilityService? = null
            private set
    }
}

