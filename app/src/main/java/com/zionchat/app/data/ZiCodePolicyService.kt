package com.zionchat.app.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject

data class ZiCodeRiskReport(
    val level: String,
    val reasons: List<String>,
    val changedFiles: Int,
    val changedLines: Int
)

interface ZiCodePolicyService {
    fun getToolspec(): JsonObject

    fun checkRisk(
        patchText: String,
        touchedPaths: List<String> = emptyList()
    ): ZiCodeRiskReport

    fun isLocalShellTool(toolName: String): Boolean
}

class DefaultZiCodePolicyService : ZiCodePolicyService {

    private val forbiddenLocalTools = setOf(
        "shell",
        "local_exec",
        "terminal.exec",
        "bash.exec",
        "powershell.exec"
    )

    override fun getToolspec(): JsonObject {
        val tools = JsonArray().apply {
            add("repo.list_tree")
            add("repo.list_dir")
            add("repo.read_file")
            add("repo.search")
            add("repo.get_file_meta")
            add("repo.create_branch")
            add("repo.apply_patch")
            add("repo.replace_range")
            add("repo.commit_push")
            add("repo.create_pr")
            add("repo.comment_pr")
            add("repo.merge_pr")
            add("actions.trigger_workflow")
            add("actions.get_run")
            add("actions.get_logs_summary")
            add("actions.list_artifacts")
            add("actions.download_artifact")
            add("pages.get_settings")
            add("pages.enable")
            add("pages.set_source")
            add("pages.get_deployments")
            add("pages.get_latest_url")
            add("pages.deploy")
            add("policy.get_toolspec")
            add("policy.check_risk")
        }
        val constraints = JsonArray().apply {
            add("ZiCode 模块禁止本地 shell 执行")
            add("高风险改动默认不自动合并")
            add("每轮任务需先创建 ai/<task-id> 分支并通过 PR 提交")
            add("构建失败需输出/解析 sandbox/report.json")
        }
        return JsonObject().apply {
            add("tools", tools)
            add("constraints", constraints)
            addProperty("default_merge_policy", "manual_confirmation_required")
        }
    }

    override fun checkRisk(
        patchText: String,
        touchedPaths: List<String>
    ): ZiCodeRiskReport {
        val normalizedPatch = patchText.trim()
        val lines = normalizedPatch.lineSequence().toList()
        val changedLines = lines.count { it.startsWith("+") || it.startsWith("-") }
        val changedFiles =
            lines.count { it.startsWith("diff --git ") }
                .takeIf { it > 0 }
                ?: touchedPaths.distinct().size

        val reasons = mutableListOf<String>()
        val lowerPatch = normalizedPatch.lowercase()
        val lowerPaths = touchedPaths.map { it.lowercase() }

        val highRiskPathKeywords = listOf(
            ".github/workflows",
            "signing",
            "keystore",
            "gradle.properties",
            "release.yml"
        )
        val highRiskMatched =
            highRiskPathKeywords.any { keyword ->
                lowerPatch.contains(keyword) || lowerPaths.any { it.contains(keyword) }
            }
        if (highRiskMatched) {
            reasons += "涉及发布/签名/工作流权限相关文件"
        }

        if (lowerPatch.contains("permissions:") && lowerPatch.contains("workflow")) {
            reasons += "检测到 workflow 权限字段变更"
        }

        if (changedFiles >= 12) {
            reasons += "单次改动文件数较大（$changedFiles）"
        }
        if (changedLines >= 500) {
            reasons += "单次改动行数较大（$changedLines）"
        }

        val level =
            when {
                reasons.any { it.contains("发布") || it.contains("权限") } -> "high"
                changedFiles >= 8 || changedLines >= 260 -> "medium"
                else -> "low"
            }
        return ZiCodeRiskReport(
            level = level,
            reasons = reasons.ifEmpty { listOf("未命中高风险规则") },
            changedFiles = changedFiles,
            changedLines = changedLines
        )
    }

    override fun isLocalShellTool(toolName: String): Boolean {
        val normalized = toolName.trim().lowercase()
        return forbiddenLocalTools.contains(normalized) ||
            normalized.contains("shell") ||
            normalized.contains("local_exec")
    }
}
