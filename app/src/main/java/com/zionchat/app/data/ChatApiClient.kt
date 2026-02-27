package com.zionchat.app.data

import com.zionchat.app.BuildConfig
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import java.io.IOException
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.random.Random

class ChatApiClient {
    private val client: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    private val webSearchClient: OkHttpClient =
        client.newBuilder()
            .readTimeout(35, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .build()
    private val grokAdminClient: OkHttpClient =
        client.newBuilder()
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    private val gson = Gson()
    private val jsonMediaType = "application/json".toMediaType()
    private val strictJsonMediaType = "application/json".toMediaType()
    private val markdownImageRegex = Regex("!\\[[^\\]]*\\]\\(([^)]+)\\)")
    private val htmlImageSrcRegex = Regex("(?i)<img[^>]+src=[\"']([^\"']+)[\"']")
    private val httpUrlRegex = Regex("https?://[^\\s\"'<>]+", RegexOption.IGNORE_CASE)
    private val dataUrlRegex = Regex("^data:([^;]+);base64,(.+)$", RegexOption.IGNORE_CASE)
    private val codexModelCache = ConcurrentHashMap<String, CodexModelMeta>()
    private val grokTokenSyncCache = ConcurrentHashMap<String, Long>()

    @Suppress("UNUSED_PARAMETER")
    private fun buildEffectiveHeaders(
        provider: ProviderConfig,
        extraHeaders: List<HttpHeader>
    ): List<HttpHeader> {
        val combined = ArrayList<HttpHeader>(extraHeaders.size)
        combined.addAll(extraHeaders)

        val map = LinkedHashMap<String, HttpHeader>()
        combined.forEach { header ->
            val key = header.key.trim()
            if (key.isBlank()) return@forEach
            map[key.lowercase()] = HttpHeader(key = key, value = header.value)
        }
        return map.values.toList()
    }

    private fun hasHeader(headers: List<HttpHeader>, key: String): Boolean {
        val normalized = key.trim().lowercase()
        return headers.any { it.key.trim().lowercase() == normalized }
    }

    private fun applyHeaders(builder: Request.Builder, headers: List<HttpHeader>) {
        headers.forEach { header ->
            val key = header.key.trim()
            if (key.isBlank()) return@forEach
            builder.header(key, header.value)
        }
    }

    private fun normalizeBearerToken(rawValue: String): String {
        val trimmed = rawValue.trim()
        if (trimmed.startsWith("bearer ", ignoreCase = true)) {
            return trimmed.substringAfter(' ', "").trim()
        }
        return trimmed
    }

    private fun providerBearerToken(provider: ProviderConfig): String? {
        val normalized = normalizeBearerToken(provider.apiKey)
        return normalized.takeIf { it.isNotBlank() }
    }

    private fun applyProviderAuthorizationIfNeeded(
        requestBuilder: Request.Builder,
        provider: ProviderConfig,
        effectiveHeaders: List<HttpHeader>
    ) {
        if (hasHeader(effectiveHeaders, "authorization")) return
        val token = providerBearerToken(provider) ?: return
        requestBuilder.header("Authorization", "Bearer $token")
    }

    private fun findHeaderValue(headers: List<HttpHeader>, key: String): String? {
        val normalized = key.trim().lowercase()
        return headers
            .firstOrNull { it.key.trim().lowercase() == normalized }
            ?.value
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun buildGrokAdminAppKeyCandidates(
        provider: ProviderConfig,
        effectiveHeaders: List<HttpHeader>
    ): List<String> {
        val candidates = LinkedHashSet<String>()
        val keyNames = listOf("x-grok-app-key", "x-app-key", "x-admin-key", "x-grok2api-app-key")

        keyNames.forEach { key ->
            findHeaderValue(effectiveHeaders, key)?.let { candidates += normalizeBearerToken(it) }
            findHeaderValue(provider.headers, key)?.let { candidates += normalizeBearerToken(it) }
        }

        candidates += GROK2API_DEFAULT_APP_KEY
        providerBearerToken(provider)?.let { raw ->
            val normalized = normalizeBearerToken(raw)
            if (normalized.isNotBlank() && !normalized.equals(GROK2API_DEFAULT_APP_KEY, ignoreCase = true)) {
                candidates += normalized
            }
        }

        return candidates.filter { it.isNotBlank() }
    }

    private fun isLocalGrokGatewayBase(baseUrl: String): Boolean {
        val lower = baseUrl.trim().lowercase()
        return lower.startsWith("http://") &&
            (
                lower.contains("localhost") ||
                    lower.contains("127.0.0.1") ||
                    lower.contains("10.0.2.2") ||
                    lower.contains("host.docker.internal")
            )
    }

    private fun grokGatewayPriority(baseUrl: String): Int {
        val lower = baseUrl.trim().lowercase()
        return when {
            lower.contains("host.docker.internal") -> 0
            lower.contains("10.0.2.2") -> 1
            lower.contains("127.0.0.1") -> 2
            lower.contains("localhost") -> 3
            else -> 10
        }
    }

    private fun toGrokAdminEndpoint(baseUrl: String, path: String): String {
        val normalized = baseUrl.trim().trimEnd('/')
        val root = normalized.replace(Regex("(?i)/v1$"), "")
        return "$root/v1/admin/$path"
    }

    private fun sha256Short(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(16)
    }

    private fun shouldSkipGrokTokenSync(cacheKey: String): Boolean {
        val now = System.currentTimeMillis()
        val last = grokTokenSyncCache[cacheKey] ?: return false
        return now - last < GROK_TOKEN_SYNC_INTERVAL_MS
    }

    private fun markGrokTokenSync(cacheKey: String) {
        grokTokenSyncCache[cacheKey] = System.currentTimeMillis()
    }

    private data class GrokTokenSyncAttemptResult(
        val success: Boolean,
        val summary: String
    )

    private data class GrokTokenSyncOutcome(
        val attempted: Boolean,
        val success: Boolean,
        val summary: String
    )

    private fun postGrokAdminJson(url: String, appKey: String, payload: Any): Pair<Int, String> {
        val body = gson.toJson(payload)
        val request =
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${normalizeBearerToken(appKey)}")
                .header("Content-Type", "application/json")
                .post(body.toRequestBody(jsonMediaType))
                .build()
        return grokAdminClient.newCall(request).execute().use { response ->
            response.code to response.body.string()
        }
    }

    private fun trySyncGrokTokenOnGateway(
        baseUrl: String,
        token: String,
        appKeyCandidates: List<String>
    ): GrokTokenSyncAttemptResult {
        if (appKeyCandidates.isEmpty()) {
            return GrokTokenSyncAttemptResult(
                success = false,
                summary = "$baseUrl token sync skipped: no app key candidates"
            )
        }
        val upsertUrl = toGrokAdminEndpoint(baseUrl, "tokens")
        val refreshUrl = toGrokAdminEndpoint(baseUrl, "tokens/refresh")
        val upsertPayload =
            mapOf(
                "ssoBasic" to listOf(mapOf("token" to token)),
                "ssoSuper" to listOf(mapOf("token" to token))
            )
        val refreshPayload = mapOf("token" to token)
        val errorMessages = mutableListOf<String>()

        appKeyCandidates.forEach { appKey ->
            val result = runCatching {
                val (statusCode, _) = postGrokAdminJson(upsertUrl, appKey, upsertPayload)
                if (statusCode in 200..299) {
                    runCatching { postGrokAdminJson(refreshUrl, appKey, refreshPayload) }
                    return GrokTokenSyncAttemptResult(
                        success = true,
                        summary = "$baseUrl token synced"
                    )
                }
                if (statusCode == 401 || statusCode == 403) {
                    errorMessages += "auth rejected"
                } else {
                    errorMessages += "upsert HTTP $statusCode"
                }
            }.exceptionOrNull()

            if (result != null) {
                val reason = result.message?.lineSequence()?.firstOrNull()?.trim().orEmpty()
                errorMessages += if (reason.isNotBlank()) reason else result::class.java.simpleName
            }
        }

        val summaryTail = errorMessages.distinct().joinToString(", ").ifBlank { "unknown reason" }
        return GrokTokenSyncAttemptResult(
            success = false,
            summary = "$baseUrl token sync failed: $summaryTail"
        )
    }

    private fun ensureGrokGatewayTokenSynced(
        provider: ProviderConfig,
        effectiveHeaders: List<HttpHeader>,
        baseCandidates: List<String>
    ): GrokTokenSyncOutcome {
        if (!isGrok2Api(provider)) {
            return GrokTokenSyncOutcome(attempted = false, success = true, summary = "")
        }
        val rawToken = providerBearerToken(provider)
            ?: return GrokTokenSyncOutcome(attempted = false, success = true, summary = "")
        if (rawToken.equals(GROK2API_DEFAULT_APP_KEY, ignoreCase = true)) {
            return GrokTokenSyncOutcome(attempted = false, success = true, summary = "")
        }
        val normalizedToken =
            rawToken
                .removePrefix("sso=")
                .trim()
                .takeIf { it.isNotBlank() }
                ?: return GrokTokenSyncOutcome(attempted = false, success = true, summary = "")
        val localBases = baseCandidates.filter { isLocalGrokGatewayBase(it) }.distinct()
        if (localBases.isEmpty()) {
            return GrokTokenSyncOutcome(attempted = false, success = true, summary = "")
        }

        val cacheKey =
            buildString {
                append(provider.id.trim())
                append('|')
                append(sha256Short(normalizedToken))
                append('|')
                append(localBases.joinToString(","))
            }
        if (shouldSkipGrokTokenSync(cacheKey)) {
            return GrokTokenSyncOutcome(attempted = true, success = true, summary = "token sync cached")
        }

        val appKeyCandidates = buildGrokAdminAppKeyCandidates(provider, effectiveHeaders)
        val failures = mutableListOf<String>()
        localBases.forEach { base ->
            val result = trySyncGrokTokenOnGateway(base, normalizedToken, appKeyCandidates)
            if (result.success) {
                markGrokTokenSync(cacheKey)
                return GrokTokenSyncOutcome(attempted = true, success = true, summary = result.summary)
            }
            failures += result.summary
        }

        markGrokTokenSync(cacheKey)
        return GrokTokenSyncOutcome(
            attempted = true,
            success = false,
            summary = failures.distinct().joinToString(" | ")
        )
    }

    private data class GrokReverseModelConfig(
        val modelName: String,
        val modelMode: String? = null
    )

    private fun resolveGrokReverseModelConfig(modelId: String): GrokReverseModelConfig {
        val key = modelId.trim().lowercase()
        return GROK_REVERSE_MODEL_CONFIGS[key] ?: GrokReverseModelConfig(
            modelName = modelId.trim().ifBlank { "grok-3" },
            modelMode = null
        )
    }

    private fun normalizeGrokSsoToken(rawValue: String): String {
        return normalizeBearerToken(rawValue)
            .removePrefix("sso=")
            .trim()
    }

    private fun isLikelyXaiApiKey(token: String): Boolean {
        val normalized = token.trim()
        if (normalized.isBlank()) return false
        return normalized.startsWith("xai-", ignoreCase = true) ||
            normalized.startsWith("sk-", ignoreCase = true) ||
            normalized.startsWith("xai_", ignoreCase = true)
    }

    private fun isGrokReverseDirect(provider: ProviderConfig): Boolean {
        if (!isGrok2Api(provider)) return false
        val token = providerBearerToken(provider) ?: return false
        val normalized = normalizeGrokSsoToken(token)
        if (normalized.isBlank()) return false
        if (normalized.equals(GROK2API_DEFAULT_APP_KEY, ignoreCase = true)) return false
        if (isLikelyXaiApiKey(normalized)) return false
        return true
    }

    private fun buildGrokReversePrompt(messages: List<Message>): String {
        if (messages.isEmpty()) return ""
        val extracted = mutableListOf<Pair<String, String>>()

        messages.forEach { message ->
            val role = message.role.trim().ifBlank { "user" }
            val parts = mutableListOf<String>()
            val (text, markdownImages) = extractMarkdownImages(message.content)
            if (text.isNotBlank()) {
                parts += text
            }
            val attachmentHints =
                message.attachments
                    .orEmpty()
                    .mapNotNull { attachment -> attachment.url.trim().takeIf { it.isNotBlank() } }
            val allImages = (markdownImages + attachmentHints).distinct()
            if (allImages.isNotEmpty()) {
                parts += allImages.joinToString("\n") { url -> "image: $url" }
            }
            val merged = parts.joinToString("\n").trim()
            if (merged.isNotBlank()) {
                extracted += role to merged
            }
        }

        if (extracted.isEmpty()) return ""
        val lastUserIndex = extracted.indexOfLast { (role, _) -> role.equals("user", ignoreCase = true) }
        return extracted.mapIndexed { index, (role, text) ->
            if (index == lastUserIndex) {
                text
            } else {
                "$role: $text"
            }
        }.joinToString("\n\n").trim()
    }

    private fun buildGrokReversePayload(
        message: String,
        modelId: String,
        reasoningEffort: String?
    ): Map<String, Any> {
        val modelConfig = resolveGrokReverseModelConfig(modelId)
        val modelConfigOverride = linkedMapOf<String, Any>(
            "temperature" to 0.8,
            "topP" to 0.95
        )
        normalizeReasoningEffort(reasoningEffort)?.let { modelConfigOverride["reasoningEffort"] = it }

        val responseMetadata =
            linkedMapOf<String, Any>(
                "requestModelDetails" to mapOf("modelId" to modelId),
                "modelConfigOverride" to modelConfigOverride
            )

        val payload = linkedMapOf<String, Any>(
            "deviceEnvInfo" to mapOf(
                "darkModeEnabled" to false,
                "devicePixelRatio" to 2,
                "screenWidth" to 2056,
                "screenHeight" to 1329,
                "viewportWidth" to 2056,
                "viewportHeight" to 1083
            ),
            "disableMemory" to false,
            "disableSearch" to false,
            "disableSelfHarmShortCircuit" to false,
            "disableTextFollowUps" to false,
            "enableImageGeneration" to true,
            "enableImageStreaming" to true,
            "enableSideBySide" to true,
            "fileAttachments" to emptyList<String>(),
            "forceConcise" to false,
            "forceSideBySide" to false,
            "imageAttachments" to emptyList<String>(),
            "imageGenerationCount" to 2,
            "isAsyncChat" to false,
            "isReasoning" to (normalizeReasoningEffort(reasoningEffort) != null && !reasoningEffort.equals("none", ignoreCase = true)),
            "message" to message,
            "modelName" to modelConfig.modelName,
            "responseMetadata" to responseMetadata,
            "returnImageBytes" to false,
            "returnRawGrokInXaiRequest" to false,
            "sendFinalMetadata" to true,
            "temporary" to true,
            "toolOverrides" to emptyMap<String, Any>()
        )
        modelConfig.modelMode?.takeIf { it.isNotBlank() }?.let { payload["modelMode"] = it }
        return payload
    }

    private fun buildGrokReverseRequest(
        provider: ProviderConfig,
        effectiveHeaders: List<HttpHeader>,
        payload: Map<String, Any>
    ): Request {
        val token = providerBearerToken(provider)?.let(::normalizeGrokSsoToken).orEmpty()
        if (token.isBlank()) {
            throw IllegalStateException("Grok token is required.")
        }

        val requestBuilder = Request.Builder().url(GROK_REVERSE_CHAT_API)
        applyHeaders(requestBuilder, effectiveHeaders)

        if (!hasHeader(effectiveHeaders, "content-type")) {
            requestBuilder.header("Content-Type", "application/json")
        }
        if (!hasHeader(effectiveHeaders, "accept")) {
            requestBuilder.header("Accept", "*/*")
        }
        if (!hasHeader(effectiveHeaders, "origin")) {
            requestBuilder.header("Origin", "https://grok.com")
        }
        if (!hasHeader(effectiveHeaders, "referer")) {
            requestBuilder.header("Referer", "https://grok.com/")
        }
        if (!hasHeader(effectiveHeaders, "sec-fetch-mode")) {
            requestBuilder.header("Sec-Fetch-Mode", "cors")
        }
        if (!hasHeader(effectiveHeaders, "sec-fetch-dest")) {
            requestBuilder.header("Sec-Fetch-Dest", "empty")
        }
        if (!hasHeader(effectiveHeaders, "sec-fetch-site")) {
            requestBuilder.header("Sec-Fetch-Site", "same-origin")
        }
        if (!hasHeader(effectiveHeaders, "priority")) {
            requestBuilder.header("Priority", "u=1, i")
        }
        if (!hasHeader(effectiveHeaders, "accept-language")) {
            requestBuilder.header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        }
        if (!hasHeader(effectiveHeaders, "user-agent")) {
            requestBuilder.header("User-Agent", GROK_REVERSE_USER_AGENT)
        }
        if (!hasHeader(effectiveHeaders, "cookie")) {
            requestBuilder.header("Cookie", "sso=$token; sso-rw=$token")
        }
        if (!hasHeader(effectiveHeaders, "baggage")) {
            requestBuilder.header("Baggage", GROK_REVERSE_BAGGAGE)
        }
        if (!hasHeader(effectiveHeaders, "x-statsig-id")) {
            requestBuilder.header("x-statsig-id", GROK_REVERSE_STATSIG_ID)
        }
        if (!hasHeader(effectiveHeaders, "x-xai-request-id")) {
            requestBuilder.header("x-xai-request-id", UUID.randomUUID().toString())
        }

        val body = gson.toJson(payload)
        return requestBuilder
            .post(body.toRequestBody(jsonMediaType))
            .build()
    }

    private fun normalizeGrokReverseStreamLine(rawLine: String): String? {
        val trimmed = rawLine.trim()
        if (trimmed.isBlank()) return null
        if (trimmed.equals("[DONE]", ignoreCase = true)) return null
        if (trimmed.startsWith("data:", ignoreCase = true)) {
            val data = trimmed.removePrefix("data:").trim()
            if (data.isBlank() || data.equals("[DONE]", ignoreCase = true)) return null
            return data
        }
        return trimmed
    }

    private fun com.google.gson.JsonObject.getObject(key: String): com.google.gson.JsonObject? {
        val element = get(key) ?: return null
        if (!element.isJsonObject) return null
        return element.asJsonObject
    }

    private fun com.google.gson.JsonObject.getStringValue(key: String): String? {
        val element = get(key) ?: return null
        if (!element.isJsonPrimitive) return null
        return runCatching { element.asString }.getOrNull()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun com.google.gson.JsonObject.getBooleanValue(key: String): Boolean? {
        val element = get(key) ?: return null
        if (!element.isJsonPrimitive) return null
        return runCatching { element.asBoolean }.getOrNull()
    }

    private fun extractGrokReverseResponseObject(rawLine: String): com.google.gson.JsonObject? {
        val normalized = normalizeGrokReverseStreamLine(rawLine) ?: return null
        val root = runCatching { JsonParser.parseString(normalized).asJsonObject }.getOrNull() ?: return null
        return root.getObject("result")?.getObject("response")
    }

    private fun isLikelyImageBase64(raw: String): Boolean {
        val candidate = raw.trim()
        if (candidate.length < 64) return false
        if (candidate.startsWith("iVBORw0KGgo")) return true
        if (candidate.startsWith("/9j/")) return true
        if (candidate.startsWith("R0lGOD")) return true
        return false
    }

    private fun normalizeCandidateImageUrl(raw: String): String? {
        val value = raw.trim()
            .trim('"', '\'', ',', ')', ']', '}')
            .trim()
        if (value.isBlank()) return null
        if (value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)) {
            return value
        }
        if (value.startsWith("data:image/", ignoreCase = true)) {
            return value
        }
        if (isLikelyImageBase64(value)) {
            val mime = if (value.startsWith("/9j/")) "image/jpeg" else "image/png"
            return "data:$mime;base64,$value"
        }
        if (value.startsWith("/") && value.contains("image", ignoreCase = true)) {
            return "https://grok.com$value"
        }
        return null
    }

    private fun collectImageUrlsFromText(raw: String, sink: LinkedHashSet<String>) {
        markdownImageRegex.findAll(raw).forEach { match ->
            val url = match.groupValues.getOrNull(1).orEmpty()
            normalizeCandidateImageUrl(url)?.let { sink += it }
        }
        htmlImageSrcRegex.findAll(raw).forEach { match ->
            val url = match.groupValues.getOrNull(1).orEmpty()
            normalizeCandidateImageUrl(url)?.let { sink += it }
        }
        httpUrlRegex.findAll(raw).forEach { match ->
            val url = match.value
            normalizeCandidateImageUrl(url)?.let { sink += it }
        }
    }

    private fun collectImageUrlsFromJsonElement(
        element: com.google.gson.JsonElement?,
        sink: LinkedHashSet<String>
    ) {
        when {
            element == null || element.isJsonNull -> return
            element.isJsonPrimitive -> {
                val primitive = element.asJsonPrimitive
                if (!primitive.isString) return
                val text = primitive.asString.trim()
                if (text.isBlank()) return
                normalizeCandidateImageUrl(text)?.let { sink += it }
                collectImageUrlsFromText(text, sink)
                if ((text.startsWith("{") && text.endsWith("}")) || (text.startsWith("[") && text.endsWith("]"))) {
                    val nested = runCatching { JsonParser.parseString(text) }.getOrNull()
                    if (nested != null) {
                        collectImageUrlsFromJsonElement(nested, sink)
                    }
                }
            }
            element.isJsonArray -> {
                element.asJsonArray.forEach { item ->
                    collectImageUrlsFromJsonElement(item, sink)
                }
            }
            element.isJsonObject -> {
                element.asJsonObject.entrySet().forEach { entry ->
                    val key = entry.key.trim().lowercase()
                    val value = entry.value
                    if (
                        key == "generatedimageurls" ||
                        key == "imageurls" ||
                        key == "imageurl" ||
                        key == "original" ||
                        key == "src" ||
                        key == "url"
                    ) {
                        collectImageUrlsFromJsonElement(value, sink)
                    }
                    collectImageUrlsFromJsonElement(value, sink)
                }
            }
        }
    }

    private fun extractGrokImageUrls(responseObject: com.google.gson.JsonObject): List<String> {
        val urls = linkedSetOf<String>()
        collectImageUrlsFromJsonElement(responseObject, urls)

        responseObject.getObject("modelResponse")?.let { modelResponse ->
            collectImageUrlsFromJsonElement(modelResponse, urls)
            modelResponse.getStringValue("message")?.let { message ->
                collectImageUrlsFromText(message, urls)
            }
            runCatching { modelResponse.getAsJsonArray("cardAttachmentsJson") }.getOrNull()
                ?.forEach { rawItem ->
                    collectImageUrlsFromJsonElement(rawItem, urls)
                }
        }

        responseObject.getObject("cardAttachment")
            ?.getStringValue("jsonData")
            ?.let { jsonData ->
                val parsed = runCatching { JsonParser.parseString(jsonData).asJsonObject }.getOrNull()
                val original = parsed?.getObject("image")?.getStringValue("original")
                if (!original.isNullOrBlank()) {
                    normalizeCandidateImageUrl(original)?.let { urls += it }
                }
                collectImageUrlsFromJsonElement(parsed, urls)
            }
        return urls.toList()
    }

    private fun grokReverseChatCompletion(
        provider: ProviderConfig,
        modelId: String,
        messages: List<Message>,
        extraHeaders: List<HttpHeader>
    ): String {
        val prompt = buildGrokReversePrompt(messages)
        if (prompt.isBlank()) {
            throw IllegalStateException("Grok prompt is empty.")
        }
        val payload = buildGrokReversePayload(prompt, modelId, reasoningEffort = null)
        val request = buildGrokReverseRequest(provider, extraHeaders, payload)
        val contentBuilder = StringBuilder()
        val reasoningBuilder = StringBuilder()
        var finalMessage = ""
        val imageUrls = linkedSetOf<String>()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val raw = response.body.string()
                throw IllegalStateException("HTTP ${response.code}: $raw")
            }
            val source = response.body.source()
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: continue
                val responseObject = extractGrokReverseResponseObject(line) ?: continue
                val tokenChunk = responseObject.getStringValue("token").orEmpty()
                val isThinking = responseObject.getBooleanValue("isThinking") == true
                if (tokenChunk.isNotEmpty()) {
                    if (isThinking) {
                        reasoningBuilder.append(tokenChunk)
                    } else {
                        contentBuilder.append(tokenChunk)
                    }
                }
                responseObject.getObject("modelResponse")
                    ?.getStringValue("message")
                    ?.let { messageText -> finalMessage = messageText }
                extractGrokImageUrls(responseObject).forEach { url -> imageUrls += url }
            }
        }

        val text = contentBuilder.toString().trim().ifBlank { finalMessage.trim() }
        val primary = text.ifBlank { reasoningBuilder.toString().trim() }
        val imagesMarkdown = imageUrls.joinToString("\n") { url -> "![image]($url)" }
        return listOf(primary, imagesMarkdown)
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .trim()
            .ifBlank { throw IllegalStateException("Grok response is empty.") }
    }

    private fun grokReverseGenerateImage(
        provider: ProviderConfig,
        modelId: String,
        prompt: String,
        extraHeaders: List<HttpHeader>,
        size: String,
        quality: String,
        n: Int
    ): String {
        val enrichedPrompt =
            buildString {
                append(prompt.trim())
                if (size.isNotBlank()) {
                    append("\n[image_size] ")
                    append(size.trim())
                }
                if (quality.isNotBlank()) {
                    append("\n[image_quality] ")
                    append(quality.trim())
                }
                if (n > 1) {
                    append("\n[image_count] ")
                    append(n)
                }
            }.trim()
        if (enrichedPrompt.isBlank()) {
            throw IllegalStateException("Prompt is required.")
        }
        val payload = buildGrokReversePayload(enrichedPrompt, modelId, reasoningEffort = null)
        val request = buildGrokReverseRequest(provider, extraHeaders, payload)
        val imageUrls = linkedSetOf<String>()
        val samples = mutableListOf<String>()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val raw = response.body.string()
                throw IllegalStateException("HTTP ${response.code}: $raw")
            }
            val source = response.body.source()
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: continue
                normalizeGrokReverseStreamLine(line)?.let { normalized ->
                    if (samples.size < 6) {
                        samples += normalized.take(240)
                    }
                }
                val responseObject = extractGrokReverseResponseObject(line) ?: continue
                extractGrokImageUrls(responseObject).forEach { url -> imageUrls += url }
            }
        }

        return imageUrls.firstOrNull()
            ?: throw IllegalStateException(
                "No image URL found in Grok reverse response. samples=${samples.joinToString(" || ")}"
            )
    }

    private data class GrokReverseWsImage(
        val imageId: String,
        val url: String,
        val blob: String,
        val blobSize: Int,
        val ext: String?,
        val isFinal: Boolean
    )

    private fun normalizeGrokWsBlob(rawBlob: String): String {
        val candidate = rawBlob.trim()
        if (candidate.isBlank()) return ""
        if (candidate.startsWith("data:image/", ignoreCase = true) && candidate.contains(",")) {
            return candidate.substringAfter(",", "")
        }
        return candidate
    }

    private fun normalizeImageSizeToAspectRatio(size: String): String {
        val normalized = size.trim().lowercase()
        return when (normalized) {
            "1280x720" -> "16:9"
            "720x1280" -> "9:16"
            "1792x1024" -> "3:2"
            "1024x1792" -> "2:3"
            "1024x1024" -> "1:1"
            else -> {
                val match = Regex("^(\\d{2,5})x(\\d{2,5})$").find(normalized)
                if (match != null) {
                    val w = match.groupValues.getOrNull(1)?.toIntOrNull()
                    val h = match.groupValues.getOrNull(2)?.toIntOrNull()
                    if (w != null && h != null && w > 0 && h > 0) {
                        val divisor = gcd(w, h).coerceAtLeast(1)
                        "${w / divisor}:${h / divisor}"
                    } else {
                        "1:1"
                    }
                } else {
                    "1:1"
                }
            }
        }
    }

    private fun gcd(a: Int, b: Int): Int {
        var x = kotlin.math.abs(a)
        var y = kotlin.math.abs(b)
        while (y != 0) {
            val t = x % y
            x = y
            y = t
        }
        return if (x == 0) 1 else x
    }

    private fun resolveImageMimeByExt(ext: String?): String {
        return when (ext?.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "image/png"
        }
    }

    private fun detectImageExt(url: String, blob: String): String {
        val fromUrl =
            Regex("/images/[a-f0-9-]+\\.(png|jpg|jpeg|webp|gif)", RegexOption.IGNORE_CASE)
                .find(url)
                ?.groupValues
                ?.getOrNull(1)
                ?.lowercase()
        if (!fromUrl.isNullOrBlank()) return fromUrl
        return when {
            blob.startsWith("/9j/") -> "jpg"
            blob.startsWith("R0lGOD") -> "gif"
            blob.startsWith("UklGR") -> "webp"
            else -> "png"
        }
    }

    private fun isGrokWsFinalImage(url: String, blobSize: Int): Boolean {
        val lowerUrl = url.trim().lowercase()
        if (lowerUrl.endsWith(".jpg") || lowerUrl.endsWith(".jpeg")) return true
        return blobSize >= GROK_REVERSE_IMAGINE_FINAL_MIN_BLOB_BYTES
    }

    private fun parseGrokReverseWsImageFrame(root: com.google.gson.JsonObject): GrokReverseWsImage? {
        val type = root.getStringValue("type")?.lowercase()
        if (type != "image") return null
        val urlRaw = root.getStringValue("url").orEmpty()
        val blobRaw = root.getStringValue("blob").orEmpty()
        val normalizedBlob = normalizeGrokWsBlob(blobRaw)
        if (urlRaw.isBlank() && normalizedBlob.isBlank()) return null
        val ext = detectImageExt(urlRaw, normalizedBlob)
        val imageIdFromUrl =
            Regex("/images/([a-f0-9-]+)\\.(?:png|jpg|jpeg|webp|gif)", RegexOption.IGNORE_CASE)
                .find(urlRaw)
                ?.groupValues
                ?.getOrNull(1)
        val imageId =
            imageIdFromUrl
                ?: root.getStringValue("imageId")
                ?: root.getStringValue("id")
                ?: UUID.randomUUID().toString()
        val final = isGrokWsFinalImage(urlRaw, normalizedBlob.length)
        return GrokReverseWsImage(
            imageId = imageId,
            url = urlRaw,
            blob = normalizedBlob,
            blobSize = normalizedBlob.length,
            ext = ext,
            isFinal = final
        )
    }

    private fun toImageOutputUrl(item: GrokReverseWsImage): String? {
        normalizeCandidateImageUrl(item.url)?.let { return it }
        if (item.blob.isBlank()) return null
        val mime = resolveImageMimeByExt(item.ext)
        return "data:$mime;base64,${item.blob}"
    }

    private fun buildGrokImagineWsPayload(
        prompt: String,
        size: String,
        n: Int
    ): Map<String, Any> {
        val aspectRatio = normalizeImageSizeToAspectRatio(size)
        val requestId = UUID.randomUUID().toString()
        val contentItemProperties =
            linkedMapOf<String, Any>(
                "section_count" to 0,
                "is_kids_mode" to false,
                "enable_nsfw" to true,
                "skip_upsampler" to false,
                "is_initial" to false,
                "aspect_ratio" to aspectRatio
            )
        if (n > 1) {
            contentItemProperties["batch_size"] = n.coerceIn(1, 6)
        }
        return linkedMapOf(
            "type" to "conversation.item.create",
            "timestamp" to System.currentTimeMillis(),
            "item" to mapOf(
                "type" to "message",
                "content" to listOf(
                    mapOf(
                        "requestId" to requestId,
                        "text" to prompt.trim(),
                        "type" to "input_text",
                        "properties" to contentItemProperties
                    )
                )
            )
        )
    }

    private fun buildGrokImagineWsRequest(
        provider: ProviderConfig,
        effectiveHeaders: List<HttpHeader>
    ): Request {
        val token = providerBearerToken(provider)?.let(::normalizeGrokSsoToken).orEmpty()
        if (token.isBlank()) {
            throw IllegalStateException("Grok token is required.")
        }
        val requestBuilder = Request.Builder().url(GROK_REVERSE_IMAGINE_WS_API)
        applyHeaders(requestBuilder, effectiveHeaders)
        if (!hasHeader(effectiveHeaders, "origin")) {
            requestBuilder.header("Origin", "https://grok.com")
        }
        if (!hasHeader(effectiveHeaders, "user-agent")) {
            requestBuilder.header("User-Agent", GROK_REVERSE_USER_AGENT)
        }
        if (!hasHeader(effectiveHeaders, "accept-language")) {
            requestBuilder.header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        }
        if (!hasHeader(effectiveHeaders, "cache-control")) {
            requestBuilder.header("Cache-Control", "no-cache")
        }
        if (!hasHeader(effectiveHeaders, "pragma")) {
            requestBuilder.header("Pragma", "no-cache")
        }
        if (!hasHeader(effectiveHeaders, "cookie")) {
            requestBuilder.header("Cookie", "sso=$token; sso-rw=$token")
        }
        return requestBuilder.build()
    }

    private fun chooseBetterGrokWsImage(
        current: GrokReverseWsImage?,
        incoming: GrokReverseWsImage
    ): GrokReverseWsImage {
        if (current == null) return incoming
        if (incoming.isFinal && !current.isFinal) return incoming
        if (!incoming.isFinal && current.isFinal) return current
        return if (incoming.blobSize > current.blobSize) incoming else current
    }

    private suspend fun grokReverseGenerateImageViaWs(
        provider: ProviderConfig,
        prompt: String,
        extraHeaders: List<HttpHeader>,
        size: String,
        quality: String,
        n: Int
    ): String {
        val finalPrompt =
            buildString {
                append(prompt.trim())
                if (size.isNotBlank()) {
                    append("\n[image_size] ")
                    append(size.trim())
                }
                if (quality.isNotBlank()) {
                    append("\n[image_quality] ")
                    append(quality.trim())
                }
            }.trim()
        if (finalPrompt.isBlank()) {
            throw IllegalStateException("Prompt is required.")
        }

        val request = buildGrokImagineWsRequest(provider, extraHeaders)
        val payload = gson.toJson(buildGrokImagineWsPayload(finalPrompt, size, n))
        val seenImages = linkedMapOf<String, GrokReverseWsImage>()
        val lock = Any()

        val output =
            try {
                withTimeout(GROK_REVERSE_IMAGINE_TIMEOUT_MS) {
                    suspendCancellableCoroutine<String> { continuation ->
                        val finished = AtomicBoolean(false)
                        fun resolveSuccess(value: String) {
                            if (!finished.compareAndSet(false, true)) return
                            continuation.resume(value)
                        }
                        fun resolveFailure(message: String) {
                            if (!finished.compareAndSet(false, true)) return
                            continuation.resumeWithException(IllegalStateException(message))
                        }
                        fun bestOutputFromSeen(): String? {
                            val best =
                                synchronized(lock) {
                                    seenImages.values.maxWithOrNull(
                                        compareBy<GrokReverseWsImage> { it.isFinal }
                                            .thenBy { it.blobSize }
                                    )
                                } ?: return null
                            return toImageOutputUrl(best)
                        }

                        val listener =
                            object : WebSocketListener() {
                                override fun onOpen(webSocket: WebSocket, response: Response) {
                                    val sent = webSocket.send(payload)
                                    if (!sent) {
                                        resolveFailure("Grok imagine websocket send failed.")
                                    }
                                }

                                override fun onMessage(webSocket: WebSocket, text: String) {
                                    val root = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull() ?: return
                                    val msgType = root.getStringValue("type")?.lowercase().orEmpty()
                                    if (msgType == "error") {
                                        val code =
                                            root.getStringValue("err_code")
                                                ?: root.getStringValue("error_code")
                                                ?: "ws_error"
                                        val msg =
                                            root.getStringValue("err_msg")
                                                ?: root.getStringValue("error")
                                                ?: "upstream websocket error"
                                        resolveFailure("Grok imagine websocket error($code): $msg")
                                        webSocket.cancel()
                                        return
                                    }
                                    if (msgType != "image") return
                                    val parsed = parseGrokReverseWsImageFrame(root) ?: return
                                    val bestCandidate =
                                        synchronized(lock) {
                                            val existing = seenImages[parsed.imageId]
                                            val merged = chooseBetterGrokWsImage(existing, parsed)
                                            seenImages[parsed.imageId] = merged
                                            merged
                                        }
                                    if (bestCandidate.isFinal) {
                                        val out = toImageOutputUrl(bestCandidate)
                                        if (!out.isNullOrBlank()) {
                                            resolveSuccess(out)
                                            webSocket.close(1000, "done")
                                        }
                                    }
                                }

                                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                                    val fallback = bestOutputFromSeen()
                                    if (!fallback.isNullOrBlank()) {
                                        resolveSuccess(fallback)
                                    } else {
                                        resolveFailure("Grok imagine websocket closed: $code $reason")
                                    }
                                }

                                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                                    val fallback = bestOutputFromSeen()
                                    if (!fallback.isNullOrBlank()) {
                                        resolveSuccess(fallback)
                                    } else {
                                        val msg = t.message?.lineSequence()?.firstOrNull()?.trim().orEmpty()
                                        val status = response?.code?.toString()?.takeIf { it.isNotBlank() }
                                        val detail =
                                            listOfNotNull(
                                                msg.takeIf { it.isNotBlank() },
                                                status?.let { "HTTP $it" }
                                            ).joinToString(", ")
                                        resolveFailure("Grok imagine websocket failed${if (detail.isNotBlank()) ": $detail" else "."}")
                                    }
                                }
                            }

                        val webSocket = client.newWebSocket(request, listener)
                        continuation.invokeOnCancellation { webSocket.cancel() }
                    }
                }
            } catch (timeout: TimeoutCancellationException) {
                val fallback =
                    synchronized(lock) {
                        seenImages.values.maxWithOrNull(
                            compareBy<GrokReverseWsImage> { it.isFinal }
                                .thenBy { it.blobSize }
                        )
                    }?.let(::toImageOutputUrl)
                if (!fallback.isNullOrBlank()) {
                    fallback
                } else {
                    throw IllegalStateException(
                        "Grok imagine websocket timeout after ${GROK_REVERSE_IMAGINE_TIMEOUT_MS / 1000}s."
                    )
                }
            }

        return output.trim().ifBlank { throw IllegalStateException("Grok imagine returned empty image output.") }
    }

    private suspend fun grokReverseGenerateImageDirect(
        provider: ProviderConfig,
        modelId: String,
        prompt: String,
        extraHeaders: List<HttpHeader>,
        size: String,
        quality: String,
        n: Int
    ): String {
        val attempts = mutableListOf<String>()
        runCatching {
            grokReverseGenerateImageViaWs(
                provider = provider,
                prompt = prompt,
                extraHeaders = extraHeaders,
                size = size,
                quality = quality,
                n = n
            )
        }.onSuccess { return it }
            .onFailure { error ->
                val msg = error.message?.lineSequence()?.firstOrNull()?.trim().orEmpty()
                attempts += if (msg.isBlank()) "ws imagine failed" else "ws imagine failed: $msg"
            }

        runCatching {
            grokReverseGenerateImage(
                provider = provider,
                modelId = modelId,
                prompt = prompt,
                extraHeaders = extraHeaders,
                size = size,
                quality = quality,
                n = n
            )
        }.onSuccess { return it }
            .onFailure { error ->
                val msg = error.message?.lineSequence()?.firstOrNull()?.trim().orEmpty()
                attempts += if (msg.isBlank()) "app-chat fallback failed" else "app-chat fallback failed: $msg"
            }

        throw IllegalStateException(
            "Grok reverse image generation failed. ${attempts.joinToString(" | ")}"
        )
    }

    private fun grokReverseChatCompletionsStream(
        provider: ProviderConfig,
        modelId: String,
        messages: List<Message>,
        extraHeaders: List<HttpHeader>,
        reasoningEffort: String?
    ): Flow<ChatStreamDelta> = flow {
        val prompt = buildGrokReversePrompt(messages)
        if (prompt.isBlank()) {
            throw IllegalStateException("Grok prompt is empty.")
        }
        val effectiveHeaders = buildEffectiveHeaders(provider, extraHeaders)
        val payload = buildGrokReversePayload(prompt, modelId, reasoningEffort = reasoningEffort)
        val request = buildGrokReverseRequest(provider, effectiveHeaders, payload)

        var hasTextChunk = false
        var finalMessage = ""
        val seenImages = linkedSetOf<String>()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val raw = response.body.string()
                throw IllegalStateException("HTTP ${response.code}: $raw")
            }

            val source = response.body.source()
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: continue
                val responseObject = extractGrokReverseResponseObject(line) ?: continue

                val tokenChunk = responseObject.getStringValue("token").orEmpty()
                if (tokenChunk.isNotEmpty()) {
                    val isThinking = responseObject.getBooleanValue("isThinking") == true
                    if (isThinking) {
                        emit(ChatStreamDelta(reasoning = tokenChunk))
                    } else {
                        emit(ChatStreamDelta(content = tokenChunk))
                        hasTextChunk = true
                    }
                }

                responseObject.getObject("modelResponse")
                    ?.getStringValue("message")
                    ?.let { messageText -> finalMessage = messageText }

                extractGrokImageUrls(responseObject).forEach { url ->
                    if (seenImages.add(url)) {
                        emit(ChatStreamDelta(content = "\n![image]($url)\n"))
                        hasTextChunk = true
                    }
                }
            }
        }

        if (!hasTextChunk && finalMessage.isNotBlank()) {
            emit(ChatStreamDelta(content = finalMessage.trim()))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun webSearch(query: String): Result<String> {
        return webSearch(query, WebSearchConfig())
    }

    suspend fun webSearch(query: String, config: WebSearchConfig): Result<String> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return Result.success("")

        return withContext(Dispatchers.IO) {
            runCatching {
                val engine = normalizeSearchEngine(config.engine)
                val maxResults = config.maxResults.coerceIn(1, 10)
                val result =
                    when (engine) {
                        "exa" -> searchWithExa(trimmed, config.exaApiKey, maxResults)
                        "tavily" -> searchWithTavily(trimmed, config.tavilyApiKey, config.tavilyDepth, maxResults)
                        "linkup" -> searchWithLinkup(trimmed, config.linkupApiKey, config.linkupDepth, maxResults)
                        else -> searchWithBing(trimmed, maxResults)
                    }
                formatWebSearchContext(engine, trimmed, result)
            }
        }
    }

    private fun normalizeSearchEngine(raw: String): String {
        return when (raw.trim().lowercase()) {
            "bing", "exa", "tavily", "linkup" -> raw.trim().lowercase()
            else -> "bing"
        }
    }

    private fun normalizeWhitespace(raw: String): String {
        return raw.replace(Regex("\\s+"), " ").trim()
    }

    private fun searchWithBing(query: String, maxResults: Int): WebSearchResponse {
        val url =
            "https://www.bing.com/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .build()
        val request =
            Request.Builder()
                .url(url)
                .get()
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                )
                .header(
                    "Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8"
                )
                .header("Accept-Language", "en-US,en;q=0.9,zh-CN;q=0.8")
                .header("Referer", "https://www.bing.com/")
                .build()

        webSearchClient.newCall(request).execute().use { response ->
            val raw = response.body.string()
            if (!response.isSuccessful) {
                error("Bing HTTP ${response.code}: $raw")
            }

            val doc = Jsoup.parse(raw)
            val items =
                doc.select("li.b_algo")
                    .mapNotNull { block ->
                        val title = normalizeWhitespace(block.selectFirst("h2")?.text().orEmpty())
                        val urlValue = normalizeWhitespace(block.selectFirst("h2 a")?.attr("href").orEmpty())
                        val snippet =
                            normalizeWhitespace(
                                block.selectFirst(".b_caption p")?.text()
                                    ?: block.selectFirst("p")?.text().orEmpty()
                            )
                        if (title.isBlank() || urlValue.isBlank()) {
                            null
                        } else {
                            WebSearchItem(
                                title = title,
                                url = urlValue,
                                text = snippet
                            )
                        }
                    }
                    .take(maxResults)
            if (items.isEmpty()) error("Bing returned no usable results.")
            return WebSearchResponse(items = items)
        }
    }

    private fun searchWithExa(query: String, apiKey: String, maxResults: Int): WebSearchResponse {
        val key = apiKey.trim()
        if (key.isBlank()) error("Exa API key is required.")

        val body =
            gson.toJson(
                mapOf(
                    "query" to query,
                    "numResults" to maxResults,
                    "contents" to mapOf("text" to true)
                )
            )
        val request =
            Request.Builder()
                .url("https://api.exa.ai/search")
                .post(body.toRequestBody(strictJsonMediaType))
                .header("Authorization", "Bearer $key")
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .build()

        webSearchClient.newCall(request).execute().use { response ->
            val raw = response.body.string()
            if (!response.isSuccessful) {
                error("Exa HTTP ${response.code}: $raw")
            }
            val json = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()
                ?: error("Invalid Exa response")
            val results = runCatching { json.getAsJsonArray("results") }.getOrNull()
            val items =
                results?.mapNotNull { element ->
                    val obj = runCatching { element.asJsonObject }.getOrNull() ?: return@mapNotNull null
                    val title = normalizeWhitespace(obj.get("title")?.asString.orEmpty())
                    val urlValue = normalizeWhitespace(obj.get("url")?.asString.orEmpty())
                    val text = normalizeWhitespace(obj.get("text")?.asString.orEmpty())
                    if (title.isBlank() || urlValue.isBlank()) null else WebSearchItem(title = title, url = urlValue, text = text)
                }.orEmpty()
            if (items.isEmpty()) error("Exa returned no usable results.")
            return WebSearchResponse(items = items.take(maxResults))
        }
    }

    private fun searchWithTavily(
        query: String,
        apiKey: String,
        depthRaw: String,
        maxResults: Int
    ): WebSearchResponse {
        val key = apiKey.trim()
        if (key.isBlank()) error("Tavily API key is required.")
        val depth =
            when (depthRaw.trim().lowercase()) {
                "basic", "advanced" -> depthRaw.trim().lowercase()
                else -> "advanced"
            }

        val body =
            gson.toJson(
                mapOf(
                    "query" to query,
                    "max_results" to maxResults,
                    "search_depth" to depth,
                    "topic" to "general"
                )
            )
        val request =
            Request.Builder()
                .url("https://api.tavily.com/search")
                .post(body.toRequestBody(strictJsonMediaType))
                .header("Authorization", "Bearer $key")
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .build()

        webSearchClient.newCall(request).execute().use { response ->
            val raw = response.body.string()
            if (!response.isSuccessful) {
                error("Tavily HTTP ${response.code}: $raw")
            }
            val json = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()
                ?: error("Invalid Tavily response")
            val answer = normalizeWhitespace(json.get("answer")?.asString.orEmpty()).ifBlank { null }
            val results = runCatching { json.getAsJsonArray("results") }.getOrNull()
            val items =
                results?.mapNotNull { element ->
                    val obj = runCatching { element.asJsonObject }.getOrNull() ?: return@mapNotNull null
                    val title = normalizeWhitespace(obj.get("title")?.asString.orEmpty())
                    val urlValue = normalizeWhitespace(obj.get("url")?.asString.orEmpty())
                    val text = normalizeWhitespace(obj.get("content")?.asString.orEmpty())
                    if (title.isBlank() || urlValue.isBlank()) null else WebSearchItem(title = title, url = urlValue, text = text)
                }.orEmpty()
            if (items.isEmpty()) error("Tavily returned no usable results.")
            return WebSearchResponse(answer = answer, items = items.take(maxResults))
        }
    }

    private fun searchWithLinkup(
        query: String,
        apiKey: String,
        depthRaw: String,
        maxResults: Int
    ): WebSearchResponse {
        val key = apiKey.trim()
        if (key.isBlank()) error("Linkup API key is required.")
        val depth =
            when (depthRaw.trim().lowercase()) {
                "standard", "deep" -> depthRaw.trim().lowercase()
                else -> "standard"
            }

        val body =
            gson.toJson(
                mapOf(
                    "q" to query,
                    "depth" to depth,
                    "outputType" to "sourcedAnswer",
                    "includeImages" to false
                )
            )
        val request =
            Request.Builder()
                .url("https://api.linkup.so/v1/search")
                .post(body.toRequestBody(strictJsonMediaType))
                .header("Authorization", "Bearer $key")
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .build()

        webSearchClient.newCall(request).execute().use { response ->
            val raw = response.body.string()
            if (!response.isSuccessful) {
                error("Linkup HTTP ${response.code}: $raw")
            }
            val json = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()
                ?: error("Invalid Linkup response")
            val answer = normalizeWhitespace(json.get("answer")?.asString.orEmpty()).ifBlank { null }
            val sources = runCatching { json.getAsJsonArray("sources") }.getOrNull()
            val items =
                sources?.mapNotNull { element ->
                    val obj = runCatching { element.asJsonObject }.getOrNull() ?: return@mapNotNull null
                    val title = normalizeWhitespace(obj.get("name")?.asString.orEmpty())
                    val urlValue = normalizeWhitespace(obj.get("url")?.asString.orEmpty())
                    val text = normalizeWhitespace(obj.get("snippet")?.asString.orEmpty())
                    if (title.isBlank() || urlValue.isBlank()) null else WebSearchItem(title = title, url = urlValue, text = text)
                }.orEmpty()
            if (items.isEmpty()) error("Linkup returned no usable results.")
            return WebSearchResponse(answer = answer, items = items.take(maxResults))
        }
    }

    private fun formatWebSearchContext(engine: String, query: String, result: WebSearchResponse): String {
        val engineLabel =
            when (engine) {
                "exa" -> "Exa"
                "tavily" -> "Tavily"
                "linkup" -> "Linkup"
                else -> "Bing"
            }
        return buildString {
            append("Search engine: ")
            append(engineLabel)
            append('\n')
            append("Query: ")
            append(query)
            append('\n')
            result.answer?.takeIf { it.isNotBlank() }?.let { answer ->
                append('\n')
                append("Engine answer: ")
                append(answer.take(600))
                append('\n')
            }
            append('\n')
            append("Results:\n")
            result.items.take(10).forEachIndexed { index, item ->
                append(index + 1)
                append(". ")
                append(item.title.take(180))
                append('\n')
                append("   ")
                append(item.url.take(420))
                append('\n')
                if (item.text.isNotBlank()) {
                    append("   ")
                    append(item.text.take(420))
                    append('\n')
                }
            }
        }.trim()
    }

    private data class WebSearchResponse(
        val answer: String? = null,
        val items: List<WebSearchItem>
    )

    private data class WebSearchItem(
        val title: String,
        val url: String,
        val text: String
    )

    suspend fun listModels(
        provider: ProviderConfig,
        extraHeaders: List<HttpHeader> = emptyList()
    ): Result<List<String>> {
        return withContext(Dispatchers.IO) {
            val type = provider.type.trim().lowercase()
            when {
                isCodex(provider) -> runCatching { listCodexModels(provider, extraHeaders) }
                isQwenCode(provider) -> runCatching { listQwenCodeModels(provider, extraHeaders) }
                isGrok2Api(provider) -> runCatching { listGrok2ApiModels(provider, extraHeaders) }
                type == "antigravity" -> runCatching { listAntigravityModels(provider, extraHeaders) }
                type == "gemini-cli" -> runCatching { listGeminiCliModels(provider, extraHeaders) }
                else ->
                    runCatching {
                        val url = normalizeProviderApiUrl(provider) + "/models"
                        val effectiveHeaders = buildEffectiveHeaders(provider, extraHeaders)
                        val requestBuilder =
                            Request.Builder()
                                .url(url)
                                .get()

                        applyHeaders(requestBuilder, effectiveHeaders)

                        requestBuilder.header("Accept", "application/json")
                        if (provider.apiKey.isNotBlank() && !hasHeader(effectiveHeaders, "authorization")) {
                            requestBuilder.header("Authorization", "Bearer ${provider.apiKey}")
                        }
                        if (isIFlow(provider) && !hasHeader(effectiveHeaders, "user-agent")) {
                            requestBuilder.header("User-Agent", IFLOW_USER_AGENT)
                        }
                        if (isQwenCode(provider)) {
                            applyQwenCompatibleHeaders(requestBuilder, effectiveHeaders)
                        }
                        if (isGitHubCopilot(provider)) {
                            applyGitHubCopilotHeadersIfNeeded(
                                requestBuilder = requestBuilder,
                                effectiveHeaders = effectiveHeaders,
                                initiator = "user",
                                visionRequest = false
                            )
                        }

                        client.newCall(requestBuilder.build()).execute().use { response ->
                            val raw = response.body.string()
                            if (!response.isSuccessful) {
                                error("HTTP ${response.code}: $raw")
                            }
                            parseModelIdsFromJson(raw)
                        }
                    }
            }
        }
    }

    private fun listCodexModels(provider: ProviderConfig, extraHeaders: List<HttpHeader>): List<String> {
        val base = normalizeCodexBaseUrl(provider)
        val endpoints =
            listOf(
                "$base/models?client_version=$CODEX_CLIENT_VERSION",
                "$base/models",
                "$base/v1/models?client_version=$CODEX_CLIENT_VERSION",
                "$base/v1/models"
            )

        for (url in endpoints) {
            val requestBuilder =
                Request.Builder()
                    .url(url)
                    .get()

            val effectiveHeaders = buildEffectiveHeaders(provider, extraHeaders)
            val isOAuthToken =
                provider.oauthProvider?.trim()?.equals("codex", ignoreCase = true) == true ||
                    !provider.oauthAccessToken.isNullOrBlank() ||
                    !provider.oauthRefreshToken.isNullOrBlank() ||
                    !provider.oauthIdToken.isNullOrBlank()

            applyHeaders(requestBuilder, effectiveHeaders)

            requestBuilder
                .header("Accept", "application/json")
                .apply {
                    if (provider.apiKey.isNotBlank() && !hasHeader(effectiveHeaders, "authorization")) {
                        header("authorization", "Bearer ${provider.apiKey}")
                    }
                }
                .header("originator", "codex_cli_rs")
                .header("User-Agent", CODEX_USER_AGENT)

            if (isOAuthToken) {
                provider.oauthAccountId?.trim()?.takeIf { it.isNotBlank() }?.let { accountId ->
                    requestBuilder.header("ChatGPT-Account-Id", accountId)
                }
            }

            val request = requestBuilder.build()
            val ids =
                runCatching {
                    client.newCall(request).execute().use { response ->
                        val raw = response.body.string()
                        if (!response.isSuccessful) return@use emptyList<String>()
                        val parsed = parseCodexModelsResponse(raw)
                        if (parsed.isNotEmpty()) {
                            parsed.forEach { meta -> codexModelCache[meta.slug] = meta }
                            parsed
                                .filter { it.visibility == "list" && it.supportedInApi }
                                .sortedWith(compareBy<CodexModelMeta> { it.priority }.thenBy { it.slug })
                                .map { it.slug }
                                .distinct()
                        } else {
                            emptyList()
                        }
                    }
                }.getOrElse { emptyList() }

            if (ids.isNotEmpty()) return ids
        }

        return CODEX_DEFAULT_MODELS
    }

    private fun parseCodexModelsResponse(raw: String): List<CodexModelMeta> {
        val json = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull() ?: return emptyList()
        val models = runCatching { json.getAsJsonArray("models") }.getOrNull() ?: return emptyList()
        return models.mapNotNull { el ->
            val obj = runCatching { el.asJsonObject }.getOrNull() ?: return@mapNotNull null
            val slug = obj.get("slug")?.asString?.trim().orEmpty()
            if (slug.isBlank()) return@mapNotNull null
            val visibility = obj.get("visibility")?.asString?.trim()?.lowercase().orEmpty()
            val supportedInApi = obj.get("supported_in_api")?.asBoolean ?: true
            val priority = obj.get("priority")?.asInt ?: Int.MAX_VALUE
            val baseInstructions = obj.get("base_instructions")?.asString?.trim().orEmpty()
            val supportsReasoningSummaries = obj.get("supports_reasoning_summaries")?.asBoolean ?: false
            val defaultReasoningEffort = obj.get("default_reasoning_level")?.asString?.trim()?.lowercase()
            val supportedReasoningEfforts =
                obj.getAsJsonArray("supported_reasoning_levels")
                    ?.mapNotNull { itemEl ->
                        val itemObj = runCatching { itemEl.asJsonObject }.getOrNull() ?: return@mapNotNull null
                        itemObj.get("effort")?.asString?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
                    }
                    ?.distinct()
                    .orEmpty()
            val supportsParallelToolCalls = obj.get("supports_parallel_tool_calls")?.asBoolean ?: false
            val inputModalities =
                obj.getAsJsonArray("input_modalities")
                    ?.mapNotNull { modalityEl ->
                        modalityEl.takeIf { it.isJsonPrimitive }?.asString?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
                    }
                    ?.distinct()
                    .orEmpty()
            CodexModelMeta(
                slug = slug,
                visibility = if (visibility.isBlank()) "list" else visibility,
                supportedInApi = supportedInApi,
                priority = priority,
                baseInstructions = baseInstructions,
                supportsReasoningSummaries = supportsReasoningSummaries,
                defaultReasoningEffort = defaultReasoningEffort,
                supportedReasoningEfforts = supportedReasoningEfforts,
                supportsParallelToolCalls = supportsParallelToolCalls,
                inputModalities = inputModalities
            )
        }
    }

    private fun parseModelIdsFromJson(raw: String): List<String> {
        val element = runCatching { JsonParser.parseString(raw) }.getOrNull() ?: return emptyList()
        if (element.isJsonArray) {
            return element.asJsonArray.mapNotNull { el ->
                when {
                    el.isJsonPrimitive && el.asJsonPrimitive.isString -> el.asString.trim()
                    el.isJsonObject ->
                        el.asJsonObject.get("id")?.asString?.trim()
                            ?: el.asJsonObject.get("slug")?.asString?.trim()
                    else -> null
                }?.takeIf { it.isNotBlank() }
            }.distinct()
        }

        if (!element.isJsonObject) return emptyList()
        val obj = element.asJsonObject

        // OpenAI style: {"object":"list","data":[{"id":"..."}, ...]}
        obj.getAsJsonArray("data")?.let { arr ->
            val ids =
                arr.mapNotNull { el ->
                    if (!el.isJsonObject) return@mapNotNull null
                    el.asJsonObject.get("id")?.asString?.trim()?.takeIf { it.isNotBlank() }
                }
            if (ids.isNotEmpty()) return ids.distinct()
        }

        // Other common shapes: {"models":[...]} or {"items":[...]}
        listOf("models", "items").forEach { key ->
            obj.getAsJsonArray(key)?.let { arr ->
                val ids =
                    arr.mapNotNull { el ->
                        when {
                            el.isJsonPrimitive && el.asJsonPrimitive.isString -> el.asString.trim()
                            el.isJsonObject ->
                                el.asJsonObject.get("id")?.asString?.trim()
                                    ?: el.asJsonObject.get("slug")?.asString?.trim()
                            else -> null
                        }?.takeIf { it.isNotBlank() }
                    }
                if (ids.isNotEmpty()) return ids.distinct()
            }
        }

        return emptyList()
    }

    private fun listQwenCodeModels(provider: ProviderConfig, extraHeaders: List<HttpHeader>): List<String> {
        val url = normalizeProviderApiUrl(provider) + "/models"
        val effectiveHeaders = buildEffectiveHeaders(provider, extraHeaders)
        val requestBuilder =
            Request.Builder()
                .url(url)
                .get()

        applyHeaders(requestBuilder, effectiveHeaders)
        requestBuilder.header("Accept", "application/json")
        if (provider.apiKey.isNotBlank() && !hasHeader(effectiveHeaders, "authorization")) {
            requestBuilder.header("Authorization", "Bearer ${provider.apiKey}")
        }
        applyQwenCompatibleHeaders(requestBuilder, effectiveHeaders)

        return runCatching {
            client.newCall(requestBuilder.build()).execute().use { response ->
                val raw = response.body.string()
                if (!response.isSuccessful) error("HTTP ${response.code}: $raw")
                val ids = parseModelIdsFromJson(raw).distinct()
                if (ids.isNotEmpty()) ids else QWEN_CODE_DEFAULT_MODELS
            }
        }.getOrElse { QWEN_CODE_DEFAULT_MODELS }
    }

    suspend fun chatCompletions(
        provider: ProviderConfig,
        modelId: String,
        messages: List<Message>,
        extraHeaders: List<HttpHeader> = emptyList(),
        reasoningEffort: String? = null,
        enableThinking: Boolean? = null,
        maxTokens: Int? = null
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val effectiveHeaders = buildEffectiveHeaders(provider, extraHeaders)
                val copilotInitiator =
                    if (messages.lastOrNull()?.role?.trim()?.lowercase() == "user") "user" else "agent"
                val copilotVisionRequest = isVisionRequest(messages)
                if (isGrokReverseDirect(provider)) {
                    return@runCatching grokReverseChatCompletion(
                        provider = provider,
                        modelId = modelId,
                        messages = messages,
                        extraHeaders = effectiveHeaders
                    )
                }
                val payload =
                    linkedMapOf<String, Any>(
                        "model" to modelId,
                        "messages" to toOpenAIChatMessages(provider, messages)
                    )
                maxTokens
                    ?.coerceIn(256, 16_384)
                    ?.let { safeMaxTokens ->
                        payload["max_tokens"] = safeMaxTokens
                    }
                enableThinking?.let { thinkingEnabled ->
                    payload["enable_thinking"] = thinkingEnabled
                    val extraBody = linkedMapOf<String, Any>("enable_thinking" to thinkingEnabled)
                    if (isKimiProvider(provider)) {
                        extraBody["thinking"] =
                            mapOf(
                                "type" to if (thinkingEnabled) "enabled" else "disabled"
                            )
                    }
                    payload["extra_body"] = extraBody
                }
                if (isGrok2Api(provider)) {
                    normalizeReasoningEffort(reasoningEffort)?.let { payload["reasoning_effort"] = it }
                }
                if (isQwenCode(provider)) {
                    val qwenTools = buildQwenBridgeTools(messages)
                    payload["tools"] = qwenTools
                    payload["tool_choice"] = if (isNoopQwenToolList(qwenTools)) "none" else "auto"
                }
                val body = gson.toJson(payload)
                val fallbackBodyWithoutThinkingParams =
                    if (enableThinking != null && (payload.containsKey("enable_thinking") || payload.containsKey("extra_body"))) {
                        val fallbackPayload = LinkedHashMap(payload)
                        fallbackPayload.remove("enable_thinking")
                        fallbackPayload.remove("extra_body")
                        gson.toJson(fallbackPayload)
                    } else {
                        null
                    }
                val baseCandidates = providerApiBaseCandidates(provider)
                ensureGrokGatewayTokenSynced(provider, effectiveHeaders, baseCandidates)
                val attemptErrors = mutableListOf<String>()
                var lastError: Throwable? = null
                for ((index, baseUrl) in baseCandidates.withIndex()) {
                    try {
                        val url = baseUrl + "/chat/completions"
                        fun buildRequest(requestBody: String): Request {
                            val requestBuilder = Request.Builder().url(url)

                            applyHeaders(requestBuilder, effectiveHeaders)
                            applyProviderAuthorizationIfNeeded(requestBuilder, provider, effectiveHeaders)
                            if (isIFlow(provider) && !hasHeader(effectiveHeaders, "user-agent")) {
                                requestBuilder.header("User-Agent", IFLOW_USER_AGENT)
                            }
                            if (isQwenCode(provider)) {
                                applyQwenCompatibleHeaders(requestBuilder, effectiveHeaders)
                            }
                            if (isGitHubCopilot(provider)) {
                                applyGitHubCopilotHeadersIfNeeded(
                                    requestBuilder = requestBuilder,
                                    effectiveHeaders = effectiveHeaders,
                                    initiator = copilotInitiator,
                                    visionRequest = copilotVisionRequest
                                )
                            }

                            requestBuilder.header("Content-Type", "application/json")
                            return requestBuilder
                                .post(requestBody.toRequestBody(jsonMediaType))
                                .build()
                        }

                        val initialResponse = client.newCall(buildRequest(body)).execute()
                        val responseToUse =
                            if (!initialResponse.isSuccessful && fallbackBodyWithoutThinkingParams != null) {
                                val initialCode = initialResponse.code
                                val initialErrorBody = initialResponse.body.string()
                                val shouldRetryWithoutParam =
                                    shouldRetryWithoutEnableThinking(initialCode, initialErrorBody)
                                initialResponse.close()
                                if (!shouldRetryWithoutParam) {
                                    throw IllegalStateException("HTTP $initialCode @ $url: $initialErrorBody")
                                }

                                val fallbackResponse =
                                    client.newCall(buildRequest(fallbackBodyWithoutThinkingParams)).execute()
                                if (!fallbackResponse.isSuccessful) {
                                    val fallbackCode = fallbackResponse.code
                                    val fallbackErrorBody = fallbackResponse.body.string()
                                    fallbackResponse.close()
                                    throw IllegalStateException("HTTP $fallbackCode @ $url: $fallbackErrorBody")
                                }
                                fallbackResponse
                            } else {
                                initialResponse
                            }

                        responseToUse.use { response ->
                            val raw = response.body.string()
                            if (!response.isSuccessful) {
                                error("HTTP ${response.code} @ $url: $raw")
                            }

                            val parsed = gson.fromJson(raw, OpenAIChatCompletionsResponse::class.java)
                            val text = parsed.choices?.firstOrNull()?.message?.content?.trim().orEmpty()
                            val toolCallTags = extractToolCallTagsFromOpenAIResponse(raw)
                            return@runCatching mergeTextAndToolCallTags(text, toolCallTags)
                        }
                    } catch (t: Throwable) {
                        lastError = t
                        if (!isGrok2Api(provider)) throw t
                        attemptErrors += formatGrokAttemptError(baseUrl, t)
                        val shouldRetry = shouldContinueGrokFallback(t, index < baseCandidates.lastIndex)
                        if (!shouldRetry) break
                    }
                }
                if (isGrok2Api(provider) && attemptErrors.isNotEmpty()) {
                    throw IllegalStateException(
                        "Grok 网关请求失败（${attemptErrors.size} 次尝试）：${attemptErrors.joinToString(" | ")}",
                        lastError
                    )
                }
                throw (lastError ?: IllegalStateException("chat_completions request failed"))
            }
        }
    }

    /**
     * 流式聊天完成 - 返回 Flow，每次发射一个增量内容片段
     */
    fun chatCompletionsStream(
        provider: ProviderConfig,
        modelId: String,
        messages: List<Message>,
        extraHeaders: List<HttpHeader> = emptyList(),
        reasoningEffort: String? = null,
        enableThinking: Boolean? = null,
        maxTokens: Int? = null,
        conversationId: String? = null
    ): Flow<ChatStreamDelta> {
        val type = provider.type.trim().lowercase()
        return when {
            isCodex(provider) -> codexResponsesStream(provider, modelId, messages, extraHeaders, reasoningEffort, conversationId)
            isGrokReverseDirect(provider) -> grokReverseChatCompletionsStream(provider, modelId, messages, extraHeaders, reasoningEffort)
            type == "antigravity" -> antigravityStream(provider, modelId, messages, extraHeaders)
            type == "gemini-cli" -> geminiCliStream(provider, modelId, messages, extraHeaders)
            else -> openAIChatCompletionsStream(provider, modelId, messages, extraHeaders, reasoningEffort, enableThinking, maxTokens)
        }
    }

    private fun openAIChatCompletionsStream(
        provider: ProviderConfig,
        modelId: String,
        messages: List<Message>,
        extraHeaders: List<HttpHeader>,
        reasoningEffort: String? = null,
        enableThinking: Boolean? = null,
        maxTokens: Int? = null
    ): Flow<ChatStreamDelta> = flow {
        val effectiveHeaders = buildEffectiveHeaders(provider, extraHeaders)
        val copilotInitiator =
            if (messages.lastOrNull()?.role?.trim()?.lowercase() == "user") "user" else "agent"
        val copilotVisionRequest = isVisionRequest(messages)
        val payload =
            linkedMapOf<String, Any>(
                "model" to modelId,
                "messages" to toOpenAIChatMessages(provider, messages),
                "stream" to true
            )
        maxTokens
            ?.coerceIn(256, 16_384)
            ?.let { safeMaxTokens ->
                payload["max_tokens"] = safeMaxTokens
            }
        if (isQwenCode(provider)) {
            val qwenTools = buildQwenBridgeTools(messages)
            payload["tools"] = qwenTools
            payload["tool_choice"] = if (isNoopQwenToolList(qwenTools)) "none" else "auto"
            payload["stream_options"] = mapOf("include_usage" to true)
        }
        enableThinking?.let { thinkingEnabled ->
            // Send both forms for broader OpenAI-compatible provider coverage:
            // - top-level enable_thinking
            // - extra_body.enable_thinking (qwen-code / dashscope style)
            payload["enable_thinking"] = thinkingEnabled

            val extraBody = linkedMapOf<String, Any>("enable_thinking" to thinkingEnabled)
            // Kimi native endpoint also accepts thinking.type.
            if (isKimiProvider(provider)) {
                extraBody["thinking"] =
                    mapOf(
                        "type" to if (thinkingEnabled) "enabled" else "disabled"
                    )
            }
            payload["extra_body"] = extraBody
        }
        if (isGrok2Api(provider)) {
            normalizeReasoningEffort(reasoningEffort)?.let { payload["reasoning_effort"] = it }
        }
        val body = gson.toJson(payload)
        val fallbackBodyWithoutThinkingParams =
            if (enableThinking != null && (payload.containsKey("enable_thinking") || payload.containsKey("extra_body"))) {
                val fallbackPayload = LinkedHashMap(payload)
                fallbackPayload.remove("enable_thinking")
                fallbackPayload.remove("extra_body")
                gson.toJson(fallbackPayload)
            } else {
                null
            }
        val baseCandidates = providerApiBaseCandidates(provider)
        ensureGrokGatewayTokenSynced(provider, effectiveHeaders, baseCandidates)
        val attemptErrors = mutableListOf<String>()
        var lastError: Throwable? = null

        for ((index, baseUrl) in baseCandidates.withIndex()) {
            try {
                val url = baseUrl + "/chat/completions"
                fun buildStreamRequest(requestBody: String): Request {
                    val requestBuilder = Request.Builder().url(url)
                    applyHeaders(requestBuilder, effectiveHeaders)
                    applyProviderAuthorizationIfNeeded(requestBuilder, provider, effectiveHeaders)
                    if (isIFlow(provider) && !hasHeader(effectiveHeaders, "user-agent")) {
                        requestBuilder.header("User-Agent", IFLOW_USER_AGENT)
                    }
                    if (isQwenCode(provider)) {
                        applyQwenCompatibleHeaders(requestBuilder, effectiveHeaders)
                    }
                    if (isGitHubCopilot(provider)) {
                        applyGitHubCopilotHeadersIfNeeded(
                            requestBuilder = requestBuilder,
                            effectiveHeaders = effectiveHeaders,
                            initiator = copilotInitiator,
                            visionRequest = copilotVisionRequest
                        )
                    }
                    requestBuilder
                        .header("Content-Type", "application/json")
                        .header("Accept", "text/event-stream")
                    return requestBuilder
                        .post(requestBody.toRequestBody(jsonMediaType))
                        .build()
                }

                val initialResponse = client.newCall(buildStreamRequest(body)).execute()
                val responseToUse =
                    if (!initialResponse.isSuccessful && fallbackBodyWithoutThinkingParams != null) {
                        val initialCode = initialResponse.code
                        val initialErrorBody = initialResponse.body.string()
                        val shouldRetryWithoutParam =
                            shouldRetryWithoutEnableThinking(initialCode, initialErrorBody)
                        initialResponse.close()
                        if (!shouldRetryWithoutParam) {
                            throw IllegalStateException("HTTP $initialCode @ $url: $initialErrorBody")
                        }

                        val fallbackResponse =
                            client.newCall(buildStreamRequest(fallbackBodyWithoutThinkingParams)).execute()
                        if (!fallbackResponse.isSuccessful) {
                            val fallbackCode = fallbackResponse.code
                            val fallbackErrorBody = fallbackResponse.body.string()
                            fallbackResponse.close()
                            throw IllegalStateException("HTTP $fallbackCode @ $url: $fallbackErrorBody")
                        }
                        fallbackResponse
                    } else {
                        initialResponse
                    }

                responseToUse.use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body.string()
                        throw IllegalStateException("HTTP ${response.code} @ $url: $errorBody")
                    }

                    val contentType = response.header("Content-Type").orEmpty()
                    if (!contentType.contains("text/event-stream", ignoreCase = true)) {
                        val raw = response.body.string()
                        val parsed = runCatching { gson.fromJson(raw, OpenAIChatCompletionsResponse::class.java) }.getOrNull()
                        val text = parsed?.choices?.firstOrNull()?.message?.content?.trim().orEmpty()
                        val toolCallTags = extractToolCallTagsFromOpenAIResponse(raw)
                        val mergedText = mergeTextAndToolCallTags(text, toolCallTags)
                        if (mergedText.isNotEmpty()) {
                            emit(ChatStreamDelta(content = mergedText))
                        }
                        return@flow
                    }

                    val source = response.body.source()
                    val nativeToolCallStates = linkedMapOf<Int, NativeToolCallState>()

                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: continue
                        if (line.startsWith("data:")) {
                            val data = line.removePrefix("data:").trimStart()
                            if (data == "[DONE]") break

                            val finishReason = collectNativeToolCallsFromChunk(data, nativeToolCallStates)

                            try {
                                val chunk = gson.fromJson(data, OpenAIStreamChunk::class.java)
                                val delta = chunk.choices?.firstOrNull()?.delta
                                val content = delta?.content
                                val reasoning = delta?.reasoning_content ?: delta?.reasoning ?: delta?.thinking
                                val normalizedFinishReason = finishReason?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
                                if (!content.isNullOrEmpty() || !reasoning.isNullOrEmpty() || normalizedFinishReason != null) {
                                    emit(
                                        ChatStreamDelta(
                                            content = content,
                                            reasoning = reasoning,
                                            finishReason = normalizedFinishReason
                                        )
                                    )
                                }
                            } catch (_: Exception) {
                                parseOpenAIStreamDeltaLenient(data)?.let { emit(it) }
                            }

                            if ((finishReason == "tool_calls" || finishReason == "stop") && nativeToolCallStates.isNotEmpty()) {
                                consumeNativeToolCallTags(nativeToolCallStates)?.let { tags ->
                                    emit(ChatStreamDelta(content = tags))
                                }
                            }
                        }
                    }

                    if (nativeToolCallStates.isNotEmpty()) {
                        consumeNativeToolCallTags(nativeToolCallStates)?.let { tags ->
                            emit(ChatStreamDelta(content = tags))
                        }
                    }
                }

                return@flow
            } catch (t: Throwable) {
                lastError = t
                if (isGrok2Api(provider)) {
                    attemptErrors += formatGrokAttemptError(baseUrl, t)
                }
                val shouldRetry = isGrok2Api(provider) && shouldContinueGrokFallback(t, index < baseCandidates.lastIndex)
                if (!shouldRetry) break
            }
        }

        if (isGrok2Api(provider)) {
            val fallbackText =
                chatCompletions(
                    provider = provider,
                    modelId = modelId,
                    messages = messages,
                    extraHeaders = extraHeaders
                ).getOrElse { fallbackError ->
                    if (attemptErrors.isNotEmpty()) {
                        throw IllegalStateException(
                            "Grok 网关流式失败并回退普通请求仍失败：${attemptErrors.joinToString(" | ")}",
                            fallbackError
                        )
                    }
                    throw (lastError ?: fallbackError)
                }
            if (fallbackText.isNotBlank()) {
                emit(ChatStreamDelta(content = fallbackText))
                return@flow
            }
        }

        if (isGrok2Api(provider) && attemptErrors.isNotEmpty()) {
            throw IllegalStateException(
                "Grok 网关流式请求失败（${attemptErrors.size} 次尝试）：${attemptErrors.joinToString(" | ")}",
                lastError
            )
        }
        throw (lastError ?: IllegalStateException("chat_completions_stream request failed"))
    }.flowOn(Dispatchers.IO)

    private fun parseOpenAIStreamDeltaLenient(data: String): ChatStreamDelta? {
        val json = runCatching { JsonParser.parseString(data).asJsonObject }.getOrNull() ?: return null
        val choices = runCatching { json.getAsJsonArray("choices") }.getOrNull() ?: return null
        if (choices.size() == 0) return null
        val firstChoice = runCatching { choices[0].asJsonObject }.getOrNull() ?: return null
        val finishReason =
            firstChoice.get("finish_reason")
                ?.takeIf { it.isJsonPrimitive }
                ?.asString
                ?.trim()
                ?.lowercase()
                ?.takeIf { it.isNotBlank() }
        val delta = runCatching { firstChoice.getAsJsonObject("delta") }.getOrNull() ?: return null

        val content = delta.get("content")?.takeIf { it.isJsonPrimitive }?.asString?.trim()
        val reasoning =
            listOf("reasoning_content", "reasoning", "thinking")
                .firstNotNullOfOrNull { key ->
                    delta.get(key)?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotEmpty() }
                }

        if (content.isNullOrEmpty() && reasoning.isNullOrEmpty() && finishReason == null) return null
        return ChatStreamDelta(content = content, reasoning = reasoning, finishReason = finishReason)
    }

    private data class NativeToolCallState(
        var id: String = "",
        var name: String = "",
        val arguments: StringBuilder = StringBuilder()
    )

    private fun mergeTextAndToolCallTags(text: String, toolCallTags: String): String {
        val cleanText = text.trim()
        val cleanTags = toolCallTags.trim()
        if (cleanText.isBlank()) return cleanTags
        if (cleanTags.isBlank()) return cleanText
        return "$cleanText\n\n$cleanTags"
    }

    private fun collectNativeToolCallsFromChunk(
        data: String,
        states: MutableMap<Int, NativeToolCallState>
    ): String? {
        val root = runCatching { JsonParser.parseString(data).asJsonObject }.getOrNull() ?: return null
        val choices = runCatching { root.getAsJsonArray("choices") }.getOrNull() ?: return null
        if (choices.size() == 0) return null
        val firstChoice = runCatching { choices[0].asJsonObject }.getOrNull() ?: return null
        val finishReason =
            firstChoice.get("finish_reason")
                ?.takeIf { it.isJsonPrimitive }
                ?.asString
                ?.trim()
                ?.lowercase()

        val delta = runCatching { firstChoice.getAsJsonObject("delta") }.getOrNull() ?: return finishReason
        val toolCalls = runCatching { delta.getAsJsonArray("tool_calls") }.getOrNull() ?: return finishReason
        toolCalls.forEachIndexed { fallbackIndex, element ->
            val item = runCatching { element.asJsonObject }.getOrNull() ?: return@forEachIndexed
            val index =
                runCatching { item.get("index")?.takeIf { it.isJsonPrimitive }?.asInt }.getOrNull()
                    ?: fallbackIndex
            val state = states.getOrPut(index) { NativeToolCallState() }
            item.get("id")
                ?.takeIf { it.isJsonPrimitive }
                ?.asString
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { state.id = it }
            val function = runCatching { item.getAsJsonObject("function") }.getOrNull() ?: return@forEachIndexed
            function.get("name")
                ?.takeIf { it.isJsonPrimitive }
                ?.asString
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { namePart ->
                    state.name =
                        when {
                            state.name.isBlank() -> namePart
                            state.name.endsWith(namePart) -> state.name
                            else -> state.name + namePart
                        }
                }
            function.get("arguments")
                ?.takeIf { it.isJsonPrimitive }
                ?.asString
                ?.let { part ->
                    if (part.isNotEmpty()) state.arguments.append(part)
                }
        }
        return finishReason
    }

    private fun extractToolCallTagsFromOpenAIResponse(raw: String): String {
        val root = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull() ?: return ""
        val choices = runCatching { root.getAsJsonArray("choices") }.getOrNull() ?: return ""
        if (choices.size() == 0) return ""
        val firstChoice = runCatching { choices[0].asJsonObject }.getOrNull() ?: return ""
        val message = runCatching { firstChoice.getAsJsonObject("message") }.getOrNull() ?: return ""
        val toolCalls = runCatching { message.getAsJsonArray("tool_calls") }.getOrNull() ?: return ""
        if (toolCalls.size() == 0) return ""

        val states = linkedMapOf<Int, NativeToolCallState>()
        toolCalls.forEachIndexed { index, element ->
            val item = runCatching { element.asJsonObject }.getOrNull() ?: return@forEachIndexed
            val state = NativeToolCallState()
            state.id =
                item.get("id")
                    ?.takeIf { it.isJsonPrimitive }
                    ?.asString
                    ?.trim()
                    .orEmpty()
            val function = runCatching { item.getAsJsonObject("function") }.getOrNull()
            state.name =
                function?.get("name")
                    ?.takeIf { it.isJsonPrimitive }
                    ?.asString
                    ?.trim()
                    .orEmpty()
            val argsValue = function?.get("arguments")
            when {
                argsValue == null -> Unit
                argsValue.isJsonPrimitive -> state.arguments.append(argsValue.asString)
                argsValue.isJsonObject || argsValue.isJsonArray -> state.arguments.append(argsValue.toString())
            }
            states[index] = state
        }

        return consumeNativeToolCallTags(states).orEmpty()
    }

    private fun consumeNativeToolCallTags(states: MutableMap<Int, NativeToolCallState>): String? {
        if (states.isEmpty()) return null
        val tags =
            states.toSortedMap()
                .values
                .mapNotNull { buildToolCallTagFromNativeState(it) }
                .filter { it.isNotBlank() }
        states.clear()
        if (tags.isEmpty()) return null
        return tags.joinToString("\n")
    }

    private fun buildToolCallTagFromNativeState(state: NativeToolCallState): String? {
        val canonicalPayload = toCanonicalToolCallPayload(state) ?: return null
        return "<tool_call>${gson.toJson(canonicalPayload)}</tool_call>"
    }

    private fun toCanonicalToolCallPayload(state: NativeToolCallState): Map<String, Any?>? {
        val functionName = state.name.trim()
        if (functionName.isBlank()) return null
        val argsObject = parseArgumentsJsonObject(state.arguments.toString())

        if (functionName.equals("app_developer", ignoreCase = true)) {
            return mapOf(
                "serverId" to "builtin_app_developer",
                "toolName" to "app_developer",
                "arguments" to argsObject
            )
        }

        val explicitToolName =
            extractStringField(
                argsObject,
                "toolName",
                "tool_name",
                "tool",
                "name"
            )

        val serverId =
            extractStringField(
                argsObject,
                "serverId",
                "server_id",
                "server",
                "mcpId",
                "mcp_id",
                "id"
            ).orEmpty()

        val rawArgumentsValue =
            listOf("arguments", "args", "input", "params", "parameters")
                .asSequence()
                .mapNotNull { key -> argsObject[key] }
                .firstOrNull()
        val normalizedArguments =
            when {
                rawArgumentsValue == null -> argsObject
                rawArgumentsValue is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    rawArgumentsValue as Map<String, Any?>
                }
                rawArgumentsValue is String -> parseArgumentsJsonObject(rawArgumentsValue)
                else -> argsObject
            }

        val toolName =
            when {
                functionName.equals("mcp_call", ignoreCase = true) -> explicitToolName
                functionName.equals("tool_call", ignoreCase = true) -> explicitToolName
                explicitToolName.isNullOrBlank() -> functionName
                else -> explicitToolName
            }?.trim().orEmpty()
        if (toolName.isBlank()) return null

        return mapOf(
            "serverId" to serverId,
            "toolName" to toolName,
            "arguments" to normalizedArguments
        )
    }

    private fun parseArgumentsJsonObject(raw: String): Map<String, Any?> {
        val normalized = raw.trim()
        if (normalized.isBlank()) return emptyMap()
        val firstPass = runCatching { JsonParser.parseString(normalized) }.getOrNull() ?: return mapOf("input" to normalized)
        val resolved =
            when {
                firstPass.isJsonObject -> firstPass.asJsonObject
                firstPass.isJsonPrimitive && firstPass.asJsonPrimitive.isString ->
                    runCatching { JsonParser.parseString(firstPass.asString).asJsonObject }.getOrNull()
                else -> null
            } ?: return mapOf("input" to normalized)

        return resolved.entrySet().associate { entry ->
            entry.key to entry.value.toKotlinValue()
        }
    }

    private fun extractStringField(
        map: Map<String, Any?>,
        vararg keys: String
    ): String? {
        return keys.asSequence()
            .mapNotNull { key ->
                map.entries.firstOrNull { entry -> entry.key.equals(key, ignoreCase = true) }?.value
            }
            .mapNotNull { value -> value.toString().trim() }
            .firstOrNull { value -> value.isNotBlank() }
    }

    private fun com.google.gson.JsonElement.toKotlinValue(): Any? {
        return when {
            isJsonNull -> null
            isJsonPrimitive -> {
                val primitive = asJsonPrimitive
                when {
                    primitive.isBoolean -> primitive.asBoolean
                    primitive.isNumber -> primitive.asNumber
                    else -> primitive.asString
                }
            }
            isJsonArray -> asJsonArray.map { it.toKotlinValue() }
            isJsonObject -> asJsonObject.entrySet().associate { it.key to it.value.toKotlinValue() }
            else -> null
        }
    }

    private fun codexResponsesStream(
        provider: ProviderConfig,
        modelId: String,
        messages: List<Message>,
        extraHeaders: List<HttpHeader>,
        reasoningEffort: String?,
        conversationId: String?
    ): Flow<ChatStreamDelta> = flow {
        val url = normalizeCodexBaseUrl(provider) + "/responses"
        val sessionId = conversationId?.trim()?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val effectiveHeaders = buildEffectiveHeaders(provider, extraHeaders)

        val meta = codexModelCache[modelId] ?: runCatching {
            // Best-effort: refresh cache once if missing.
            listCodexModels(provider, extraHeaders)
            codexModelCache[modelId]
        }.getOrNull()

        val baseInstructions =
            meta?.baseInstructions?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: "You are a helpful assistant."

        val shouldIncludeReasoning = meta?.supportsReasoningSummaries == true
        val effectiveReasoningEffort =
            reasoningEffort?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
                ?: meta?.defaultReasoningEffort?.trim()?.lowercase()?.takeIf { it.isNotBlank() }

        val reasoningPayload: Map<String, Any>? =
            if (shouldIncludeReasoning) {
                buildMap {
                    effectiveReasoningEffort?.let { put("effort", it) }
                    put("summary", "auto")
                }
            } else {
                null
            }

        val includeList =
            if (reasoningPayload != null) listOf("reasoning.encrypted_content") else emptyList()

        val supportsParallelToolCalls = meta?.supportsParallelToolCalls ?: false

        val input =
            messages.mapNotNull { message ->
                val role =
                    when (message.role) {
                        "system" -> "developer"
                        "developer" -> "developer"
                        "assistant" -> "assistant"
                        "user" -> "user"
                        else -> message.role.trim()
                    }.trim()
                if (role.isBlank()) return@mapNotNull null

                val content =
                    if (role == "assistant") {
                        listOf(mapOf("type" to "output_text", "text" to message.content))
                    } else {
                        val parts = mutableListOf<Map<String, Any>>()

                        // Handle text content (extract images from markdown first for backward compatibility)
                        val (text, markdownImages) = extractMarkdownImages(message.content)
                        if (text.isNotBlank()) {
                            parts.add(mapOf("type" to "input_text", "text" to text))
                        }

                        // Add images from markdown content
                        markdownImages.forEach { url ->
                            parts.add(mapOf("type" to "input_image", "image_url" to url))
                        }

                        // Add images from message attachments (new way)
                        message.attachments?.forEach { attachment ->
                            if (attachment.type == "image" && attachment.url.isNotBlank()) {
                                parts.add(mapOf("type" to "input_image", "image_url" to attachment.url))
                            }
                        }

                        if (parts.isEmpty()) {
                            parts.add(mapOf("type" to "input_text", "text" to ""))
                        }
                        parts
                    }

                mapOf(
                    "type" to "message",
                    "role" to role,
                    "content" to content
                )
            }

        val requestPayload = mutableMapOf<String, Any>(
            "model" to modelId,
            "instructions" to baseInstructions,
            "tools" to emptyList<Any>(),
            "tool_choice" to "auto",
            "stream" to true,
            "store" to false,
            "input" to input,
            "parallel_tool_calls" to supportsParallelToolCalls,
            "prompt_cache_key" to sessionId
        )

        if (reasoningPayload != null) {
            requestPayload["reasoning"] = reasoningPayload
        }
        if (includeList.isNotEmpty()) {
            requestPayload["include"] = includeList
        }

        val body = gson.toJson(requestPayload)

        val requestBuilder =
            Request.Builder()
                .url(url)
                .post(body.toByteArray(Charsets.UTF_8).toRequestBody(strictJsonMediaType))

        val isOAuthToken =
            provider.oauthProvider?.trim()?.equals("codex", ignoreCase = true) == true ||
                !provider.oauthAccessToken.isNullOrBlank() ||
                !provider.oauthRefreshToken.isNullOrBlank() ||
                !provider.oauthIdToken.isNullOrBlank()

        applyHeaders(requestBuilder, effectiveHeaders)

        requestBuilder
            .apply {
                if (provider.apiKey.isNotBlank() && !hasHeader(effectiveHeaders, "authorization")) {
                    header("authorization", "Bearer ${provider.apiKey}")
                }
            }
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .header("originator", "codex_cli_rs")
            .header("session_id", sessionId)
            .header("User-Agent", CODEX_USER_AGENT)

        if (isOAuthToken) {
            provider.oauthAccountId?.trim()?.takeIf { it.isNotBlank() }?.let { accountId ->
                requestBuilder.header("ChatGPT-Account-Id", accountId)
            }
        }

        val request = requestBuilder.build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body.string()
                val sentContentType = request.body?.contentType()?.toString().orEmpty()
                throw IllegalStateException(
                    "HTTP ${response.code}: $errorBody (url=$url, content-type=$sentContentType)"
                )
            }

            val source = response.body.source()
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: continue
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") break

                val json = runCatching { JsonParser.parseString(data).asJsonObject }.getOrNull() ?: continue
                val type = json.get("type")?.asString?.trim().orEmpty()
                when (type) {
                    "response.output_text.delta" -> {
                        val delta = json.get("delta")?.asString ?: ""
                        if (delta.isNotEmpty()) emit(ChatStreamDelta(content = delta))
                    }
                    "response.reasoning_text.delta" -> {
                        val delta = json.get("delta")?.asString ?: ""
                        if (delta.isNotEmpty()) emit(ChatStreamDelta(reasoning = delta))
                    }
                    "response.reasoning_summary_text.delta" -> {
                        val delta = json.get("delta")?.asString ?: ""
                        if (delta.isNotEmpty()) emit(ChatStreamDelta(reasoning = delta))
                    }
                    "response.reasoning_summary_text.done" -> emit(ChatStreamDelta(reasoning = "\n\n"))
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun antigravityStream(
        provider: ProviderConfig,
        modelId: String,
        messages: List<Message>,
        extraHeaders: List<HttpHeader>
    ): Flow<ChatStreamDelta> = flow {
        val effectiveHeaders = buildEffectiveHeaders(provider, extraHeaders)
        val project = provider.oauthProjectId?.trim().takeIf { !it.isNullOrBlank() } ?: generateAntigravityProjectId()

        val systemText =
            messages.filter { it.role == "system" || it.role == "developer" }
                .joinToString("\n\n") { it.content }
                .trim()

        val contents =
            messages.filterNot { it.role == "system" || it.role == "developer" }
                .mapNotNull { message ->
                    val role =
                        when (message.role) {
                            "assistant" -> "model"
                            "user" -> "user"
                            else -> null
                        } ?: return@mapNotNull null
                    mapOf("role" to role, "parts" to toGeminiParts(message.content, message.attachments))
                }

        val requestPayload =
            mutableMapOf<String, Any>(
                "contents" to contents,
                "sessionId" to generateAntigravitySessionId(messages)
            )

        if (systemText.isNotBlank()) {
            requestPayload["systemInstruction"] = mapOf("role" to "user", "parts" to listOf(mapOf("text" to systemText)))
        }

        val body =
            gson.toJson(
                mapOf(
                    "project" to project,
                    "request" to requestPayload,
                    "model" to modelId,
                    "userAgent" to "antigravity",
                    "requestType" to "agent",
                    "requestId" to "agent-${UUID.randomUUID()}"
                )
            )

        val baseUrls = antigravityBaseUrlFallbackOrder(provider)
        var lastError: String? = null

        for ((index, baseUrl) in baseUrls.withIndex()) {
            var completed = false
            val url = baseUrl.trimEnd('/') + "/v1internal:streamGenerateContent?alt=sse"
            val requestBuilder = Request.Builder().url(url)

            applyHeaders(requestBuilder, effectiveHeaders)

            if (provider.apiKey.isNotBlank() && !hasHeader(effectiveHeaders, "authorization")) {
                requestBuilder.header("Authorization", "Bearer ${provider.apiKey}")
            }
            if (!hasHeader(effectiveHeaders, "user-agent")) {
                requestBuilder.header("User-Agent", ANTIGRAVITY_USER_AGENT)
            }

            requestBuilder
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")

            val request = requestBuilder.post(body.toRequestBody(jsonMediaType)).build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body.string()
                    lastError = "HTTP ${response.code}: $errorBody"
                    if (index + 1 < baseUrls.size) return@use
                    throw IllegalStateException(lastError)
                }

                completed = true
                val source = response.body.source()
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: continue
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break

                    val json = runCatching { JsonParser.parseString(data).asJsonObject }.getOrNull() ?: continue
                    val responseObj = json.getAsJsonObject("response") ?: json
                    val candidates = responseObj.getAsJsonArray("candidates") ?: continue
                    if (candidates.size() == 0) continue
                    val first = candidates[0].asJsonObject
                    val contentObj = first.getAsJsonObject("content") ?: continue
                    val parts = contentObj.getAsJsonArray("parts") ?: continue
                    parts.forEach { partEl ->
                        val part = runCatching { partEl.asJsonObject }.getOrNull() ?: return@forEach
                        val text = part.get("text")?.asString ?: return@forEach
                        if (text.isEmpty()) return@forEach
                        val isThought = part.get("thought")?.asBoolean ?: false
                        if (isThought) emit(ChatStreamDelta(reasoning = text)) else emit(ChatStreamDelta(content = text))
                    }
                }
            }

            if (completed) return@flow
        }
    }.flowOn(Dispatchers.IO)

    private fun geminiCliStream(
        provider: ProviderConfig,
        modelId: String,
        messages: List<Message>,
        extraHeaders: List<HttpHeader>
    ): Flow<ChatStreamDelta> = flow {
        val project = provider.oauthProjectId?.trim().orEmpty()
        if (project.isBlank()) {
            throw IllegalStateException("Missing project id")
        }

        val effectiveHeaders = buildEffectiveHeaders(provider, extraHeaders)
        val url = normalizeProviderApiUrl(provider) + "/v1internal:streamGenerateContent?alt=sse"

        val systemText =
            messages.filter { it.role == "system" || it.role == "developer" }
                .joinToString("\n\n") { it.content }
                .trim()

        val contents =
            messages.filterNot { it.role == "system" || it.role == "developer" }
                .mapNotNull { message ->
                    val role =
                        when (message.role) {
                            "assistant" -> "model"
                            "user" -> "user"
                            else -> null
                        } ?: return@mapNotNull null
                    mapOf("role" to role, "parts" to toGeminiParts(message.content, message.attachments))
                }

        val requestPayload = mutableMapOf<String, Any>("contents" to contents)
        if (systemText.isNotBlank()) {
            requestPayload["systemInstruction"] = mapOf("role" to "user", "parts" to listOf(mapOf("text" to systemText)))
        }

        val body = gson.toJson(mapOf("project" to project, "request" to requestPayload, "model" to modelId))

        val requestBuilder = Request.Builder().url(url)

        applyHeaders(requestBuilder, effectiveHeaders)

        if (provider.apiKey.isNotBlank() && !hasHeader(effectiveHeaders, "authorization")) {
            requestBuilder.header("Authorization", "Bearer ${provider.apiKey}")
        }
        if (!hasHeader(effectiveHeaders, "user-agent")) {
            requestBuilder.header("User-Agent", GEMINI_CLI_USER_AGENT)
        }

        requestBuilder
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .header("X-Goog-Api-Client", GEMINI_CLI_API_CLIENT)
            .header("Client-Metadata", GEMINI_CLI_CLIENT_METADATA)

        val request = requestBuilder.post(body.toRequestBody(jsonMediaType)).build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body.string()
                throw IllegalStateException("HTTP ${response.code}: $errorBody")
            }

            val source = response.body.source()
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: continue
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") break

                val json = runCatching { JsonParser.parseString(data).asJsonObject }.getOrNull() ?: continue
                val responseObj = json.getAsJsonObject("response") ?: json
                val candidates = responseObj.getAsJsonArray("candidates") ?: continue
                if (candidates.size() == 0) continue
                val first = candidates[0].asJsonObject
                val contentObj = first.getAsJsonObject("content") ?: continue
                val parts = contentObj.getAsJsonArray("parts") ?: continue
                parts.forEach { partEl ->
                    val part = runCatching { partEl.asJsonObject }.getOrNull() ?: return@forEach
                    val text = part.get("text")?.asString ?: return@forEach
                    if (text.isEmpty()) return@forEach
                    val isThought = part.get("thought")?.asBoolean ?: false
                    if (isThought) emit(ChatStreamDelta(reasoning = text)) else emit(ChatStreamDelta(content = text))
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun listAntigravityModels(provider: ProviderConfig, extraHeaders: List<HttpHeader>): List<String> {
        val effectiveHeaders = buildEffectiveHeaders(provider, extraHeaders)
        val baseUrls = antigravityBaseUrlFallbackOrder(provider)
        var lastError: String? = null

        baseUrls.forEachIndexed { index, baseUrl ->
            val url = baseUrl.trimEnd('/') + "/v1internal:fetchAvailableModels"
            val requestBuilder =
                Request.Builder()
                    .url(url)
                    .post("{}".toRequestBody(jsonMediaType))

            applyHeaders(requestBuilder, effectiveHeaders)

            if (provider.apiKey.isNotBlank() && !hasHeader(effectiveHeaders, "authorization")) {
                requestBuilder.header("Authorization", "Bearer ${provider.apiKey}")
            }
            if (!hasHeader(effectiveHeaders, "user-agent")) {
                requestBuilder.header("User-Agent", ANTIGRAVITY_USER_AGENT)
            }

            requestBuilder
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")

            val request = requestBuilder.build()

            client.newCall(request).execute().use { response ->
                val raw = response.body.string()
                if (!response.isSuccessful) {
                    lastError = "HTTP ${response.code}: $raw"
                    if (index + 1 < baseUrls.size) return@use
                    error(lastError)
                }

                val json = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull() ?: return emptyList()
                val models = json.getAsJsonObject("models") ?: return emptyList()
                return models.entrySet().mapNotNull { entry ->
                    entry.key?.trim()?.takeIf { it.isNotBlank() }
                }.sorted()
            }
        }

        lastError?.takeIf { it.isNotBlank() }?.let { error(it) }
        return emptyList()
    }

    private fun listGeminiCliModels(provider: ProviderConfig, extraHeaders: List<HttpHeader>): List<String> {
        val url = normalizeProviderApiUrl(provider) + "/v1internal:fetchAvailableModels"
        val effectiveHeaders = buildEffectiveHeaders(provider, extraHeaders)
        val requestBuilder =
            Request.Builder()
                .url(url)
                .post("{}".toRequestBody(jsonMediaType))

        applyHeaders(requestBuilder, effectiveHeaders)

        if (provider.apiKey.isNotBlank() && !hasHeader(effectiveHeaders, "authorization")) {
            requestBuilder.header("Authorization", "Bearer ${provider.apiKey}")
        }
        if (!hasHeader(effectiveHeaders, "user-agent")) {
            requestBuilder.header("User-Agent", GEMINI_CLI_USER_AGENT)
        }

        requestBuilder
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("X-Goog-Api-Client", GEMINI_CLI_API_CLIENT)
            .header("Client-Metadata", GEMINI_CLI_CLIENT_METADATA)

        val request = requestBuilder.build()

        client.newCall(request).execute().use { response ->
            val raw = response.body.string()
            if (!response.isSuccessful) error("HTTP ${response.code}: $raw")

            val json = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull() ?: return emptyList()
            val models = json.getAsJsonObject("models") ?: return emptyList()
            return models.entrySet().mapNotNull { entry ->
                entry.key?.trim()?.takeIf { it.isNotBlank() }
            }.sorted()
        }
    }

    private fun listGrok2ApiModels(provider: ProviderConfig, extraHeaders: List<HttpHeader>): List<String> {
        if (isGrokReverseDirect(provider)) {
            return GROK2API_DEFAULT_MODELS
        }
        val effectiveHeaders = buildEffectiveHeaders(provider, extraHeaders)
        val baseCandidates = providerApiBaseCandidates(provider)
        var remoteModels: List<String> = emptyList()

        for ((index, baseUrl) in baseCandidates.withIndex()) {
            try {
                val requestBuilder =
                    Request.Builder()
                        .url(baseUrl + "/models")
                        .get()

                applyHeaders(requestBuilder, effectiveHeaders)
                if (provider.apiKey.isNotBlank() && !hasHeader(effectiveHeaders, "authorization")) {
                    requestBuilder.header("Authorization", "Bearer ${provider.apiKey}")
                }
                requestBuilder.header("Accept", "application/json")

                remoteModels =
                    client.newCall(requestBuilder.build()).execute().use { response ->
                        val raw = response.body.string()
                        if (!response.isSuccessful) {
                            error("HTTP ${response.code}: $raw")
                        }
                        parseModelIdsFromJson(raw)
                    }
                break
            } catch (t: Throwable) {
                val shouldRetry = shouldRetryGrokRequest(t) && index < baseCandidates.lastIndex
                if (!shouldRetry) break
            }
        }

        return (remoteModels + GROK2API_DEFAULT_MODELS)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    /**
     * 图片生成 - 使用 DALL-E 或其他文生图模型
     */
    suspend fun generateImage(
        provider: ProviderConfig,
        modelId: String,
        prompt: String,
        extraHeaders: List<HttpHeader> = emptyList(),
        size: String = "1024x1024",
        quality: String = "standard",
        n: Int = 1
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val effectiveHeaders = buildEffectiveHeaders(provider, extraHeaders)
                if (isGrokReverseDirect(provider)) {
                    return@runCatching grokReverseGenerateImageDirect(
                        provider = provider,
                        modelId = modelId,
                        prompt = prompt,
                        extraHeaders = effectiveHeaders,
                        size = size,
                        quality = quality,
                        n = n
                    )
                }
                val body = gson.toJson(
                    mapOf(
                        "model" to modelId,
                        "prompt" to prompt,
                        "n" to n,
                        "size" to size,
                        "quality" to quality,
                        "response_format" to "url"
                    )
                )
                val baseCandidates = providerApiBaseCandidates(provider)
                var lastError: Throwable? = null

                for ((index, baseUrl) in baseCandidates.withIndex()) {
                    try {
                        val requestBuilder = Request.Builder().url(baseUrl + "/images/generations")

                        applyHeaders(requestBuilder, effectiveHeaders)

                        if (provider.apiKey.isNotBlank() && !hasHeader(effectiveHeaders, "authorization")) {
                            requestBuilder.header("Authorization", "Bearer ${provider.apiKey}")
                        }

                        requestBuilder.header("Content-Type", "application/json")
                        val request = requestBuilder
                            .post(body.toRequestBody(jsonMediaType))
                            .build()

                        return@runCatching client.newCall(request).execute().use { response ->
                            val raw = response.body.string()
                            if (!response.isSuccessful) {
                                error("HTTP ${response.code}: $raw")
                            }

                            val parsed = gson.fromJson(raw, ImageGenerationResponse::class.java)
                            val url = parsed.data?.firstOrNull()?.url
                            if (!url.isNullOrBlank()) return@use url

                            val b64 = parsed.data?.firstOrNull()?.b64_json
                            if (!b64.isNullOrBlank()) return@use "data:image/png;base64,$b64"

                            error("No image URL in response")
                        }
                    } catch (t: Throwable) {
                        lastError = t
                        val shouldRetry = isGrok2Api(provider) && shouldRetryGrokRequest(t) && index < baseCandidates.lastIndex
                        if (!shouldRetry) throw t
                    }
                }

                throw (lastError ?: IllegalStateException("image generation request failed"))
            }
        }
    }

    companion object {
        private const val GROK2API_DEFAULT_APP_KEY = "grok2api"
        private const val GROK_TOKEN_SYNC_INTERVAL_MS = 2 * 60 * 1000L
        private const val GROK_REVERSE_CHAT_API = "https://grok.com/rest/app-chat/conversations/new"
        private const val GROK_REVERSE_IMAGINE_WS_API = "wss://grok.com/ws/imagine/listen"
        private const val GROK_REVERSE_IMAGINE_TIMEOUT_MS = 70_000L
        private const val GROK_REVERSE_IMAGINE_FINAL_MIN_BLOB_BYTES = 280_000
        private const val GROK_REVERSE_USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36"
        private const val GROK_REVERSE_BAGGAGE =
            "sentry-environment=production,sentry-release=d6add6fb0460641fd482d767a335ef72b9b6abb8,sentry-public_key=b311e0f2690c81f25e2c4cf6d4f7ce1c"
        private const val GROK_REVERSE_STATSIG_ID =
            "ZTpUeXBlRXJyb3I6IENhbm5vdCByZWFkIHByb3BlcnRpZXMgb2YgdW5kZWZpbmVkIChyZWFkaW5nICdjaGlsZE5vZGVzJyk="
        private const val CODEX_USER_AGENT = "codex_cli_rs/0.50.0 (Mac OS 26.0.1; arm64) Apple_Terminal/464"
        private const val CODEX_CLIENT_VERSION = "0.50.0"
        private const val ANTIGRAVITY_USER_AGENT = "antigravity/1.104.0 darwin/arm64"
        private const val ANTIGRAVITY_BASE_DAILY = "https://daily-cloudcode-pa.googleapis.com"
        private const val ANTIGRAVITY_BASE_SANDBOX = "https://daily-cloudcode-pa.sandbox.googleapis.com"
        private const val IFLOW_USER_AGENT = "iFlow-Cli"
        private const val QWEN_USER_AGENT = "QwenCode/0.10.3 (darwin; arm64)"
        private const val GEMINI_CLI_USER_AGENT = "google-api-nodejs-client/9.15.1"
        private const val GEMINI_CLI_API_CLIENT = "gl-node/22.17.0"
        private const val GEMINI_CLI_CLIENT_METADATA =
            "ideType=IDE_UNSPECIFIED,platform=PLATFORM_UNSPECIFIED,pluginType=GEMINI"

        private val CODEX_DEFAULT_MODELS =
            listOf(
                "gpt-5.2-codex",
                "gpt-5.2",
                "gpt-5.1-codex",
                "gpt-5.1-codex-mini",
                "gpt-5.1-codex-max",
                "gpt-5-codex",
                "gpt-5-codex-mini",
                "gpt-5"
            )
        private val GROK2API_DEFAULT_MODELS =
            listOf(
                "grok-3",
                "grok-3-mini",
                "grok-3-thinking",
                "grok-4",
                "grok-4-mini",
                "grok-4-thinking",
                "grok-4-heavy",
                "grok-4.1-mini",
                "grok-4.1-fast",
                "grok-4.1-expert",
                "grok-4.1-thinking",
                "grok-4.20-beta",
                "grok-imagine-1.0",
                "grok-imagine-1.0-edit",
                "grok-imagine-1.0-video"
            )
        private val GROK_REVERSE_MODEL_CONFIGS =
            mapOf(
                "grok-3" to GrokReverseModelConfig("grok-3", "MODEL_MODE_GROK_3"),
                "grok-3-mini" to GrokReverseModelConfig("grok-3", "MODEL_MODE_GROK_3_MINI_THINKING"),
                "grok-3-thinking" to GrokReverseModelConfig("grok-3", "MODEL_MODE_GROK_3_THINKING"),
                "grok-4" to GrokReverseModelConfig("grok-4", "MODEL_MODE_GROK_4"),
                "grok-4-mini" to GrokReverseModelConfig("grok-4-mini", "MODEL_MODE_GROK_4_MINI_THINKING"),
                "grok-4-thinking" to GrokReverseModelConfig("grok-4", "MODEL_MODE_GROK_4_THINKING"),
                "grok-4-heavy" to GrokReverseModelConfig("grok-4", "MODEL_MODE_HEAVY"),
                "grok-4.1-mini" to GrokReverseModelConfig("grok-4-1-thinking-1129", "MODEL_MODE_GROK_4_1_MINI_THINKING"),
                "grok-4.1-fast" to GrokReverseModelConfig("grok-4-1-thinking-1129", "MODEL_MODE_FAST"),
                "grok-4.1-expert" to GrokReverseModelConfig("grok-4-1-thinking-1129", "MODEL_MODE_EXPERT"),
                "grok-4.1-thinking" to GrokReverseModelConfig("grok-4-1-thinking-1129", "MODEL_MODE_GROK_4_1_THINKING"),
                "grok-4.20-beta" to GrokReverseModelConfig("grok-420", "MODEL_MODE_GROK_420"),
                "grok-imagine-1.0" to GrokReverseModelConfig("grok-3", "MODEL_MODE_FAST"),
                "grok-imagine-1.0-edit" to GrokReverseModelConfig("imagine-image-edit", "MODEL_MODE_FAST"),
                "grok-imagine-1.0-video" to GrokReverseModelConfig("grok-3", "MODEL_MODE_FAST")
            )
        private val QWEN_CODE_DEFAULT_MODELS =
            listOf(
                "qwen3-coder-plus",
                "qwen3-coder-flash",
                "coder-model",
                "vision-model"
            )
    }

    private data class CodexModelMeta(
        val slug: String,
        val visibility: String,
        val supportedInApi: Boolean,
        val priority: Int,
        val baseInstructions: String,
        val supportsReasoningSummaries: Boolean,
        val defaultReasoningEffort: String?,
        val supportedReasoningEfforts: List<String>,
        val supportsParallelToolCalls: Boolean,
        val inputModalities: List<String>
    )

    private fun isCodex(provider: ProviderConfig): Boolean {
        if (provider.type.trim().equals("codex", ignoreCase = true)) return true
        if (provider.presetId?.trim()?.equals("codex", ignoreCase = true) == true) return true
        if (provider.oauthProvider?.trim()?.equals("codex", ignoreCase = true) == true) return true
        if (provider.apiUrl.contains("/backend-api/codex", ignoreCase = true)) return true
        return false
    }

    private fun isGrok2Api(provider: ProviderConfig): Boolean {
        val normalizedType = provider.type.trim()
        if (normalizedType.equals("grok2api", ignoreCase = true)) return true
        if (normalizedType.equals("grok", ignoreCase = true)) return true
        if (provider.presetId?.trim()?.equals("grok2api", ignoreCase = true) == true) return true
        if (provider.presetId?.trim()?.equals("grok", ignoreCase = true) == true) return true
        if (provider.apiUrl.contains("grok2api", ignoreCase = true)) return true
        if (provider.apiUrl.contains("grok.com", ignoreCase = true)) return true
        val lowerName = provider.name.trim().lowercase()
        val lowerPreset = provider.presetId?.trim()?.lowercase().orEmpty()
        val lowerUrl = provider.apiUrl.trim().lowercase()
        val localGateway =
            lowerUrl.contains("localhost") ||
                lowerUrl.contains("127.0.0.1") ||
                lowerUrl.contains("10.0.2.2") ||
                lowerUrl.contains("host.docker.internal")
        if (localGateway && (lowerName.contains("grok") || lowerName.contains("xai"))) return true
        if (localGateway && (lowerPreset == "xai" || lowerPreset == "grok2api" || lowerPreset == "grok")) return true
        if (lowerUrl.contains("api.x.ai") && (lowerName.contains("grok") || lowerName.contains("xai"))) return true
        if (lowerUrl.contains("api.x.ai") && (lowerPreset == "xai" || lowerPreset == "grok2api" || lowerPreset == "grok")) return true
        return false
    }

    private fun isQwenCode(provider: ProviderConfig): Boolean {
        if (provider.oauthProvider?.trim()?.equals("qwen", ignoreCase = true) == true) return true
        if (provider.presetId?.trim()?.equals("qwen_code", ignoreCase = true) == true) return true
        if (provider.type.trim().equals("qwen", ignoreCase = true)) return true
        return provider.apiUrl.contains("portal.qwen.ai", ignoreCase = true) ||
            provider.apiUrl.contains("chat.qwen.ai", ignoreCase = true)
    }

    private fun isGitHubCopilot(provider: ProviderConfig): Boolean {
        if (provider.deviceProvider?.trim()?.equals("github_copilot", ignoreCase = true) == true) return true
        if (provider.presetId?.trim()?.equals("github_copilot", ignoreCase = true) == true) return true
        if (provider.apiUrl.contains("api.githubcopilot.com", ignoreCase = true)) return true
        return provider.name.contains("copilot", ignoreCase = true)
    }

    private fun applyGitHubCopilotHeadersIfNeeded(
        requestBuilder: Request.Builder,
        effectiveHeaders: List<HttpHeader>,
        initiator: String,
        visionRequest: Boolean
    ) {
        if (!hasHeader(effectiveHeaders, "x-initiator")) {
            requestBuilder.header("x-initiator", initiator)
        }
        if (!hasHeader(effectiveHeaders, "user-agent")) {
            val version = BuildConfig.VERSION_NAME.trim().ifBlank { "dev" }
            requestBuilder.header("User-Agent", "ZionChat/$version")
        }
        if (!hasHeader(effectiveHeaders, "openai-intent")) {
            requestBuilder.header("Openai-Intent", "conversation-edits")
        }
        if (visionRequest && !hasHeader(effectiveHeaders, "copilot-vision-request")) {
            requestBuilder.header("Copilot-Vision-Request", "true")
        }
    }

    private fun isVisionRequest(messages: List<Message>): Boolean {
        return messages.any { message ->
            message.attachments?.any { it.type == "image" && it.url.isNotBlank() } == true ||
                extractMarkdownImages(message.content).second.isNotEmpty()
        }
    }

    private fun normalizeProviderApiUrl(provider: ProviderConfig): String {
        return providerApiBaseCandidates(provider).first()
    }

    private fun providerApiBaseCandidates(provider: ProviderConfig): List<String> {
        val raw = provider.apiUrl.trim().trimEnd('/')
        if (!isGrok2Api(provider)) {
            return listOf(raw)
        }

        val fallbackGateways =
            listOf(
                "http://host.docker.internal:8000/v1",
                "http://10.0.2.2:8000/v1",
                "http://127.0.0.1:8000/v1",
                "http://localhost:8000/v1"
            )

        val candidates = LinkedHashSet<String>()
        val isXaiSource = raw.contains("api.x.ai", ignoreCase = true)
        if (raw.isNotBlank()) {
            addGrokBaseCandidate(candidates, raw)
            if (isXaiSource) {
                addGrokBaseCandidate(candidates, raw.replace("://localhost", "://10.0.2.2", ignoreCase = true))
                addGrokBaseCandidate(candidates, raw.replace("://127.0.0.1", "://10.0.2.2", ignoreCase = true))
                addGrokBaseCandidate(candidates, raw.replace("://10.0.2.2", "://127.0.0.1", ignoreCase = true))
                addGrokBaseCandidate(candidates, raw.replace("://localhost", "://127.0.0.1", ignoreCase = true))
                addGrokBaseCandidate(candidates, raw.replace("://127.0.0.1", "://localhost", ignoreCase = true))
                addGrokBaseCandidate(candidates, raw.replace("://10.0.2.2", "://localhost", ignoreCase = true))
                addGrokBaseCandidate(candidates, raw.replace("://host.docker.internal", "://10.0.2.2", ignoreCase = true))
                addGrokBaseCandidate(candidates, raw.replace("://host.docker.internal", "://127.0.0.1", ignoreCase = true))
                addGrokBaseCandidate(candidates, raw.replace("://host.docker.internal", "://localhost", ignoreCase = true))
                addGrokBaseCandidate(candidates, raw.replace("://10.0.2.2", "://host.docker.internal", ignoreCase = true))
                addGrokBaseCandidate(candidates, raw.replace("://127.0.0.1", "://host.docker.internal", ignoreCase = true))
                addGrokBaseCandidate(candidates, raw.replace("://localhost", "://host.docker.internal", ignoreCase = true))
            }
        }
        if (raw.isBlank() || isXaiSource) {
            fallbackGateways.forEach { addGrokBaseCandidate(candidates, it) }
        }

        val normalizedCandidates =
            candidates
            .map { it.trim().trimEnd('/') }
            .filter { it.isNotBlank() }
            .distinct()

        if (isLocalGrokGatewayBase(raw)) {
            return normalizedCandidates.sortedWith(compareBy<String> { grokGatewayPriority(it) }.thenBy { it })
        }
        return normalizedCandidates
    }

    private fun addGrokBaseCandidate(target: LinkedHashSet<String>, rawValue: String) {
        val value =
            rawValue
                .trim()
                .trimEnd('/')
                .replace(Regex("(?i)/(chat/completions|images/generations|images/edits|models)$"), "")
                .trimEnd('/')
        if (value.isBlank()) return
        target += value

        val lower = value.lowercase()
        val hasVersionSegment = Regex("/v\\d+(?:/|$)").containsMatchIn(lower)
        if (!hasVersionSegment) {
            target += "$value/v1"
        }
    }

    private fun parseHttpStatusCode(error: Throwable): Int? {
        val msg = error.message?.lowercase().orEmpty()
        if (msg.isBlank()) return null
        return Regex("http\\s*(\\d{3})")
            .find(msg)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }

    private fun shouldRetryGrokRequest(error: Throwable): Boolean {
        if (error is IOException) return true
        val msg = error.message?.lowercase().orEmpty()
        if (msg.isBlank()) return false
        val statusCode = parseHttpStatusCode(error)
        if (statusCode != null && statusCode in 500..599) return true
        return msg.contains("connection reset") ||
            msg.contains("failed to connect") ||
            msg.contains("connection refused") ||
            msg.contains("timeout") ||
            msg.contains("broken pipe") ||
            msg.contains("bad gateway") ||
            msg.contains("service unavailable") ||
            msg.contains("gateway timeout") ||
            msg.contains("upstream")
    }

    private fun shouldContinueGrokFallback(error: Throwable, hasMoreCandidates: Boolean): Boolean {
        if (!hasMoreCandidates) return false
        if (error is IOException) return true
        val statusCode = parseHttpStatusCode(error)
        if (statusCode == 401 || statusCode == 403) return false
        if (statusCode != null) return true
        return shouldRetryGrokRequest(error)
    }

    private fun formatGrokAttemptError(baseUrl: String, error: Throwable): String {
        val message = error.message?.lineSequence()?.firstOrNull()?.trim().orEmpty()
        return if (message.isBlank()) {
            "$baseUrl => ${error::class.java.simpleName}"
        } else {
            "$baseUrl => $message"
        }
    }

    private fun normalizeReasoningEffort(value: String?): String? {
        val normalized = value?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
        return when (normalized) {
            "none", "minimal", "low", "medium", "high", "xhigh" -> normalized
            else -> null
        }
    }

    private fun shouldRetryWithoutEnableThinking(statusCode: Int, errorBody: String): Boolean {
        if (statusCode != 400 && statusCode != 422) return false
        val lower = errorBody.lowercase()
        if (!lower.contains("enable_thinking") && !lower.contains("extra_body") && !lower.contains("thinking")) return false
        return lower.contains("unknown") ||
            lower.contains("unsupported") ||
            lower.contains("invalid") ||
            lower.contains("unrecognized") ||
            lower.contains("not allowed") ||
            lower.contains("not permit") ||
            lower.contains("additional properties") ||
            lower.contains("schema")
    }

    private fun isKimiProvider(provider: ProviderConfig): Boolean {
        val preset = provider.presetId?.trim().orEmpty()
        val providerType = provider.type.trim()
        val oauth = provider.oauthProvider?.trim().orEmpty()
        val apiUrl = provider.apiUrl.trim()
        val name = provider.name.trim()
        return preset.equals("kimi", ignoreCase = true) ||
            providerType.equals("kimi", ignoreCase = true) ||
            oauth.equals("kimi", ignoreCase = true) ||
            apiUrl.contains("api.moonshot.ai", ignoreCase = true) ||
            apiUrl.contains("api.moonshot.cn", ignoreCase = true) ||
            name.contains("kimi", ignoreCase = true) ||
            name.contains("moonshot", ignoreCase = true)
    }

    private fun normalizeCodexBaseUrl(provider: ProviderConfig): String {
        val raw = provider.apiUrl.trim().trimEnd('/')
        if (raw.endsWith("/v1", ignoreCase = true)) {
            return raw.dropLast(3)
        }
        return raw
    }

    private fun isIFlow(provider: ProviderConfig): Boolean {
        if (provider.oauthProvider?.trim()?.equals("iflow", ignoreCase = true) == true) return true
        if (provider.presetId?.trim()?.equals("iflow", ignoreCase = true) == true) return true
        return provider.apiUrl.contains("iflow", ignoreCase = true)
    }

    private fun applyQwenCompatibleHeaders(
        requestBuilder: Request.Builder,
        effectiveHeaders: List<HttpHeader>
    ) {
        if (!hasHeader(effectiveHeaders, "user-agent")) {
            requestBuilder.header("User-Agent", QWEN_USER_AGENT)
        }
        if (!hasHeader(effectiveHeaders, "x-dashscope-useragent")) {
            requestBuilder.header("X-Dashscope-Useragent", QWEN_USER_AGENT)
        }
        if (!hasHeader(effectiveHeaders, "x-stainless-runtime-version")) {
            requestBuilder.header("X-Stainless-Runtime-Version", "v22.17.0")
        }
        if (!hasHeader(effectiveHeaders, "sec-fetch-mode")) {
            requestBuilder.header("Sec-Fetch-Mode", "cors")
        }
        if (!hasHeader(effectiveHeaders, "x-stainless-lang")) {
            requestBuilder.header("X-Stainless-Lang", "js")
        }
        if (!hasHeader(effectiveHeaders, "x-stainless-arch")) {
            requestBuilder.header("X-Stainless-Arch", "arm64")
        }
        if (!hasHeader(effectiveHeaders, "x-stainless-package-version")) {
            requestBuilder.header("X-Stainless-Package-Version", "5.11.0")
        }
        if (!hasHeader(effectiveHeaders, "x-dashscope-cachecontrol")) {
            requestBuilder.header("X-Dashscope-Cachecontrol", "enable")
        }
        if (!hasHeader(effectiveHeaders, "x-stainless-retry-count")) {
            requestBuilder.header("X-Stainless-Retry-Count", "0")
        }
        if (!hasHeader(effectiveHeaders, "x-stainless-os")) {
            requestBuilder.header("X-Stainless-Os", "MacOS")
        }
        if (!hasHeader(effectiveHeaders, "x-dashscope-authtype")) {
            requestBuilder.header("X-Dashscope-Authtype", "qwen-oauth")
        }
        if (!hasHeader(effectiveHeaders, "x-stainless-runtime")) {
            requestBuilder.header("X-Stainless-Runtime", "node")
        }
    }

    private fun buildQwenSafetyNoopTool(): Map<String, Any> {
        return mapOf(
            "type" to "function",
            "function" to mapOf(
                "name" to "do_not_call_me",
                "description" to "Do not call this tool.",
                "parameters" to mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "operation" to mapOf(
                            "type" to "string",
                            "description" to "Never call this tool."
                        )
                    ),
                    "required" to listOf("operation")
                )
            )
        )
    }

    private fun buildQwenBridgeTools(messages: List<Message>): List<Map<String, Any>> {
        val hasToolIntent =
            messages
                .takeLast(8)
                .asSequence()
                .map { it.content.lowercase() }
                .any { content ->
                    content.contains("mcp_call") ||
                        content.contains("tool_call") ||
                        content.contains("app_developer") ||
                        content.contains("mcp tools") ||
                        content.contains("应用开发")
                }

        if (!hasToolIntent) {
            return listOf(buildQwenSafetyNoopTool())
        }

        val mcpBridgeTool =
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to "mcp_call",
                    "description" to "Call an MCP tool by serverId, toolName and arguments.",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "serverId" to mapOf("type" to "string", "description" to "MCP server id."),
                            "toolName" to mapOf("type" to "string", "description" to "Exact MCP tool name."),
                            "arguments" to mapOf(
                                "type" to "object",
                                "description" to "Arguments object for the selected tool.",
                                "additionalProperties" to true
                            )
                        ),
                        "required" to listOf("toolName", "arguments")
                    )
                )
            )

        val appDeveloperTool =
            mapOf(
                "type" to "function",
                "function" to mapOf(
                    "name" to "app_developer",
                    "description" to "Generate or edit a single-file HTML app.",
                    "parameters" to mapOf(
                        "type" to "object",
                        "properties" to mapOf(
                            "name" to mapOf("type" to "string", "description" to "App name."),
                            "description" to mapOf("type" to "string", "description" to "Detailed app requirement."),
                            "app_icon" to mapOf("type" to "string", "description" to "Lucide icon name.")
                        ),
                        "required" to listOf("name", "description", "app_icon")
                    )
                )
            )

        return listOf(mcpBridgeTool, appDeveloperTool)
    }

    private fun isNoopQwenToolList(tools: List<Map<String, Any>>): Boolean {
        if (tools.size != 1) return false
        val function = tools.first()["function"] as? Map<*, *> ?: return false
        val name = function["name"]?.toString()?.trim().orEmpty()
        return name.equals("do_not_call_me", ignoreCase = true)
    }

    private fun generateAntigravityProjectId(): String {
        val adjectives = listOf("useful", "bright", "swift", "calm", "bold")
        val nouns = listOf("fuze", "wave", "spark", "flow", "core")
        val adj = adjectives.random()
        val noun = nouns.random()
        val randomPart = UUID.randomUUID().toString().lowercase().take(5)
        return "$adj-$noun-$randomPart"
    }

    private fun generateAntigravitySessionId(messages: List<Message>): String {
        val firstUserText = messages.firstOrNull { it.role == "user" }?.content?.trim().orEmpty()
        if (firstUserText.isNotBlank()) {
            val digest = MessageDigest.getInstance("SHA-256").digest(firstUserText.toByteArray(Charsets.UTF_8))
            val n = abs(ByteBuffer.wrap(digest.copyOfRange(0, 8)).long)
            return "-$n"
        }
        val n = Random.nextLong(0, 9_000_000_000_000_000_000L)
        return "-$n"
    }

    private fun antigravityBaseUrlFallbackOrder(provider: ProviderConfig): List<String> {
        val custom = provider.apiUrl.trim().trimEnd('/')
        val isProd = custom.contains("cloudcode-pa.googleapis.com", ignoreCase = true) &&
            !custom.contains("daily-cloudcode-pa.googleapis.com", ignoreCase = true)

        val candidates =
            when {
                isProd -> listOf(ANTIGRAVITY_BASE_DAILY, ANTIGRAVITY_BASE_SANDBOX, custom)
                custom.contains("daily-cloudcode-pa.googleapis.com", ignoreCase = true) ->
                    listOf(custom, ANTIGRAVITY_BASE_SANDBOX)
                custom.contains("daily-cloudcode-pa.sandbox.googleapis.com", ignoreCase = true) ->
                    listOf(custom, ANTIGRAVITY_BASE_DAILY)
                else -> listOf(custom)
            }

        return candidates
            .map { it.trim().trimEnd('/') }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun toOpenAIChatMessages(provider: ProviderConfig, messages: List<Message>): List<Map<String, Any>> {
        return messages.map { message ->
            val role = message.role
            if (role != "user") {
                mapOf("role" to role, "content" to message.content)
            } else {
                // First get images from attachments (new approach)
                val attachmentImages = message.attachments?.mapNotNull { it.url }.orEmpty()
                // Also extract from markdown content (backward compatibility)
                val (text, markdownImages) = extractMarkdownImages(message.content)
                // Combine both sources of images
                val allImages = (attachmentImages + markdownImages).distinct()

                if (allImages.isEmpty()) {
                    mapOf("role" to role, "content" to message.content)
                } else {
                    val parts = mutableListOf<Map<String, Any>>()
                    if (text.isNotBlank()) {
                        parts.add(mapOf("type" to "text", "text" to text))
                    } else if (isQwenCode(provider)) {
                        parts.add(
                            mapOf(
                                "type" to "text",
                                "text" to "请识别并描述图片中的关键信息。"
                            )
                        )
                    }
                    allImages.forEach { url ->
                        parts.add(mapOf("type" to "image_url", "image_url" to mapOf("url" to url)))
                    }
                    mapOf("role" to role, "content" to parts)
                }
            }
        }
    }

    private fun extractMarkdownImages(content: String): Pair<String, List<String>> {
        val raw = content.trim()
        if (raw.isBlank()) return "" to emptyList()

        val urls =
            markdownImageRegex.findAll(raw)
                .mapNotNull { match ->
                    match.groupValues.getOrNull(1)?.trim()
                }
                .map { it.trim() }
                .filter { it.startsWith("http", ignoreCase = true) || it.startsWith("data:image", ignoreCase = true) }
                .distinct()
                .toList()

        if (urls.isEmpty()) return raw to emptyList()
        val text = raw.replace(markdownImageRegex, "").trim()
        return text to urls
    }

    private fun toGeminiParts(content: String, attachments: List<MessageAttachment>? = null): List<Map<String, Any>> {
        val (text, images) = extractMarkdownImages(content)
        val parts = mutableListOf<Map<String, Any>>()

        if (text.isNotBlank()) {
            parts.add(mapOf("text" to text))
        }

        // Add attachments (base64 data URLs or remote URLs)
        attachments?.forEach { attachment ->
            val url = attachment.url
            val inline = parseInlineDataPart(url)
            if (inline != null) {
                parts.add(inline)
            } else {
                // Best-effort fallback: keep the URL in text form.
                parts.add(mapOf("text" to url))
            }
        }

        // Also process any markdown images in content
        images.forEach { url ->
            val inline = parseInlineDataPart(url)
            if (inline != null) {
                parts.add(inline)
            } else {
                // Best-effort fallback: keep the URL in text form.
                parts.add(mapOf("text" to url))
            }
        }

        if (parts.isEmpty()) {
            parts.add(mapOf("text" to ""))
        }

        return parts
    }

    private fun parseInlineDataPart(url: String): Map<String, Any>? {
        val match = dataUrlRegex.matchEntire(url.trim()) ?: return null
        val mimeType = match.groupValues.getOrNull(1)?.trim().orEmpty()
        val b64 = match.groupValues.getOrNull(2)?.trim().orEmpty()
        if (mimeType.isBlank() || b64.isBlank()) return null
        return mapOf("inlineData" to mapOf("mimeType" to mimeType, "data" to b64))
    }
}

data class OpenAIChatCompletionsResponse(
    val choices: List<OpenAIChoice>?
)

data class OpenAIChoice(
    val message: OpenAIMessage?
)

data class OpenAIMessage(
    val content: String?
)

data class OpenAIModelsResponse(
    val data: List<OpenAIModel>?
)

data class OpenAIModel(
    val id: String?
)

// 流式响应数据类
data class OpenAIStreamChunk(
    val choices: List<OpenAIStreamChoice>?
)

data class OpenAIStreamChoice(
    val delta: OpenAIStreamDelta?
)

data class OpenAIStreamDelta(
    val content: String?,
    val reasoning_content: String? = null,
    val reasoning: String? = null,
    val thinking: String? = null
)

data class ChatStreamDelta(
    val content: String? = null,
    val reasoning: String? = null,
    val finishReason: String? = null
)

// 图片生成相关数据类
data class ImageGenerationResponse(
    val data: List<ImageData>?
)

data class ImageData(
    val url: String?,
    val b64_json: String?
)

