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

fun buildZiCodeToolCapabilities(): List<ZiCodeToolCapability> {
    return listOf(
        ZiCodeToolCapability("GitHub", "Account identity", "Validate the current GitHub token and read the connected account profile."),
        ZiCodeToolCapability("GitHub", "Repository listing", "Read repositories available to the connected GitHub account."),
        ZiCodeToolCapability("GitHub", "Repository detail", "Inspect the selected repository before execution starts."),
        ZiCodeToolCapability("GitHub", "Repository creation", "Create a new repository and open it as a project chat."),
        ZiCodeToolCapability("Contents", "Tree browsing", "Read nested directories and preview files in a bottom sheet."),
        ZiCodeToolCapability("Contents", "File preview", "Open repository files and render decoded content inside ZiCode."),
        ZiCodeToolCapability("Contents", "File write API", "Create, update, and delete repository files through GitHub Contents API."),
        ZiCodeToolCapability("Branches", "Branch listing", "Inspect available branches before deciding where to run changes."),
        ZiCodeToolCapability("Branches", "Branch creation", "Create a new working branch when the task needs an isolated path."),
        ZiCodeToolCapability("Commits", "Commit history", "Read recent commits to understand current project momentum."),
        ZiCodeToolCapability("Actions", "Workflow scan", "Read workflows, runs, and step traces for CI or deployment checks."),
        ZiCodeToolCapability("Actions", "Workflow dispatch", "Trigger workflow dispatch jobs directly inside the project session."),
        ZiCodeToolCapability("Actions", "Run control", "Inspect workflow runs, read step traces, rerun jobs, or cancel active runs."),
        ZiCodeToolCapability("Release", "Release history", "Read releases and prepare release context from GitHub."),
        ZiCodeToolCapability("Release", "Release creation", "Create a GitHub release when the repository is ready to ship."),
        ZiCodeToolCapability("Pages", "Pages status", "Inspect GitHub Pages status and source branch configuration.")
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
