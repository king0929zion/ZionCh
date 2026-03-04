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
        fun schema(
            name: String,
            desc: String,
            args: Map<String, String> = emptyMap()
        ): JsonObject {
            val argsObj = JsonObject().apply {
                args.forEach { (key, value) ->
                    addProperty(key, value)
                }
            }
            return JsonObject().apply {
                addProperty("name", name)
                addProperty("desc", desc)
                add("args", argsObj)
            }
        }

        val tools = JsonArray().apply {
            add("repo.list_tree")
            add("repo.list_dir")
            add("repo.read_file")
            add("repo.search")
            add("repo.get_file_meta")
            add("repo.list_branches")
            add("repo.create_branch")
            add("repo.apply_patch")
            add("repo.replace_range")
            add("repo.commit_push")
            add("repo.create_pr")
            add("repo.comment_pr")
            add("repo.merge_pr")
            add("actions.trigger_workflow")
            add("actions.get_run")
            add("actions.get_latest_run")
            add("actions.get_logs_summary")
            add("actions.list_artifacts")
            add("actions.download_artifact")
            add("pages.get_settings")
            add("pages.enable")
            add("pages.set_source")
            add("pages.get_deployments")
            add("pages.get_latest_url")
            add("pages.deploy")
            add("mcp.list_servers")
            add("mcp.list_tools")
            add("mcp.call_tool")
            add("policy.get_toolspec")
            add("policy.check_risk")
        }
        val toolSchemas = JsonArray().apply {
            add(
                schema(
                    name = "repo.list_tree",
                    desc = "读取仓库文件树（空仓库将自动初始化首个提交后重试）",
                    args = mapOf(
                        "ref" to "可选 string，默认 workspace.defaultBranch"
                    )
                )
            )
            add(
                schema(
                    name = "repo.list_dir",
                    desc = "按目录列出子项（空仓库将自动初始化首个提交后重试）",
                    args = mapOf(
                        "ref" to "可选 string，默认 workspace.defaultBranch",
                        "path" to "可选 string，默认空表示根目录"
                    )
                )
            )
            add(
                schema(
                    name = "repo.read_file",
                    desc = "读取文件内容",
                    args = mapOf(
                        "path" to "必填 string",
                        "ref" to "可选 string，默认 workspace.defaultBranch"
                    )
                )
            )
            add(
                schema(
                    name = "repo.search",
                    desc = "在仓库内搜索关键字",
                    args = mapOf(
                        "keyword" to "必填 string",
                        "per_page" to "可选 int，默认 20（1-100）"
                    )
                )
            )
            add(
                schema(
                    name = "repo.get_file_meta",
                    desc = "获取文件元信息（sha/size/type）",
                    args = mapOf(
                        "path" to "必填 string",
                        "ref" to "可选 string，默认 workspace.defaultBranch"
                    )
                )
            )
            add(
                schema(
                    name = "repo.list_branches",
                    desc = "读取分支列表",
                    args = mapOf(
                        "per_page" to "可选 int，默认 100（1-100）"
                    )
                )
            )
            add(
                schema(
                    name = "repo.create_branch",
                    desc = "创建分支（branch 可省略自动生成；空仓库将自动初始化首个提交）",
                    args = mapOf(
                        "branch" to "可选 string，默认自动生成 ai/task-<timestamp>",
                        "base" to "可选 string，默认 workspace.defaultBranch"
                    )
                )
            )
            add(
                schema(
                    name = "repo.apply_patch",
                    desc = "应用 unified diff 补丁到会话暂存区",
                    args = mapOf(
                        "patch" to "必填 string（unified diff）",
                        "ref" to "可选 string，默认 workspace.defaultBranch"
                    )
                )
            )
            add(
                schema(
                    name = "repo.replace_range",
                    desc = "按行号范围替换文件内容到会话暂存区",
                    args = mapOf(
                        "path" to "必填 string",
                        "start_line" to "必填 int（1-based）",
                        "end_line" to "必填 int（1-based）",
                        "replacement" to "可选 string，默认空",
                        "ref" to "可选 string，默认 workspace.defaultBranch"
                    )
                )
            )
            add(
                schema(
                    name = "repo.commit_push",
                    desc = "提交并推送会话暂存区变更（自动使用/创建 ai 分支）",
                    args = mapOf(
                        "message" to "可选 string，默认 ZiCode update"
                    )
                )
            )
            add(
                schema(
                    name = "repo.create_pr",
                    desc = "创建 Pull Request",
                    args = mapOf(
                        "head" to "必填 string（分支名）",
                        "base" to "可选 string，默认 workspace.defaultBranch",
                        "title" to "必填 string",
                        "body" to "可选 string"
                    )
                )
            )
            add(
                schema(
                    name = "repo.comment_pr",
                    desc = "在 PR 中追加评论",
                    args = mapOf(
                        "pr_number" to "必填 int",
                        "body" to "必填 string"
                    )
                )
            )
            add(
                schema(
                    name = "repo.merge_pr",
                    desc = "合并 Pull Request",
                    args = mapOf(
                        "pr_number" to "必填 int",
                        "merge_method" to "可选 string（squash|merge|rebase），默认 squash"
                    )
                )
            )
            add(
                schema(
                    name = "actions.trigger_workflow",
                    desc = "触发 workflow_dispatch",
                    args = mapOf(
                        "workflow" to "必填 string（如 android_build.yml）",
                        "ref" to "可选 string，默认 workspace.defaultBranch",
                        "inputs" to "可选 object"
                    )
                )
            )
            add(
                schema(
                    name = "actions.get_run",
                    desc = "查询工作流运行状态",
                    args = mapOf(
                        "run_id" to "必填 long"
                    )
                )
            )
            add(
                schema(
                    name = "actions.get_latest_run",
                    desc = "获取指定 workflow 的最新 run",
                    args = mapOf(
                        "workflow" to "可选 string",
                        "branch" to "可选 string"
                    )
                )
            )
            add(
                schema(
                    name = "actions.get_logs_summary",
                    desc = "提取失败日志摘要与 report.json（若可推断）",
                    args = mapOf(
                        "run_id" to "必填 long"
                    )
                )
            )
            add(
                schema(
                    name = "actions.list_artifacts",
                    desc = "列出工作流产物",
                    args = mapOf(
                        "run_id" to "必填 long"
                    )
                )
            )
            add(
                schema(
                    name = "actions.download_artifact",
                    desc = "获取产物下载信息",
                    args = mapOf(
                        "artifact_id" to "必填 long"
                    )
                )
            )
            add(
                schema(
                    name = "pages.get_settings",
                    desc = "读取 Pages 配置"
                )
            )
            add(
                schema(
                    name = "pages.enable",
                    desc = "启用 GitHub Pages",
                    args = mapOf(
                        "branch" to "可选 string，默认 workspace.defaultBranch",
                        "path" to "可选 string，默认 /"
                    )
                )
            )
            add(
                schema(
                    name = "pages.set_source",
                    desc = "设置 Pages 构建来源",
                    args = mapOf(
                        "branch" to "可选 string，默认 workspace.defaultBranch",
                        "path" to "可选 string，默认 /"
                    )
                )
            )
            add(
                schema(
                    name = "pages.get_deployments",
                    desc = "获取 Pages 部署记录"
                )
            )
            add(
                schema(
                    name = "pages.get_latest_url",
                    desc = "获取 Pages 最新在线地址"
                )
            )
            add(
                schema(
                    name = "pages.deploy",
                    desc = "触发 Pages 部署"
                )
            )
            add(
                schema(
                    name = "mcp.list_servers",
                    desc = "获取 MCP 服务列表",
                    args = mapOf(
                        "include_disabled" to "可选 boolean，默认 false"
                    )
                )
            )
            add(
                schema(
                    name = "mcp.list_tools",
                    desc = "获取 MCP 工具列表",
                    args = mapOf(
                        "server_id" to "可选 string",
                        "server_name" to "可选 string",
                        "use_cached" to "可选 boolean，默认 true"
                    )
                )
            )
            add(
                schema(
                    name = "mcp.call_tool",
                    desc = "调用 MCP 工具",
                    args = mapOf(
                        "server_id" to "可选 string",
                        "server_name" to "可选 string",
                        "tool_name" to "必填 string",
                        "arguments" to "可选 object"
                    )
                )
            )
            add(
                schema(
                    name = "policy.get_toolspec",
                    desc = "读取工具能力说明"
                )
            )
            add(
                schema(
                    name = "policy.check_risk",
                    desc = "对补丁进行风险分级",
                    args = mapOf(
                        "patch" to "必填 string（unified diff）",
                        "touched_paths" to "可选 array<string>"
                    )
                )
            )
        }
        val constraints = JsonArray().apply {
            add("ZiCode 模块禁止本地 shell 执行")
            add("高风险改动默认不自动合并")
            add("每轮任务需先创建 ai/<task-id> 分支并通过 PR 提交")
            add("构建失败需输出/解析 sandbox/report.json")
        }
        return JsonObject().apply {
            add("tools", tools)
            add("tool_schemas", toolSchemas)
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
