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
    val memberModelIds: List<String> = emptyList(),
    val strategy: String = "dynamic", // dynamic | round_robin | random
    val dynamicCoordinatorModelId: String? = null,
    val conversationId: String,
    val roundRobinCursor: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val reasoning: String? = null,
    val tags: List<MessageTag>? = null,
    val attachments: List<MessageAttachment>? = null,
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
