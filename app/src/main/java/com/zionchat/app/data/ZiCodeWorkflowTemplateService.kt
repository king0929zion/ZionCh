package com.zionchat.app.data

import com.google.gson.Gson
import com.google.gson.JsonObject

data class ZiCodeWorkflowTemplateSpec(
    val path: String,
    val content: String
)

data class ZiCodeWorkflowInitResult(
    val success: Boolean,
    val message: String,
    val initializedFiles: List<String> = emptyList(),
    val pullRequestUrl: String? = null
)

class ZiCodeWorkflowTemplateService(
    private val toolDispatcher: ZiCodeToolDispatcher,
    private val gson: Gson = Gson()
) {

    suspend fun ensureWorkflowTemplates(
        sessionId: String,
        workspace: ZiCodeWorkspace,
        settings: ZiCodeSettings,
        baseBranch: String = workspace.defaultBranch
    ): ZiCodeWorkflowInitResult {
        val templates = buildWorkflowTemplates()
        val missing = mutableListOf<ZiCodeWorkflowTemplateSpec>()

        templates.forEach { template ->
            val metaResult =
                toolDispatcher.dispatch(
                    sessionId = sessionId,
                    workspace = workspace,
                    settings = settings,
                    toolName = "repo.get_file_meta",
                    argsJson = gson.toJson(
                        JsonObject().apply {
                            addProperty("path", template.path)
                            addProperty("ref", baseBranch)
                        }
                    )
                )
            if (!metaResult.success) {
                missing += template
            }
        }

        if (missing.isEmpty()) {
            return ZiCodeWorkflowInitResult(success = true, message = "工作流模板已存在，无需初始化")
        }

        missing.forEach { template ->
            val patch = buildAddFilePatch(template.path, template.content)
            val applyResult =
                toolDispatcher.dispatch(
                    sessionId = sessionId,
                    workspace = workspace,
                    settings = settings,
                    toolName = "repo.apply_patch",
                    argsJson = gson.toJson(JsonObject().apply { addProperty("patch", patch) })
                )
            if (!applyResult.success) {
                return ZiCodeWorkflowInitResult(
                    success = false,
                    message = applyResult.error ?: "初始化工作流补丁失败：${template.path}"
                )
            }
        }

        val commitResult =
            toolDispatcher.dispatch(
                sessionId = sessionId,
                workspace = workspace,
                settings = settings,
                toolName = "repo.commit_push",
                argsJson = gson.toJson(
                    JsonObject().apply {
                        addProperty("message", "ZiCode: initialize workflow templates")
                    }
                )
            )
        if (!commitResult.success) {
            return ZiCodeWorkflowInitResult(success = false, message = commitResult.error ?: "提交工作流模板失败")
        }

        val commitObj = parseJson(commitResult.resultJson)
        val branch = commitObj.get("branch")?.asString.orEmpty().ifBlank { "ai/workflow-init" }

        val prResult =
            toolDispatcher.dispatch(
                sessionId = sessionId,
                workspace = workspace,
                settings = settings,
                toolName = "repo.create_pr",
                argsJson = gson.toJson(
                    JsonObject().apply {
                        addProperty("head", branch)
                        addProperty("base", baseBranch)
                        addProperty("title", "ZiCode: initialize workflow templates")
                        addProperty("body", "初始化 lint/test/web/pages/android/release 六个工作流模板，并统一产出 sandbox/report.json")
                    }
                )
            )
        if (!prResult.success) {
            return ZiCodeWorkflowInitResult(success = false, message = prResult.error ?: "创建 PR 失败")
        }

        val prObj = parseJson(prResult.resultJson)
        return ZiCodeWorkflowInitResult(
            success = true,
            message = "工作流模板初始化完成，已创建 PR",
            initializedFiles = missing.map { it.path },
            pullRequestUrl = prObj.get("html_url")?.asString
        )
    }

    private fun parseJson(raw: String?): JsonObject {
        val text = raw?.trim().orEmpty().ifBlank { "{}" }
        return runCatching { gson.fromJson(text, JsonObject::class.java) }.getOrElse { JsonObject() }
    }

    private fun buildAddFilePatch(path: String, content: String): String {
        val body = content.replace("\r\n", "\n").split("\n").joinToString("\n") { "+$it" }
        return buildString {
            append("diff --git a/$path b/$path\n")
            append("new file mode 100644\n")
            append("--- /dev/null\n")
            append("+++ b/$path\n")
            append("@@ -0,0 +1,${content.replace("\r\n", "\n").split("\n").size} @@\n")
            append(body)
            append("\n")
        }
    }

    private fun buildWorkflowTemplates(): List<ZiCodeWorkflowTemplateSpec> {
        return listOf(
            ZiCodeWorkflowTemplateSpec(".github/workflows/lint.yml", lintWorkflow()),
            ZiCodeWorkflowTemplateSpec(".github/workflows/test.yml", testWorkflow()),
            ZiCodeWorkflowTemplateSpec(".github/workflows/web_build.yml", webBuildWorkflow()),
            ZiCodeWorkflowTemplateSpec(".github/workflows/pages_build_deploy.yml", pagesWorkflow()),
            ZiCodeWorkflowTemplateSpec(".github/workflows/android_build.yml", androidBuildWorkflow()),
            ZiCodeWorkflowTemplateSpec(".github/workflows/release.yml", releaseWorkflow())
        )
    }

    private fun lintWorkflow(): String = """
name: lint
on:
  workflow_dispatch:
  push:
    branches: [main]
jobs:
  lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - run: ./gradlew lint
      - run: |
          mkdir -p sandbox
          cat > sandbox/report.json <<'JSON'
          {"status":"success","summary":"lint completed","failing_step":null,"error_summary":null,"file_hints":[],"next_reads":[],"artifacts":[],"pages_url":null,"deployment_status":null}
          JSON
      - uses: actions/upload-artifact@v4
        with:
          name: sandbox-report
          path: sandbox/report.json
""".trimIndent()

    private fun testWorkflow(): String = """
name: test
on:
  workflow_dispatch:
  push:
    branches: [main]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - run: ./gradlew test
      - run: |
          mkdir -p sandbox
          cat > sandbox/report.json <<'JSON'
          {"status":"success","summary":"test completed","failing_step":null,"error_summary":null,"file_hints":[],"next_reads":[],"artifacts":[],"pages_url":null,"deployment_status":null}
          JSON
      - uses: actions/upload-artifact@v4
        with:
          name: sandbox-report
          path: sandbox/report.json
""".trimIndent()

    private fun webBuildWorkflow(): String = """
name: web_build
on:
  workflow_dispatch:
jobs:
  web-build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '20'
      - run: npm ci
      - run: npm run build --if-present
      - run: |
          mkdir -p sandbox
          cat > sandbox/report.json <<'JSON'
          {"status":"success","summary":"web build completed","failing_step":null,"error_summary":null,"file_hints":[],"next_reads":[],"artifacts":["web-build"],"pages_url":null,"deployment_status":null}
          JSON
      - uses: actions/upload-artifact@v4
        with:
          name: sandbox-report
          path: sandbox/report.json
""".trimIndent()

    private fun pagesWorkflow(): String = """
name: pages_build_deploy
on:
  workflow_dispatch:
permissions:
  contents: read
  pages: write
  id-token: write
jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/configure-pages@v5
      - uses: actions/upload-pages-artifact@v3
        with:
          path: .
      - id: deploy
        uses: actions/deploy-pages@v4
      - run: |
          mkdir -p sandbox
          cat > sandbox/report.json <<'JSON'
          {"status":"success","summary":"pages deployed","failing_step":null,"error_summary":null,"file_hints":[],"next_reads":[],"artifacts":[],"pages_url":"${'$'}{{ steps.deploy.outputs.page_url }}","deployment_status":"deployed"}
          JSON
      - uses: actions/upload-artifact@v4
        with:
          name: sandbox-report
          path: sandbox/report.json
""".trimIndent()

    private fun androidBuildWorkflow(): String = """
name: android_build
on:
  workflow_dispatch:
jobs:
  android:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - run: ./gradlew :app:assembleDebug
      - run: |
          mkdir -p sandbox
          cat > sandbox/report.json <<'JSON'
          {"status":"success","summary":"android debug apk built","failing_step":null,"error_summary":null,"file_hints":[],"next_reads":[],"artifacts":["debug-apk"],"pages_url":null,"deployment_status":null}
          JSON
      - uses: actions/upload-artifact@v4
        with:
          name: sandbox-report
          path: sandbox/report.json
""".trimIndent()

    private fun releaseWorkflow(): String = """
name: release
on:
  workflow_dispatch:
    inputs:
      version:
        required: true
        description: release version
jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: |
          mkdir -p sandbox
          cat > sandbox/report.json <<'JSON'
          {"status":"pending","summary":"release workflow prepared, requires manual approval/signing","failing_step":null,"error_summary":null,"file_hints":[],"next_reads":[],"artifacts":[],"pages_url":null,"deployment_status":"pending"}
          JSON
      - uses: actions/upload-artifact@v4
        with:
          name: sandbox-report
          path: sandbox/report.json
""".trimIndent()
}
