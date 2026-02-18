package com.zionchat.app.data

import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
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
    private val gson = Gson()
    private val jsonMediaType = "application/json".toMediaType()
    private val strictJsonMediaType = "application/json".toMediaType()
    private val markdownImageRegex = Regex("!\\[[^\\]]*\\]\\(([^)]+)\\)")
    private val dataUrlRegex = Regex("^data:([^;]+);base64,(.+)$", RegexOption.IGNORE_CASE)
    private val codexModelCache = ConcurrentHashMap<String, CodexModelMeta>()

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
            val raw = response.body?.string().orEmpty()
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
            val raw = response.body?.string().orEmpty()
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
            val raw = response.body?.string().orEmpty()
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
            val raw = response.body?.string().orEmpty()
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

                        client.newCall(requestBuilder.build()).execute().use { response ->
                            val raw = response.body?.string().orEmpty()
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
                        val raw = response.body?.string().orEmpty()
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

    suspend fun chatCompletions(
        provider: ProviderConfig,
        modelId: String,
        messages: List<Message>,
        extraHeaders: List<HttpHeader> = emptyList()
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val url = normalizeProviderApiUrl(provider) + "/chat/completions"
                val effectiveHeaders = buildEffectiveHeaders(provider, extraHeaders)
                val body = gson.toJson(
                    mapOf(
                        "model" to modelId,
                        "messages" to toOpenAIChatMessages(messages)
                    )
                )
                val requestBuilder = Request.Builder()
                    .url(url)

                applyHeaders(requestBuilder, effectiveHeaders)

                if (provider.apiKey.isNotBlank() && !hasHeader(effectiveHeaders, "authorization")) {
                    requestBuilder.header("Authorization", "Bearer ${provider.apiKey}")
                }
                if (isIFlow(provider) && !hasHeader(effectiveHeaders, "user-agent")) {
                    requestBuilder.header("User-Agent", IFLOW_USER_AGENT)
                }

                requestBuilder.header("Content-Type", "application/json")
                val request = requestBuilder
                    .post(body.toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        error("HTTP ${response.code}: $raw")
                    }

                    val parsed = gson.fromJson(raw, OpenAIChatCompletionsResponse::class.java)
                    parsed.choices?.firstOrNull()?.message?.content?.trim().orEmpty()
                }
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
        conversationId: String? = null
    ): Flow<ChatStreamDelta> {
        val type = provider.type.trim().lowercase()
        return when {
            isCodex(provider) -> codexResponsesStream(provider, modelId, messages, extraHeaders, reasoningEffort, conversationId)
            type == "antigravity" -> antigravityStream(provider, modelId, messages, extraHeaders)
            type == "gemini-cli" -> geminiCliStream(provider, modelId, messages, extraHeaders)
            else -> openAIChatCompletionsStream(provider, modelId, messages, extraHeaders, reasoningEffort)
        }
    }

    private fun openAIChatCompletionsStream(
        provider: ProviderConfig,
        modelId: String,
        messages: List<Message>,
        extraHeaders: List<HttpHeader>,
        reasoningEffort: String? = null
    ): Flow<ChatStreamDelta> = flow {
        val url = normalizeProviderApiUrl(provider) + "/chat/completions"
        val effectiveHeaders = buildEffectiveHeaders(provider, extraHeaders)
        val payload =
            linkedMapOf<String, Any>(
                "model" to modelId,
                "messages" to toOpenAIChatMessages(messages),
                "stream" to true
            )
        if (isGrok2Api(provider)) {
            normalizeReasoningEffort(reasoningEffort)?.let { payload["reasoning_effort"] = it }
        }
        val body = gson.toJson(payload)
        val requestBuilder = Request.Builder().url(url)

        applyHeaders(requestBuilder, effectiveHeaders)

        if (provider.apiKey.isNotBlank() && !hasHeader(effectiveHeaders, "authorization")) {
            requestBuilder.header("Authorization", "Bearer ${provider.apiKey}")
        }
        if (isIFlow(provider) && !hasHeader(effectiveHeaders, "user-agent")) {
            requestBuilder.header("User-Agent", IFLOW_USER_AGENT)
        }

        requestBuilder
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
        val request = requestBuilder
            .post(body.toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string().orEmpty()
                throw IllegalStateException("HTTP ${response.code}: $errorBody")
            }

            val contentType = response.header("Content-Type").orEmpty()
            if (!contentType.contains("text/event-stream", ignoreCase = true)) {
                val raw = response.body?.string().orEmpty()
                val parsed = runCatching { gson.fromJson(raw, OpenAIChatCompletionsResponse::class.java) }.getOrNull()
                val text = parsed?.choices?.firstOrNull()?.message?.content?.trim().orEmpty()
                if (text.isNotEmpty()) {
                    emit(ChatStreamDelta(content = text))
                }
                return@use
            }

            val source = response.body?.source()
                ?: throw IllegalStateException("Response body is null")

            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: continue
                if (line.startsWith("data:")) {
                    val data = line.removePrefix("data:").trimStart()
                    if (data == "[DONE]") break

                    try {
                        val chunk = gson.fromJson(data, OpenAIStreamChunk::class.java)
                        val delta = chunk.choices?.firstOrNull()?.delta
                        val content = delta?.content
                        val reasoning = delta?.reasoning_content ?: delta?.reasoning ?: delta?.thinking
                        if (!content.isNullOrEmpty() || !reasoning.isNullOrEmpty()) {
                            emit(ChatStreamDelta(content = content, reasoning = reasoning))
                        }
                    } catch (_: Exception) {
                        parseOpenAIStreamDeltaLenient(data)?.let { emit(it) }
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun parseOpenAIStreamDeltaLenient(data: String): ChatStreamDelta? {
        val json = runCatching { JsonParser.parseString(data).asJsonObject }.getOrNull() ?: return null
        val choices = runCatching { json.getAsJsonArray("choices") }.getOrNull() ?: return null
        if (choices.size() == 0) return null
        val firstChoice = runCatching { choices[0].asJsonObject }.getOrNull() ?: return null
        val delta = runCatching { firstChoice.getAsJsonObject("delta") }.getOrNull() ?: return null

        val content = delta.get("content")?.takeIf { it.isJsonPrimitive }?.asString?.trim()
        val reasoning =
            listOf("reasoning_content", "reasoning", "thinking")
                .firstNotNullOfOrNull { key ->
                    delta.get(key)?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.takeIf { it.isNotEmpty() }
                }

        if (content.isNullOrEmpty() && reasoning.isNullOrEmpty()) return null
        return ChatStreamDelta(content = content, reasoning = reasoning)
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
                val errorBody = response.body?.string().orEmpty()
                val sentContentType = request.body?.contentType()?.toString().orEmpty()
                throw IllegalStateException(
                    "HTTP ${response.code}: $errorBody (url=$url, content-type=$sentContentType)"
                )
            }

            val source = response.body?.source() ?: throw IllegalStateException("Response body is null")
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
                    val errorBody = response.body?.string().orEmpty()
                    lastError = "HTTP ${response.code}: $errorBody"
                    if (index + 1 < baseUrls.size) return@use
                    throw IllegalStateException(lastError ?: "HTTP ${response.code}")
                }

                completed = true
                val source = response.body?.source() ?: throw IllegalStateException("Response body is null")
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
                val errorBody = response.body?.string().orEmpty()
                throw IllegalStateException("HTTP ${response.code}: $errorBody")
            }

            val source = response.body?.source() ?: throw IllegalStateException("Response body is null")
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
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    lastError = "HTTP ${response.code}: $raw"
                    if (index + 1 < baseUrls.size) return@use
                    error(lastError ?: "HTTP ${response.code}")
                }

                val json = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull() ?: return emptyList()
                val models = json.getAsJsonObject("models") ?: return emptyList()
                return models.entrySet().mapNotNull { entry ->
                    entry.key?.trim()?.takeIf { it.isNotBlank() }
                }.sorted()
            }
        }

        if (!lastError.isNullOrBlank()) error(lastError!!)
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
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("HTTP ${response.code}: $raw")

            val json = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull() ?: return emptyList()
            val models = json.getAsJsonObject("models") ?: return emptyList()
            return models.entrySet().mapNotNull { entry ->
                entry.key?.trim()?.takeIf { it.isNotBlank() }
            }.sorted()
        }
    }

    private fun listGrok2ApiModels(provider: ProviderConfig, extraHeaders: List<HttpHeader>): List<String> {
        val url = normalizeProviderApiUrl(provider) + "/models"
        val effectiveHeaders = buildEffectiveHeaders(provider, extraHeaders)
        val requestBuilder =
            Request.Builder()
                .url(url)
                .get()

        applyHeaders(requestBuilder, effectiveHeaders)
        if (provider.apiKey.isNotBlank() && !hasHeader(effectiveHeaders, "authorization")) {
            requestBuilder.header("Authorization", "Bearer ${provider.apiKey}")
        }
        requestBuilder.header("Accept", "application/json")

        val remoteModels =
            runCatching {
                client.newCall(requestBuilder.build()).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) return@use emptyList<String>()
                    parseModelIdsFromJson(raw)
                }
            }.getOrElse { emptyList() }

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
                val url = normalizeProviderApiUrl(provider) + "/images/generations"
                val effectiveHeaders = buildEffectiveHeaders(provider, extraHeaders)
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
                val requestBuilder = Request.Builder().url(url)

                applyHeaders(requestBuilder, effectiveHeaders)

                if (provider.apiKey.isNotBlank() && !hasHeader(effectiveHeaders, "authorization")) {
                    requestBuilder.header("Authorization", "Bearer ${provider.apiKey}")
                }

                requestBuilder.header("Content-Type", "application/json")
                val request = requestBuilder
                    .post(body.toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
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
            }
        }
    }

    companion object {
        private const val CODEX_USER_AGENT = "codex_cli_rs/0.50.0 (Mac OS 26.0.1; arm64) Apple_Terminal/464"
        private const val CODEX_CLIENT_VERSION = "0.50.0"
        private const val ANTIGRAVITY_USER_AGENT = "antigravity/1.104.0 darwin/arm64"
        private const val ANTIGRAVITY_BASE_DAILY = "https://daily-cloudcode-pa.googleapis.com"
        private const val ANTIGRAVITY_BASE_SANDBOX = "https://daily-cloudcode-pa.sandbox.googleapis.com"
        private const val IFLOW_USER_AGENT = "iFlow-Cli"
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
        return false
    }

    private fun normalizeProviderApiUrl(provider: ProviderConfig): String {
        var base = provider.apiUrl.trim().trimEnd('/')
        if (isGrok2Api(provider)) {
            val gatewayBaseUrl = "http://10.0.2.2:8000/v1"
            val shouldUseGateway =
                base.isBlank() ||
                    base.contains("api.x.ai", ignoreCase = true) ||
                    base.contains("localhost", ignoreCase = true) ||
                    base.contains("127.0.0.1", ignoreCase = true)
            if (shouldUseGateway) {
                base = gatewayBaseUrl
            }
        }
        return base
    }

    private fun normalizeReasoningEffort(value: String?): String? {
        val normalized = value?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
        return when (normalized) {
            "none", "minimal", "low", "medium", "high", "xhigh" -> normalized
            else -> null
        }
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

    private fun toOpenAIChatMessages(messages: List<Message>): List<Map<String, Any>> {
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
    val reasoning: String? = null
)

// 图片生成相关数据类
data class ImageGenerationResponse(
    val data: List<ImageData>?
)

data class ImageData(
    val url: String?,
    val b64_json: String?
)
