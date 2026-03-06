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
    val status: ZiCodeToolStatus = ZiCodeToolStatus.QUEUED,
    val summary: String = "",
    val detailLog: String = "",
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
    val title: String = "New conversation",
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
