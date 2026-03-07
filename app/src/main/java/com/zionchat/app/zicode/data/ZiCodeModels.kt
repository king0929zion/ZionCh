package com.zionchat.app.zicode.data

import java.util.UUID

enum class ZiCodeRunStatus {
    QUEUED,
    RUNNING,
    SUCCESS,
    FAILED
}

enum class ZiCodeToolStatus {
    QUEUED,
    RUNNING,
    SUCCESS,
    FAILED
}

data class ZiCodeViewer(
    val login: String,
    val displayName: String? = null,
    val avatarUrl: String? = null
)

data class ZiCodeSettings(
    val githubToken: String = "",
    val viewer: ZiCodeViewer? = null,
    val lastValidatedAt: Long? = null
)

data class ZiCodeRemoteRepo(
    val id: Long = 0L,
    val owner: String,
    val name: String,
    val fullName: String,
    val description: String = "",
    val privateRepo: Boolean = false,
    val defaultBranch: String = "main",
    val htmlUrl: String = "",
    val homepageUrl: String? = null,
    val pushedAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class ZiCodeRecentRepo(
    val owner: String,
    val name: String,
    val description: String = "",
    val privateRepo: Boolean = false,
    val defaultBranch: String = "main",
    val htmlUrl: String = "",
    val homepageUrl: String? = null,
    val pushedAt: Long = 0L,
    val updatedAt: Long = 0L,
    val lastAccessedAt: Long = 0L
)

data class ZiCodeToolCallState(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val toolName: String,
    val group: String = "",
    val status: ZiCodeToolStatus = ZiCodeToolStatus.QUEUED,
    val summary: String = "",
    val inputSummary: String = "",
    val detailLog: String = "",
    val resultSummary: String = "",
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null
)

data class ZiCodeTurn(
    val id: String = UUID.randomUUID().toString(),
    val prompt: String,
    val response: String = "",
    val status: ZiCodeRunStatus = ZiCodeRunStatus.QUEUED,
    val toolCalls: List<ZiCodeToolCallState> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val resultLink: String? = null,
    val resultLabel: String? = null,
    val failureMessage: String? = null
)

data class ZiCodeSession(
    val id: String = UUID.randomUUID().toString(),
    val repoOwner: String,
    val repoName: String,
    val title: String = "",
    val turns: List<ZiCodeTurn> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class ZiCodeRepoNode(
    val name: String,
    val path: String,
    val type: String,
    val size: Long? = null,
    val sha: String? = null,
    val downloadUrl: String? = null
)

data class ZiCodeFilePreview(
    val path: String,
    val content: String,
    val size: Long = content.length.toLong(),
    val truncated: Boolean = false
)

data class ZiCodeGitHubBranch(
    val name: String,
    val sha: String,
    val protectedBranch: Boolean = false
)

data class ZiCodeGitHubCommit(
    val sha: String,
    val message: String,
    val authorName: String? = null,
    val htmlUrl: String? = null,
    val committedAt: Long = 0L
)

data class ZiCodeGitHubWorkflow(
    val id: Long,
    val name: String,
    val path: String,
    val state: String = ""
)

data class ZiCodeGitHubWorkflowRun(
    val id: Long,
    val name: String,
    val status: String,
    val conclusion: String? = null,
    val htmlUrl: String = "",
    val branch: String? = null,
    val event: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class ZiCodeGitHubRelease(
    val id: Long,
    val name: String,
    val tagName: String,
    val htmlUrl: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val publishedAt: Long = 0L
)

data class ZiCodeGitHubIssue(
    val id: Long,
    val number: Int,
    val title: String,
    val state: String,
    val htmlUrl: String = "",
    val bodyPreview: String? = null,
    val authorLogin: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class ZiCodeGitHubPullRequest(
    val id: Long,
    val number: Int,
    val title: String,
    val state: String,
    val htmlUrl: String = "",
    val draft: Boolean = false,
    val headRef: String? = null,
    val baseRef: String? = null,
    val authorLogin: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class ZiCodeGitHubArtifact(
    val id: Long,
    val name: String,
    val sizeInBytes: Long = 0L,
    val expired: Boolean = false,
    val downloadUrl: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class ZiCodeGitHubPagesInfo(
    val status: String,
    val htmlUrl: String? = null,
    val sourceBranch: String? = null,
    val sourcePath: String? = null
)

data class ZiCodeToolCapability(
    val group: String,
    val title: String,
    val description: String,
    val active: Boolean = true
)

fun buildZiCodeToolCapabilities(useChinese: Boolean = false): List<ZiCodeToolCapability> {
    fun text(zh: String, en: String): String = if (useChinese) zh else en

    return listOf(
        ZiCodeToolCapability(text("GitHub", "GitHub"), text("账号身份", "Account identity"), text("校验当前 GitHub Token，并读取连接账号资料。", "Validate the current GitHub token and read the connected account profile.")),
        ZiCodeToolCapability(text("GitHub", "GitHub"), text("仓库列表", "Repository listing"), text("读取当前 GitHub 账号可访问的仓库列表。", "Read repositories available to the connected GitHub account.")),
        ZiCodeToolCapability(text("GitHub", "GitHub"), text("仓库详情", "Repository detail"), text("在执行前读取当前仓库的核心元数据。", "Inspect the selected repository before execution starts.")),
        ZiCodeToolCapability(text("GitHub", "GitHub"), text("新建仓库", "Repository creation"), text("直接创建新的 GitHub 仓库并进入项目会话。", "Create a new repository and open it as a project chat.")),
        ZiCodeToolCapability(text("文件", "Contents"), text("目录树浏览", "Tree browsing"), text("查看多层目录结构，并在底部面板预览文件。", "Read nested directories and preview files in a bottom sheet.")),
        ZiCodeToolCapability(text("文件", "Contents"), text("文件预览", "File preview"), text("读取仓库文件内容，并在 ZiCode 中直接展示。", "Open repository files and render decoded content inside ZiCode.")),
        ZiCodeToolCapability(text("文件", "Contents"), text("文件写入", "File write API"), text("通过 GitHub Contents API 创建、更新和删除文件。", "Create, update, and delete repository files through GitHub Contents API.")),
        ZiCodeToolCapability(text("分支", "Branches"), text("分支列表", "Branch listing"), text("在执行修改前读取当前可用分支。", "Inspect available branches before deciding where to run changes.")),
        ZiCodeToolCapability(text("分支", "Branches"), text("新建分支", "Branch creation"), text("在任务明确需要隔离时创建新的工作分支。", "Create a new working branch when the task needs an isolated path.")),
        ZiCodeToolCapability(text("提交", "Commits"), text("提交历史", "Commit history"), text("读取最近提交，判断项目当前推进状态。", "Read recent commits to understand current project momentum.")),
        ZiCodeToolCapability(text("问题", "Issues"), text("Issue 列表", "Issue listing"), text("读取当前仓库的 Issues，用来理解缺陷与待办。", "Read repository issues to understand bugs and planned work.")),
        ZiCodeToolCapability(text("问题", "Issues"), text("创建 Issue", "Issue creation"), text("在需要记录任务、缺陷或待办时创建新的 Issue。", "Create a new issue when work needs to be tracked on GitHub.")),
        ZiCodeToolCapability(text("拉取请求", "Pull Requests"), text("PR 列表", "Pull request listing"), text("读取当前打开的 Pull Requests，了解现有变更流。", "Read open pull requests to understand in-flight repository changes.")),
        ZiCodeToolCapability(text("拉取请求", "Pull Requests"), text("创建 PR", "Pull request creation"), text("在已有工作分支时创建新的 Pull Request。", "Create a pull request from a working branch when review is needed.")),
        ZiCodeToolCapability(text("Actions", "Actions"), text("工作流扫描", "Workflow scan"), text("读取 Actions 工作流、运行记录和步骤明细。", "Read workflows, runs, and step traces for CI or deployment checks.")),
        ZiCodeToolCapability(text("Actions", "Actions"), text("触发工作流", "Workflow dispatch"), text("在项目会话中直接触发 workflow_dispatch 工作流。", "Trigger workflow dispatch jobs directly inside the project session.")),
        ZiCodeToolCapability(text("Actions", "Actions"), text("运行控制", "Run control"), text("读取运行状态、步骤日志，并支持重跑或取消。", "Inspect workflow runs, read step traces, rerun jobs, or cancel active runs.")),
        ZiCodeToolCapability(text("产物", "Artifacts"), text("构建产物", "Artifacts"), text("读取最近 workflow run 的构建产物和下载入口。", "Inspect workflow artifacts and their download links from recent runs.")),
        ZiCodeToolCapability(text("发布", "Release"), text("发布历史", "Release history"), text("读取 Release 历史并整理发版上下文。", "Read releases and prepare release context from GitHub.")),
        ZiCodeToolCapability(text("发布", "Release"), text("创建 Release", "Release creation"), text("在仓库准备完成后直接创建 GitHub Release。", "Create a GitHub release when the repository is ready to ship.")),
        ZiCodeToolCapability(text("Pages", "Pages"), text("Pages 状态", "Pages status"), text("检查 GitHub Pages 是否启用，以及来源分支和路径。", "Inspect GitHub Pages status and source branch configuration."))
    )
}

fun buildZiCodeRepoKey(owner: String, name: String): String {
    return "${owner.trim().lowercase()}/${name.trim().lowercase()}"
}

fun ZiCodeRemoteRepo.toRecentRepo(lastAccessedAt: Long = 0L): ZiCodeRecentRepo {
    return ZiCodeRecentRepo(
        owner = owner,
        name = name,
        description = description,
        privateRepo = privateRepo,
        defaultBranch = defaultBranch,
        htmlUrl = htmlUrl,
        homepageUrl = homepageUrl,
        pushedAt = pushedAt,
        updatedAt = updatedAt,
        lastAccessedAt = lastAccessedAt
    )
}
