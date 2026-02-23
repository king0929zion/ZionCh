package com.zionchat.app.autosoul.runtime

import org.json.JSONArray
import org.json.JSONObject

data class AutoSoulScriptStep(
    val action: String,
    val args: Map<String, String>
)

object AutoSoulScriptParser {
    fun parse(raw: String): Result<List<AutoSoulScriptStep>> {
        return runCatching {
            val trimmed = raw.trim()
            if (trimmed.isBlank()) error("脚本为空")

            val array =
                when {
                    trimmed.startsWith("{") -> {
                        val json = JSONObject(trimmed)
                        json.optJSONArray("steps") ?: error("脚本对象缺少 steps 数组")
                    }

                    trimmed.startsWith("[") -> JSONArray(trimmed)
                    else -> error("脚本必须是 JSON 对象或数组")
                }

            val steps = mutableListOf<AutoSoulScriptStep>()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: error("第 ${index + 1} 步不是对象")
                val action = item.optString("action").trim()
                if (action.isBlank()) error("第 ${index + 1} 步缺少 action")

                val args = linkedMapOf<String, String>()
                val argsObject = item.optJSONObject("args")
                if (argsObject != null) {
                    argsObject.keys().forEach { key ->
                        args[key] = argsObject.opt(key)?.toString()?.trim().orEmpty()
                    }
                }
                item.keys().forEach { key ->
                    if (key != "action" && key != "args") {
                        args[key] = item.opt(key)?.toString()?.trim().orEmpty()
                    }
                }
                steps += AutoSoulScriptStep(action = action, args = args)
            }

            if (steps.isEmpty()) error("脚本没有可执行步骤")
            steps
        }
    }
}

