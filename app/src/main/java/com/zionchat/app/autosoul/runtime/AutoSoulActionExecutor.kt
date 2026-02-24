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

class AutoSoulNonRetryableActionException(
    message: String
) : IllegalArgumentException(message)

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
        val rawAction = step.action.trim().lowercase().replace("-", "_").replace(" ", "_")
        val action = normalizeAction(rawAction)
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
                val target = resolveLaunchTarget(step.args)
                if (target.isNullOrBlank()) {
                    onLog("操作结果(Launch)：失败（缺少 app/package 参数）")
                    throw AutoSoulNonRetryableActionException("缺少 app/package 参数")
                }
                val launched = launchApp(target, onLog)
                if (launched) {
                    val settleMs = parseSettleDurationMs(step.args, defaultMs = 900L)
                    if (settleMs > 0L) {
                        Thread.sleep(settleMs)
                    }
                }
                launched
            }

            "tap" -> {
                val pointRaw =
                    firstNonBlank(
                        step.args["point"],
                        step.args["value"],
                        step.args["coord"],
                        step.args["coords"],
                        step.args["position"],
                        step.args["x"],
                        coordinatePairString(step.args, "x", "y")
                    )
                val point = parsePoint(pointRaw.orEmpty())
                if (point == null) {
                    onLog("操作结果(Tap)：失败（坐标解析失败：${pointRaw.orEmpty()}）")
                    throw AutoSoulNonRetryableActionException("Tap 坐标解析失败")
                }
                val (x, y) = toAbsolutePoint(service, point.first, point.second)
                var ok = awaitGesture { cb -> service.performTap(x, y, cb) }
                if (!ok) {
                    Thread.sleep(120L)
                    ok = awaitGesture { cb -> service.performTap(x, y, cb) }
                }
                onLog("操作结果(Tap)：${if (ok) "成功" else "失败"} @(${x.toInt()}, ${y.toInt()})")
                ok
            }

            "long_press", "longpress" -> {
                val pointRaw =
                    firstNonBlank(
                        step.args["point"],
                        step.args["value"],
                        step.args["coord"],
                        step.args["coords"],
                        step.args["position"],
                        step.args["x"],
                        coordinatePairString(step.args, "x", "y")
                    )
                val point = parsePoint(pointRaw.orEmpty())
                if (point == null) {
                    onLog("操作结果(LongPress)：失败（坐标解析失败：${pointRaw.orEmpty()}）")
                    throw AutoSoulNonRetryableActionException("LongPress 坐标解析失败")
                }
                val (x, y) = toAbsolutePoint(service, point.first, point.second)
                var ok = awaitGesture { cb -> service.performLongPress(x, y, cb) }
                if (!ok) {
                    Thread.sleep(120L)
                    ok = awaitGesture { cb -> service.performLongPress(x, y, cb) }
                }
                onLog("操作结果(LongPress)：${if (ok) "成功" else "失败"} @(${x.toInt()}, ${y.toInt()})")
                ok
            }

            "swipe" -> {
                val direction = resolveSwipeDirection(rawAction, step.args)
                val defaultSwipe = defaultSwipePoints(direction) ?: defaultSwipePoints("up")
                val startRaw =
                    firstNonBlank(
                        step.args["start"],
                        step.args["from"],
                        step.args["start_point"],
                        step.args["from_point"],
                        coordinatePairString(step.args, "start_x", "start_y"),
                        coordinatePairString(step.args, "x1", "y1"),
                        defaultSwipe?.first?.let { "${it.first},${it.second}" }
                    )
                val endRaw =
                    firstNonBlank(
                        step.args["end"],
                        step.args["to"],
                        step.args["end_point"],
                        step.args["to_point"],
                        coordinatePairString(step.args, "end_x", "end_y"),
                        coordinatePairString(step.args, "x2", "y2"),
                        defaultSwipe?.second?.let { "${it.first},${it.second}" }
                    )
                val start =
                    parseSwipePoint(
                        raw = startRaw.orEmpty(),
                        fallback = defaultSwipe?.first
                    )
                val end =
                    parseSwipePoint(
                        raw = endRaw.orEmpty(),
                        fallback = defaultSwipe?.second
                    )
                if (start == null || end == null) {
                    onLog(
                        "操作结果(Swipe)：失败（坐标解析失败 start=${startRaw.orEmpty()} end=${endRaw.orEmpty()}）"
                    )
                    throw AutoSoulNonRetryableActionException("Swipe 坐标解析失败")
                }
                val (sx, sy) = toAbsolutePoint(service, start.first, start.second)
                val (ex, ey) = toAbsolutePoint(service, end.first, end.second)
                val duration = parseSwipeDurationMs(step.args)
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
                if (text.isBlank()) {
                    throw AutoSoulNonRetryableActionException("TapText 缺少 text 参数")
                }
                val node = service.findNodeByText(text)
                val ok = service.clickNode(node)
                onLog("操作结果(TapText)：${if (ok) "成功" else "失败"}（$text）")
                ok
            }

            else -> {
                onLog("操作结果(${step.action})：失败（未知动作）")
                throw AutoSoulNonRetryableActionException("未知动作: ${step.action}")
            }
        }
    }

    private fun resolveLaunchTarget(args: Map<String, String>): String? {
        val explicit =
            firstNonBlank(
                args["package"],
                args["app"],
                args["app_name"],
                args["target"],
                args["name"]
            )
        if (!explicit.isNullOrBlank()) return explicit

        val loose = firstNonBlank(args["value"], args["text"])
        if (!loose.isNullOrBlank() && !looksLikeCoordinatePayload(loose)) {
            return loose
        }

        val nestedFromValues =
            args.values.asSequence()
                .mapNotNull { extractLaunchTargetFromNarrative(it) }
                .firstOrNull()
        if (!nestedFromValues.isNullOrBlank()) return nestedFromValues

        val nestedFromKeys =
            args.entries.asSequence()
                .firstOrNull { (key, _) ->
                    key.contains("app", ignoreCase = true) || key.contains("package", ignoreCase = true)
                }
                ?.value
                ?.trim()
        if (!nestedFromKeys.isNullOrBlank() && !looksLikeCoordinatePayload(nestedFromKeys)) {
            return nestedFromKeys
        }
        return null
    }

    private fun looksLikeCoordinatePayload(raw: String): Boolean {
        if (raw.isBlank()) return false
        if (parsePoint(raw) != null) return true
        val normalized = raw.trim()
        return Regex("""^[-+]?\d*\.?\d+%?\s*[,，]\s*[-+]?\d*\.?\d+%?$""").matches(normalized)
    }

    private fun extractLaunchTargetFromNarrative(raw: String): String? {
        val text = raw.trim()
        if (text.isBlank()) return null

        val quotedPattern =
            Regex(
                """(?i)(?:app(?:_name)?|package|pkg|target|name)\s*[:=]\s*["'“”]?([^"'”’,\s)\]\}；;]+)"""
            )
        quotedPattern.findAll(text).forEach { match ->
            val candidate = match.groupValues.getOrNull(1)?.trim().orEmpty()
            if (candidate.isNotBlank() && !looksLikeCoordinatePayload(candidate)) {
                return candidate
            }
        }

        val launchPipePattern =
            Regex(
                """(?i)\blaunch\b[^\n\r]{0,160}\b(?:app(?:_name)?|package|pkg)\s*[:=]\s*["'“”]?([^"'”’,\s)\]\}；;]+)"""
            )
        launchPipePattern.find(text)?.groupValues?.getOrNull(1)?.trim()?.let { candidate ->
            if (candidate.isNotBlank() && !looksLikeCoordinatePayload(candidate)) {
                return candidate
            }
        }

        return null
    }

    private fun normalizeAction(rawAction: String): String {
        return when (rawAction.trim().lowercase()) {
            "click", "touch", "press", "tap_screen" -> "tap"
            "longpress", "long_press", "long_tap", "longtap", "hold", "press_hold" -> "long_press"
            "input", "type_text", "enter_text", "text" -> "type"
            "taptext", "tap_text", "clicktext", "click_text" -> "tap_text"
            "scroll", "scrollup", "scroll_up", "scroll_down", "scrollleft", "scroll_left", "scrollright", "scroll_right",
            "swipe_up", "swipe_down", "swipe_left", "swipe_right" -> "swipe"
            else -> rawAction
        }
    }

    private fun firstNonBlank(vararg values: String?): String? {
        return values.firstOrNull { !it.isNullOrBlank() }?.trim()
    }

    private fun coordinatePairString(
        args: Map<String, String>,
        xKey: String,
        yKey: String
    ): String? {
        val x = args[xKey]?.trim().orEmpty()
        val y = args[yKey]?.trim().orEmpty()
        if (x.isBlank() || y.isBlank()) return null
        return "$x,$y"
    }

    private fun parseSwipeDurationMs(args: Map<String, String>): Long {
        args["duration_ms"]?.trim()?.toLongOrNull()?.let { return it.coerceIn(80L, 5000L) }
        val raw = args["duration"]?.trim().orEmpty()
        if (raw.isBlank()) return 360L
        val normalized = raw.lowercase()
        val millis =
            when {
                normalized.endsWith("ms") ->
                    normalized.removeSuffix("ms").trim().toDoubleOrNull()

                normalized.endsWith("s") ->
                    normalized.removeSuffix("s").trim().toDoubleOrNull()?.times(1000.0)

                else -> {
                    val parsed = normalized.toDoubleOrNull()
                    if (parsed == null) null else if (parsed > 12.0) parsed else parsed * 1000.0
                }
            } ?: 360.0
        return millis.toLong().coerceIn(80L, 5000L)
    }

    private fun resolveSwipeDirection(
        rawAction: String,
        args: Map<String, String>
    ): String? {
        val signal =
            listOf(rawAction, args["direction"], args["dir"], args["swipe_direction"])
                .joinToString(" ")
                .trim()
                .lowercase()
        return when {
            signal.contains("up") || signal.contains("上") -> "up"
            signal.contains("down") || signal.contains("下") -> "down"
            signal.contains("left") || signal.contains("左") -> "left"
            signal.contains("right") || signal.contains("右") -> "right"
            else -> null
        }
    }

    private fun defaultSwipePoints(direction: String?): Pair<Pair<Double, Double>, Pair<Double, Double>>? {
        return when (direction) {
            "up" -> (0.50 to 0.80) to (0.50 to 0.24)
            "down" -> (0.50 to 0.28) to (0.50 to 0.82)
            "left" -> (0.82 to 0.52) to (0.18 to 0.52)
            "right" -> (0.18 to 0.52) to (0.82 to 0.52)
            else -> null
        }
    }

    private fun parseSwipePoint(
        raw: String,
        fallback: Pair<Double, Double>?
    ): Pair<Double, Double>? {
        if (raw.isBlank()) return fallback
        parsePoint(raw)?.let { return it }
        extractDelimitedCoordinatePair(raw.trim())?.let { return it }
        val coordinates = extractCoordinateValues(raw)
        if (coordinates.size == 2) {
            return coordinates[0] to coordinates[1]
        }
        if (coordinates.size == 1 && fallback != null) {
            return coordinates[0] to fallback.second
        }
        return fallback
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
            throw AutoSoulNonRetryableActionException("Input 缺少 text 参数")
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

    private fun parseSettleDurationMs(
        args: Map<String, String>,
        defaultMs: Long
    ): Long {
        val raw =
            firstNonBlank(
                args["settle_ms"],
                args["settle"],
                args["wait_after_launch_ms"],
                args["wait_after_launch"]
            ) ?: return defaultMs
        val normalized = raw.trim().lowercase()
        val millis =
            when {
                normalized.endsWith("ms") ->
                    normalized.removeSuffix("ms").trim().toDoubleOrNull()

                normalized.endsWith("s") ->
                    normalized.removeSuffix("s").trim().toDoubleOrNull()?.times(1000.0)

                else -> {
                    val parsed = normalized.toDoubleOrNull()
                    if (parsed == null) null else if (parsed > 20.0) parsed else parsed * 1000.0
                }
            } ?: defaultMs.toDouble()
        return millis.toLong().coerceIn(0L, 5_000L)
    }

    private fun parsePoint(raw: String): Pair<Double, Double>? {
        val cleaned =
            raw.trim()
                .removePrefix("[")
                .removeSuffix("]")
                .removePrefix("(")
                .removeSuffix(")")
                .removePrefix("（")
                .removeSuffix("）")
                .removePrefix("【")
                .removeSuffix("】")
                .replace("，", ",")
                .replace("；", ",")
                .replace(";", ",")
                .replace("、", ",")
                .trim()
        if (cleaned.isBlank()) return null
        extractKeyedCoordinatePair(cleaned)?.let { return it }
        extractDelimitedCoordinatePair(cleaned)?.let { return it }
        val parts = cleaned.split(Regex("\\s+")).map { it.trim() }.filter { it.isNotBlank() }
        if (parts.size == 2) {
            val x = parseCoordinateValue(parts[0])
            val y = parseCoordinateValue(parts[1])
            if (x != null && y != null) return x to y
        }
        return null
    }

    private fun extractKeyedCoordinatePair(raw: String): Pair<Double, Double>? {
        val xRaw = Regex("""(?i)\bx\s*[:=]\s*([-+]?\d*\.?\d+%?)""").findAll(raw).lastOrNull()?.groupValues?.getOrNull(1)
        val yRaw = Regex("""(?i)\by\s*[:=]\s*([-+]?\d*\.?\d+%?)""").findAll(raw).lastOrNull()?.groupValues?.getOrNull(1)
        val x = xRaw?.let { parseCoordinateValue(it) }
        val y = yRaw?.let { parseCoordinateValue(it) }
        return if (x != null && y != null) x to y else null
    }

    private fun extractDelimitedCoordinatePair(raw: String): Pair<Double, Double>? {
        val pairPattern = Regex("""([-+]?\d*\.?\d+%?)\s*[,，]\s*([-+]?\d*\.?\d+%?)""")
        val match = pairPattern.findAll(raw).lastOrNull() ?: return null
        val x = parseCoordinateValue(match.groupValues.getOrNull(1).orEmpty())
        val y = parseCoordinateValue(match.groupValues.getOrNull(2).orEmpty())
        return if (x != null && y != null) x to y else null
    }

    private fun parseCoordinateValue(raw: String): Double? {
        val token =
            raw.trim()
                .replace(Regex("^[xyXY]\\s*[:=]\\s*"), "")
                .trim()
        if (token.isBlank()) return null
        if (token.endsWith("%")) {
            token.removeSuffix("%").trim().toDoubleOrNull()?.let { return it.div(100.0) }
        }
        token.toDoubleOrNull()?.let { return it }

        val numberPart = Regex("[-+]?\\d*\\.?\\d+%?").find(token)?.value?.trim().orEmpty()
        if (numberPart.isBlank()) return null
        if (numberPart.endsWith("%")) {
            return numberPart.removeSuffix("%").trim().toDoubleOrNull()?.div(100.0)
        }
        return numberPart.toDoubleOrNull()
    }

    private fun extractCoordinateValues(raw: String): List<Double> {
        if (raw.isBlank()) return emptyList()
        return Regex("[-+]?\\d*\\.?\\d+%?")
            .findAll(raw)
            .mapNotNull { parseCoordinateValue(it.value) }
            .toList()
    }

    private fun toAbsolutePoint(
        service: AutoSoulAccessibilityService,
        rawX: Double,
        rawY: Double
    ): Pair<Float, Float> {
        val dm = context.resources.displayMetrics
        val width = dm.widthPixels.toFloat().coerceAtLeast(2f)
        val height = dm.heightPixels.toFloat().coerceAtLeast(2f)
        val percentMode = rawX in 1.0..100.0 && rawY in 1.0..100.0

        val absX =
            when {
                rawX in 0.0..1.0 -> (rawX * width).toFloat()
                percentMode -> ((rawX / 100.0) * width).toFloat()
                else -> rawX.toFloat()
            }
        val absY =
            when {
                rawY in 0.0..1.0 -> (rawY * height).toFloat()
                percentMode -> ((rawY / 100.0) * height).toFloat()
                else -> rawY.toFloat()
            }

        val minX = 1f
        val maxX = (width - 1f).coerceAtLeast(1f)
        val minY = 1f
        val maxY = (height - 1f).coerceAtLeast(1f)
        return absX.coerceIn(minX, maxX) to absY.coerceIn(minY, maxY)
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
