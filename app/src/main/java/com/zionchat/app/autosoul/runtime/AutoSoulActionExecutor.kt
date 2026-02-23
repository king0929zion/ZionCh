package com.zionchat.app.autosoul.runtime

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.zionchat.app.autosoul.AutoSoulAccessibilityService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AutoSoulActionExecutor(
    private val context: Context,
    private val serviceProvider: () -> AutoSoulAccessibilityService?
) {
    fun execute(step: AutoSoulScriptStep, onLog: (String) -> Unit): Boolean {
        val service = serviceProvider() ?: return false
        val action = step.action.trim().lowercase()
        return when (action) {
            "home" -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            "back" -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            "wait" -> {
                val durationMs = parseDuration(step.args).coerceIn(0L, 30_000L)
                Thread.sleep(durationMs)
                true
            }

            "launch" -> {
                val target = step.args["package"].orEmpty().ifBlank { step.args["app"].orEmpty() }
                launchApp(target)
            }

            "tap" -> {
                val point = parsePoint(step.args["point"] ?: "${step.args["x"]},${step.args["y"]}") ?: return false
                val (x, y) = toAbsolutePoint(point.first, point.second)
                awaitGesture { cb -> service.performTap(x, y, cb) }
            }

            "long_press", "longpress" -> {
                val point = parsePoint(step.args["point"] ?: "${step.args["x"]},${step.args["y"]}") ?: return false
                val (x, y) = toAbsolutePoint(point.first, point.second)
                awaitGesture { cb -> service.performLongPress(x, y, cb) }
            }

            "swipe" -> {
                val start = parsePoint(step.args["start"] ?: "${step.args["start_x"]},${step.args["start_y"]}") ?: return false
                val end = parsePoint(step.args["end"] ?: "${step.args["end_x"]},${step.args["end_y"]}") ?: return false
                val (sx, sy) = toAbsolutePoint(start.first, start.second)
                val (ex, ey) = toAbsolutePoint(end.first, end.second)
                val duration = step.args["duration_ms"]?.toLongOrNull()?.coerceIn(80L, 5000L) ?: 360L
                awaitGesture { cb ->
                    service.performSwipe(
                        startX = sx,
                        startY = sy,
                        endX = ex,
                        endY = ey,
                        durationMs = duration,
                        onDone = cb
                    )
                }
            }

            "type", "input" -> {
                val text = step.args["text"].orEmpty()
                inputText(service, text, onLog)
            }

            "tap_text" -> {
                val text = step.args["text"].orEmpty().trim()
                if (text.isBlank()) return false
                val node = service.findNodeByText(text)
                service.clickNode(node)
            }

            else -> false
        }
    }

    private fun launchApp(target: String): Boolean {
        val trimmed = target.trim()
        if (trimmed.isBlank()) return false

        val packageName =
            if (trimmed.contains(".")) {
                trimmed
            } else {
                resolvePackageByAppName(trimmed) ?: return false
            }
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun resolvePackageByAppName(appName: String): String? {
        val pm = context.packageManager
        val launcherIntent =
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = pm.queryIntentActivities(launcherIntent, 0)
        val exact =
            list.firstOrNull { item ->
                val label = item.loadLabel(pm)?.toString().orEmpty()
                label.equals(appName, ignoreCase = true)
            }
        if (exact != null) return exact.activityInfo.packageName

        val fuzzy =
            list.firstOrNull { item ->
                val label = item.loadLabel(pm)?.toString().orEmpty()
                label.contains(appName, ignoreCase = true)
            }
        return fuzzy?.activityInfo?.packageName
    }

    private fun inputText(
        service: AutoSoulAccessibilityService,
        text: String,
        onLog: (String) -> Unit
    ): Boolean {
        if (text.isBlank()) return false
        val root = service.rootInActiveWindow ?: return false
        val target = findEditableNode(root)
        if (target == null) {
            onLog("未找到可输入控件")
            return false
        }

        runCatching { target.performAction(AccessibilityNodeInfo.ACTION_FOCUS) }
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val setTextOk = runCatching { target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) }.getOrDefault(false)
        if (setTextOk) return true

        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return false
        runCatching { clipboard.setPrimaryClip(ClipData.newPlainText("AutoSoul", text)) }
        return runCatching { target.performAction(AccessibilityNodeInfo.ACTION_PASTE) }.getOrDefault(false)
    }

    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(node)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val editable = current.isEditable || current.className?.toString() == "android.widget.EditText"
            if (editable) return current
            for (index in 0 until current.childCount) {
                current.getChild(index)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun parseDuration(args: Map<String, String>): Long {
        val durationMs = args["duration_ms"]?.toLongOrNull()
        if (durationMs != null) return durationMs
        val duration = args["duration"]?.trim().orEmpty()
        if (duration.isBlank()) return 800L
        if (duration.endsWith("ms", true)) {
            return duration.removeSuffix("ms").trim().toLongOrNull() ?: 800L
        }
        return (duration.toDoubleOrNull()?.times(1000.0))?.toLong() ?: 800L
    }

    private fun parsePoint(raw: String): Pair<Double, Double>? {
        val cleaned = raw.trim().removePrefix("[").removeSuffix("]")
        val parts = cleaned.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (parts.size < 2) return null
        val x = parts[0].toDoubleOrNull() ?: return null
        val y = parts[1].toDoubleOrNull() ?: return null

        return if (x in 0.0..1.0 && y in 0.0..1.0) {
            x to y
        } else {
            (x.coerceIn(0.0, 1000.0) / 1000.0) to (y.coerceIn(0.0, 1000.0) / 1000.0)
        }
    }

    private fun toAbsolutePoint(normX: Double, normY: Double): Pair<Float, Float> {
        val dm = context.resources.displayMetrics
        val x = (normX.coerceIn(0.0, 1.0) * dm.widthPixels).toFloat()
        val y = (normY.coerceIn(0.0, 1.0) * dm.heightPixels).toFloat()
        return x to y
    }

    private fun awaitGesture(start: ((Boolean) -> Unit) -> Unit): Boolean {
        val latch = CountDownLatch(1)
        var ok = false
        start { success ->
            ok = success
            latch.countDown()
        }
        return latch.await(7L, TimeUnit.SECONDS) && ok
    }
}

