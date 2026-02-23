package com.zionchat.app.autosoul.runtime

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
        val service =
            serviceProvider() ?: run {
                onLog("操作结果(${step.action})：失败（无障碍服务不可用）")
                return false
            }
        val action = step.action.trim().lowercase()
        return when (action) {
            "home" -> {
                val ok = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                onLog("操作结果(Home)：${if (ok) "成功" else "失败"}")
                ok
            }
            "back" -> {
                val ok = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                onLog("操作结果(Back)：${if (ok) "成功" else "失败"}")
                ok
            }
            "wait" -> {
                val durationMs = parseDuration(step.args).coerceIn(0L, 30_000L)
                Thread.sleep(durationMs)
                onLog("操作结果(Wait)：成功（${durationMs}ms）")
                true
            }

            "launch" -> {
                val target = step.args["package"].orEmpty().ifBlank { step.args["app"].orEmpty() }
                launchApp(target, onLog)
            }

            "tap" -> {
                val point = parsePoint(step.args["point"] ?: "${step.args["x"]},${step.args["y"]}") ?: return false
                val (x, y) = toAbsolutePoint(point.first, point.second)
                val ok = awaitGesture { cb -> service.performTap(x, y, cb) }
                onLog("操作结果(Tap)：${if (ok) "成功" else "失败"} @(${x.toInt()}, ${y.toInt()})")
                ok
            }

            "long_press", "longpress" -> {
                val point = parsePoint(step.args["point"] ?: "${step.args["x"]},${step.args["y"]}") ?: return false
                val (x, y) = toAbsolutePoint(point.first, point.second)
                val ok = awaitGesture { cb -> service.performLongPress(x, y, cb) }
                onLog("操作结果(LongPress)：${if (ok) "成功" else "失败"} @(${x.toInt()}, ${y.toInt()})")
                ok
            }

            "swipe" -> {
                val start = parsePoint(step.args["start"] ?: "${step.args["start_x"]},${step.args["start_y"]}") ?: return false
                val end = parsePoint(step.args["end"] ?: "${step.args["end_x"]},${step.args["end_y"]}") ?: return false
                val (sx, sy) = toAbsolutePoint(start.first, start.second)
                val (ex, ey) = toAbsolutePoint(end.first, end.second)
                val duration = step.args["duration_ms"]?.toLongOrNull()?.coerceIn(80L, 5000L) ?: 360L
                val ok = awaitGesture { cb ->
                    service.performSwipe(
                        startX = sx,
                        startY = sy,
                        endX = ex,
                        endY = ey,
                        durationMs = duration,
                        onDone = cb
                    )
                }
                onLog(
                    "操作结果(Swipe)：${if (ok) "成功" else "失败"} " +
                        "(${sx.toInt()},${sy.toInt()}) -> (${ex.toInt()},${ey.toInt()}) ${duration}ms"
                )
                ok
            }

            "type", "input" -> {
                val text = step.args["text"].orEmpty()
                inputText(service, text, onLog)
            }

            "tap_text" -> {
                val text = step.args["text"].orEmpty().trim()
                if (text.isBlank()) return false
                val node = service.findNodeByText(text)
                val ok = service.clickNode(node)
                onLog("操作结果(TapText)：${if (ok) "成功" else "失败"}（$text）")
                ok
            }

            else -> {
                onLog("操作结果(${step.action})：失败（未知动作）")
                false
            }
        }
    }

    private fun launchApp(
        target: String,
        onLog: (String) -> Unit
    ): Boolean {
        val trimmed = target.trim()
        if (trimmed.isBlank()) return false

        val candidates = resolveLaunchPackageCandidates(trimmed)
        if (candidates.isEmpty()) {
            onLog("无法解析应用：$trimmed")
            return false
        }

        val pm = context.packageManager
        for (packageName in candidates.distinct()) {
            val intent = pm.getLaunchIntentForPackage(packageName) ?: continue
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val launched =
                runCatching {
                    context.startActivity(intent)
                    true
                }.getOrDefault(false)
            if (launched) {
                onLog("已启动应用：$packageName")
                return true
            }
        }
        onLog("启动失败：$trimmed（候选：${candidates.joinToString()}）")
        return false
    }

    private fun resolveLaunchPackageCandidates(target: String): List<String> {
        val candidates = linkedSetOf<String>()
        if (target.contains(".")) {
            candidates += target
        } else {
            candidates += knownPackageAliases(target)
            candidates += resolvePackagesByAppName(target)
        }
        return candidates.toList()
    }

    private fun resolvePackagesByAppName(appName: String): List<String> {
        val pm = context.packageManager
        val launcherIntent =
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = queryLauncherActivities(pm, launcherIntent)
        if (list.isEmpty()) return emptyList()

        val normalizedTarget = normalizeAppName(appName)
        val exact =
            list.filter { item ->
                val label = item.loadLabel(pm)?.toString().orEmpty()
                label.equals(appName, ignoreCase = true) || normalizeAppName(label) == normalizedTarget
            }
            .mapNotNull { it.activityInfo?.packageName }
            .filter { it.isNotBlank() }
        if (exact.isNotEmpty()) return exact

        val fuzzy =
            list.filter { item ->
                val label = item.loadLabel(pm)?.toString().orEmpty()
                val normalizedLabel = normalizeAppName(label)
                label.contains(appName, ignoreCase = true) ||
                    appName.contains(label, ignoreCase = true) ||
                    normalizedLabel.contains(normalizedTarget) ||
                    normalizedTarget.contains(normalizedLabel)
            }
            .mapNotNull { it.activityInfo?.packageName }
            .filter { it.isNotBlank() }
        return fuzzy
    }

    private fun queryLauncherActivities(
        pm: PackageManager,
        intent: Intent
    ): List<android.content.pm.ResolveInfo> {
        val withAll = runCatching { pm.queryIntentActivities(intent, PackageManager.MATCH_ALL) }.getOrDefault(emptyList())
        if (withAll.isNotEmpty()) return withAll
        return runCatching { pm.queryIntentActivities(intent, 0) }.getOrDefault(emptyList())
    }

    private fun knownPackageAliases(appName: String): List<String> {
        val normalized = normalizeAppName(appName)
        return when {
            normalized.contains("抖音极速版") -> listOf("com.ss.android.ugc.aweme.lite")
            normalized.contains("抖音") || normalized.contains("douyin") -> listOf(
                "com.ss.android.ugc.aweme",
                "com.ss.android.ugc.aweme.lite",
                "com.ss.android.ugc.trill"
            )
            normalized == "微信" || normalized == "weixin" || normalized == "wechat" -> listOf("com.tencent.mm")
            normalized == "qq" -> listOf("com.tencent.mobileqq")
            normalized.contains("支付宝") || normalized == "alipay" -> listOf("com.eg.android.AlipayGphone")
            normalized.contains("小红书") || normalized == "xiaohongshu" || normalized == "red" -> listOf("com.xingin.xhs")
            normalized == "淘宝" || normalized == "taobao" -> listOf("com.taobao.taobao")
            normalized == "京东" || normalized == "jingdong" || normalized == "jd" -> listOf("com.jingdong.app.mall")
            normalized.contains("哔哩") || normalized == "bilibili" -> listOf("tv.danmaku.bili")
            else -> emptyList()
        }
    }

    private fun normalizeAppName(raw: String): String {
        return raw
            .trim()
            .lowercase()
            .replace(" ", "")
            .replace("　", "")
            .replace("-", "")
            .replace("_", "")
            .replace("·", "")
            .replace(".", "")
    }

    private fun inputText(
        service: AutoSoulAccessibilityService,
        text: String,
        onLog: (String) -> Unit
    ): Boolean {
        if (text.isBlank()) {
            onLog("操作结果(Input)：失败（输入内容为空）")
            return false
        }
        val root = service.rootInActiveWindow ?: run {
            onLog("操作结果(Input)：失败（未获取到当前窗口）")
            return false
        }
        val target = findEditableNode(root)
        if (target == null) {
            onLog("操作结果(Input)：失败（未找到可输入控件）")
            return false
        }

        runCatching { target.performAction(AccessibilityNodeInfo.ACTION_FOCUS) }
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val setTextOk = runCatching { target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) }.getOrDefault(false)
        if (setTextOk) {
            onLog("操作结果(Input)：成功（无障碍输入）")
            return true
        }
        val fallbackSetOk = runCatching { service.setText(target, text) }.getOrDefault(false)
        onLog("操作结果(Input)：${if (fallbackSetOk) "成功" else "失败"}（无障碍输入）")
        return fallbackSetOk
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
