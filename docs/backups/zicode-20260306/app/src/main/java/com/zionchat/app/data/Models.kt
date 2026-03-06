package com.zionchat.app.data

import java.util.UUID

data class ProviderConfig(
    val id: String = UUID.randomUUID().toString(),
    val presetId: String? = null,
    val iconAsset: String? = null,
    val name: String,
    val type: String,
    val apiUrl: String,
    val apiKey: String,
    val headers: List<HttpHeader> = emptyList(),
    val oauthProvider: String? = null,
    val oauthAccessToken: String? = null,
    val oauthRefreshToken: String? = null,
    val oauthIdToken: String? = null,
    val oauthAccountId: String? = null,
    val oauthEmail: String? = null,
    val oauthProjectId: String? = null,
    val oauthExpiresAtMs: Long? = null,
    val deviceProvider: String? = null,
    val deviceExpiresAtMs: Long? = null
)

data class ModelConfig(
    val id: String,
    val displayName: String,
    val enabled: Boolean = true,
    val providerId: String? = null,
    val headers: List<HttpHeader> = emptyList(),
    val reasoningEffort: String? = null,
    val inputModality: String = "text-image"
)

private const val MODEL_ID_SEPARATOR = "::"

fun buildModelStorageId(providerId: String, modelId: String): String {
    return "${providerId.trim()}$MODEL_ID_SEPARATOR${modelId.trim()}"
}

fun extractRemoteModelId(storageId: String): String {
    return storageId.substringAfter(MODEL_ID_SEPARATOR, storageId).trim()
}

data class HttpHeader(
    val key: String,
    val value: String
)

data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New chat",
    val messages: List<Message> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class GroupChatConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val memberBotIds: List<String> = emptyList(), // 群成员是Bot好友
    val strategy: String = "dynamic", // dynamic | round_robin
    val dynamicCoordinatorModelId: String? = null, // 调度模型（单独选择）
    val conversationId: String,
    val roundRobinCursor: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    @Deprecated("Use memberBotIds instead") val memberModelIds: List<String> = emptyList() // 向后兼容
)

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val reasoning: String? = null,
    val tags: List<MessageTag>? = null,
    val attachments: List<MessageAttachment>? = null,
    val speakerBotId: String? = null,
    val speakerName: String? = null,
    val speakerAvatarUri: String? = null,
    val speakerAvatarAssetName: String? = null,
    val speakerBatchId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class MessageAttachment(
    val id: String = UUID.randomUUID().toString(),
    val type: String = "image",
    val url: String,  // base64 data URL or remote URL
    val thumbnailUrl: String? = null
)

data class MessageTag(
    val id: String = UUID.randomUUID().toString(),
    val kind: String,
    val title: String,
    val content: String,
    val status: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class MemoryItem(
    val id: String = UUID.randomUUID().toString(),
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class SavedApp(
    val id: String = UUID.randomUUID().toString(),
    val sourceTagId: String? = null,
    val name: String,
    val description: String,
    val html: String,
    val deployUrl: String? = null,
    val runtimeBuildStatus: String = "",
    val runtimeBuildRequestId: String? = null,
    val runtimeBuildRunId: Long? = null,
    val runtimeBuildRunUrl: String? = null,
    val runtimeBuildArtifactName: String? = null,
    val runtimeBuildArtifactUrl: String? = null,
    val runtimeBuildError: String? = null,
    val runtimeBuildVersionName: String? = null,
    val runtimeBuildVersionCode: Int? = null,
    val runtimeBuildVersionModel: Int? = null,
    val runtimeBuildUpdatedAt: Long? = null,
    val versionCode: Int = 1,
    val versionName: String = "v1",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class SavedAppVersion(
    val id: String = UUID.randomUUID().toString(),
    val appId: String,
    val versionCode: Int,
    val versionName: String,
    val html: String,
    val deployUrl: String? = null,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class AppAutomationTask(
    val id: String = UUID.randomUUID().toString(),
    val mode: String, // "edit" | "debug_fix"
    val appId: String,
    val appName: String,
    val appHtml: String,
    val request: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class WebHostingConfig(
    val provider: String = "vercel",
    val token: String = "",
    val projectId: String = "",
    val teamId: String = "",
    val customDomain: String = "",
    val autoDeploy: Boolean = true
)

data class WebSearchConfig(
    val engine: String = "bing",
    val exaApiKey: String = "",
    val tavilyApiKey: String = "",
    val tavilyDepth: String = "advanced",
    val linkupApiKey: String = "",
    val linkupDepth: String = "standard",
    val autoSearchEnabled: Boolean = true,
    val maxResults: Int = 6
)

data class RuntimePackagingConfig(
    val localBridgeBaseUrl: String = "http://127.0.0.1:17856",
    val localBridgeToken: String = "",
    val requestTimeoutMs: Long = 30_000L
)

data class BotConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val avatarUri: String? = null, // 头像URI（用户上传或预设）
    val avatarAssetName: String? = null, // 预设头像资源名
    val defaultModelId: String? = null,
    val systemPrompt: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class ZiCodeWorkspace(
    val id: String = UUID.randomUUID().toString(),
    val owner: String,
    val repo: String,
    val defaultBranch: String = "main",
    val displayName: String = "$owner/$repo",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class ZiCodeSession(
    val id: String = UUID.randomUUID().toString(),
    val workspaceId: String,
    val modelName: String,
    val title: String,
    val branchName: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class ZiCodeMessage(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val role: String,
    val content: String,
    val toolHints: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

data class ZiCodeToolCall(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val toolName: String,
    val argsJson: String,
    val status: String,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val result: String? = null,
    val error: String? = null,
    val userHint: String = ""
)

data class ZiCodeRunRecord(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val workflow: String,
    val runId: Long? = null,
    val status: String,
    val summary: String = "",
    val runUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class ZiCodeReport(
    val status: String,
    val summary: String,
    val failingStep: String? = null,
    val errorSummary: String? = null,
    val fileHints: List<String> = emptyList(),
    val nextReads: List<String> = emptyList(),
    val artifacts: List<String> = emptyList(),
    val pagesUrl: String? = null,
    val deploymentStatus: String? = null
)

data class ZiCodeSettings(
    val pat: String = "",
    val currentWorkspaceId: String? = null,
    val autoInitWorkflowTemplates: Boolean = true,
    val autoMergePullRequest: Boolean = false,
    val maxSelfHealLoops: Int = 5
)
