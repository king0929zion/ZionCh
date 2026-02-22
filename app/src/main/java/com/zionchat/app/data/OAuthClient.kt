package com.zionchat.app.data

import android.net.Uri
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.zionchat.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

class OAuthClient {
    private val client = OkHttpClient()
    private val gson = Gson()

    suspend fun startGitHubCopilotDeviceCode(domain: String = "github.com"): Result<GitHubDeviceCodeStart> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val normalizedDomain = normalizeGitHubDomain(domain)
                val url = "https://$normalizedDomain/login/device/code"
                val form =
                    FormBody.Builder()
                        .add("client_id", GITHUB_COPILOT_CLIENT_ID)
                        .add("scope", GITHUB_COPILOT_SCOPE)
                        .build()
                val request =
                    Request.Builder()
                        .url(url)
                        .post(form)
                        .addHeader("Accept", "application/json")
                        .addHeader("Content-Type", "application/x-www-form-urlencoded")
                        .addHeader("User-Agent", buildGitHubUserAgent())
                        .build()

                client.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) error("HTTP ${response.code}: $raw")
                    val parsed = gson.fromJson(raw, GitHubDeviceCodeResponse::class.java)

                    val verificationUri =
                        parsed.verification_uri_complete?.trim().takeIf { !it.isNullOrBlank() }
                            ?: parsed.verification_uri?.trim().orEmpty()
                    val userCode = parsed.user_code?.trim().orEmpty()
                    val deviceCode = parsed.device_code?.trim().orEmpty()
                    if (verificationUri.isBlank() || userCode.isBlank() || deviceCode.isBlank()) {
                        error("Invalid GitHub device authorization response")
                    }

                    GitHubDeviceCodeStart(
                        domain = normalizedDomain,
                        verificationUri = verificationUri,
                        userCode = userCode,
                        deviceCode = deviceCode,
                        pollIntervalSeconds = parsed.interval?.coerceAtLeast(1) ?: GITHUB_DEFAULT_POLL_INTERVAL_SECONDS,
                        expiresInSeconds = parsed.expires_in?.coerceAtLeast(1) ?: GITHUB_DEFAULT_EXPIRES_IN_SECONDS
                    )
                }
            }
        }
    }

    suspend fun exchangeGitHubCopilotDeviceCode(
        domain: String,
        deviceCode: String,
        pollIntervalSeconds: Int,
        expiresInSeconds: Int
    ): Result<GitHubDeviceTokenResult> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val normalizedDomain = normalizeGitHubDomain(domain)
                val url = "https://$normalizedDomain/login/oauth/access_token"
                val safeDeviceCode = deviceCode.trim()
                if (safeDeviceCode.isBlank()) error("Missing GitHub device_code")

                var intervalSeconds = pollIntervalSeconds.coerceAtLeast(1)
                val safeExpiresIn = expiresInSeconds.coerceAtLeast(1)
                val maxAttempts =
                    ((safeExpiresIn.toDouble() / intervalSeconds.toDouble()) + 2.0)
                        .toInt()
                        .coerceIn(10, 400)

                repeat(maxAttempts) {
                    val form =
                        FormBody.Builder()
                            .add("client_id", GITHUB_COPILOT_CLIENT_ID)
                            .add("device_code", safeDeviceCode)
                            .add("grant_type", GITHUB_DEVICE_GRANT_TYPE)
                            .build()
                    val request =
                        Request.Builder()
                            .url(url)
                            .post(form)
                            .addHeader("Accept", "application/json")
                            .addHeader("Content-Type", "application/x-www-form-urlencoded")
                            .addHeader("User-Agent", buildGitHubUserAgent())
                            .build()

                    client.newCall(request).execute().use { response ->
                        val raw = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            error("HTTP ${response.code}: $raw")
                        }

                        val parsed = gson.fromJson(raw, GitHubDeviceTokenResponse::class.java)
                        val token = parsed.access_token?.trim().orEmpty()
                        if (token.isNotBlank()) {
                            return@runCatching GitHubDeviceTokenResult(
                                accessToken = token,
                                tokenType = parsed.token_type?.trim(),
                                scope = parsed.scope?.trim()
                            )
                        }

                        when (parsed.error?.trim()?.lowercase()) {
                            "authorization_pending" -> {
                                delay(intervalSeconds * 1000L + GITHUB_POLLING_SAFETY_MARGIN_MS)
                                return@use
                            }
                            "slow_down" -> {
                                val serverInterval = parsed.interval?.takeIf { it > 0 }
                                intervalSeconds = (serverInterval ?: (intervalSeconds + 5)).coerceAtLeast(1)
                                delay(intervalSeconds * 1000L + GITHUB_POLLING_SAFETY_MARGIN_MS)
                                return@use
                            }
                            null, "" -> error("Missing access_token in response")
                            else -> {
                                val description = parsed.error_description?.trim().orEmpty()
                                val suffix = if (description.isBlank()) "" else ": $description"
                                error("GitHub device code failed: ${parsed.error}$suffix")
                            }
                        }
                    }
                }

                error("GitHub device code timed out")
            }
        }
    }

    fun extractCodexAccountId(idToken: String?, accessToken: String?): String? {
        return parseCodexTokenClaims(idToken)?.accountId ?: parseCodexTokenClaims(accessToken)?.accountId
    }

    fun extractCodexEmail(idToken: String?, accessToken: String?): String? {
        return parseCodexTokenClaims(idToken)?.email ?: parseCodexTokenClaims(accessToken)?.email
    }

    fun startCodexOAuth(): OAuthStartResult {
        val state = generateRandomState()
        val codeVerifier = generatePkceCodeVerifier()
        val codeChallenge = generatePkceCodeChallenge(codeVerifier)

        val authUrl =
            Uri.parse(CODEX_AUTH_URL).buildUpon()
                .appendQueryParameter("client_id", CODEX_CLIENT_ID)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("redirect_uri", CODEX_REDIRECT_URI)
                .appendQueryParameter("scope", "openid profile email offline_access")
                .appendQueryParameter("state", state)
                .appendQueryParameter("code_challenge", codeChallenge)
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("id_token_add_organizations", "true")
                .appendQueryParameter("codex_cli_simplified_flow", "true")
                .appendQueryParameter("originator", CODEX_ORIGINATOR)
                .build()
                .toString()

        return OAuthStartResult(
            provider = OAuthProvider.Codex,
            authUrl = authUrl,
            redirectUri = CODEX_REDIRECT_URI,
            state = state,
            pkceCodeVerifier = codeVerifier
        )
    }

    fun startIFlowOAuth(): OAuthStartResult {
        val state = generateRandomState()
        val redirectUri = "http://localhost:$IFLOW_CALLBACK_PORT/oauth2callback"
        val clientId = resolveIFlowClientId()
        val authUrl =
            Uri.parse(IFLOW_AUTHORIZE_URL).buildUpon()
                .appendQueryParameter("loginMethod", "phone")
                .appendQueryParameter("type", "phone")
                .appendQueryParameter("redirect", redirectUri)
                .appendQueryParameter("state", state)
                .appendQueryParameter("client_id", clientId)
                .build()
                .toString()

        return OAuthStartResult(
            provider = OAuthProvider.IFlow,
            authUrl = authUrl,
            redirectUri = redirectUri,
            state = state
        )
    }

    suspend fun startQwenOAuth(): Result<OAuthStartResult> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val state = generateRandomState()
                val codeVerifier = generatePkceCodeVerifier()
                val codeChallenge = generatePkceCodeChallenge(codeVerifier)
                val form =
                    FormBody.Builder()
                        .add("client_id", QWEN_CLIENT_ID)
                        .add("scope", QWEN_SCOPE)
                        .add("code_challenge", codeChallenge)
                        .add("code_challenge_method", "S256")
                        .build()
                val request =
                    Request.Builder()
                        .url(QWEN_DEVICE_CODE_URL)
                        .post(form)
                        .addHeader("Accept", "application/json")
                        .addHeader("Content-Type", "application/x-www-form-urlencoded")
                        .build()

                client.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) error("HTTP ${response.code}: $raw")
                    val parsed = gson.fromJson(raw, QwenDeviceCodeResponse::class.java)
                    val authUrl =
                        parsed.verification_uri_complete?.trim().takeIf { !it.isNullOrBlank() }
                            ?: parsed.verification_uri?.trim().orEmpty()
                    val deviceCode = parsed.device_code?.trim().orEmpty()
                    if (authUrl.isBlank() || deviceCode.isBlank()) {
                        error("Invalid Qwen device authorization response")
                    }
                    OAuthStartResult(
                        provider = OAuthProvider.QwenCode,
                        authUrl = authUrl,
                        redirectUri = "",
                        state = state,
                        pkceCodeVerifier = codeVerifier,
                        deviceCode = deviceCode,
                        pollIntervalSeconds = parsed.interval?.coerceAtLeast(1) ?: QWEN_DEFAULT_POLL_INTERVAL_SECONDS
                    )
                }
            }
        }
    }

    fun parseCallback(input: String): OAuthCallback? {
        val raw = input.trim()
        if (raw.isBlank()) return null

        val uri =
            runCatching {
                when {
                    raw.startsWith("http://", ignoreCase = true) ||
                        raw.startsWith("https://", ignoreCase = true) -> Uri.parse(raw)
                    raw.startsWith("localhost", ignoreCase = true) -> Uri.parse("http://$raw")
                    raw.startsWith("?") -> Uri.parse("http://localhost/$raw")
                    raw.contains("code=") || raw.contains("state=") -> Uri.parse("http://localhost/?$raw")
                    else -> Uri.parse(raw)
                }
            }.getOrNull() ?: return null

        return OAuthCallback(
            code = uri.getQueryParameter("code"),
            state = uri.getQueryParameter("state"),
            error = uri.getQueryParameter("error"),
            errorDescription = uri.getQueryParameter("error_description")
        )
    }

    suspend fun exchangeCodex(code: String, redirectUri: String, pkceCodeVerifier: String): Result<CodexOAuthResult> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val form =
                    FormBody.Builder()
                        .add("grant_type", "authorization_code")
                        .add("client_id", CODEX_CLIENT_ID)
                        .add("code", code)
                        .add("redirect_uri", redirectUri)
                        .add("code_verifier", pkceCodeVerifier)
                        .build()

                val request =
                    Request.Builder()
                        .url(CODEX_TOKEN_URL)
                        .post(form)
                        .addHeader("Accept", "application/json")
                        .addHeader("Content-Type", "application/x-www-form-urlencoded")
                        .build()

                client.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) error("HTTP ${response.code}: $raw")

                    val parsed = gson.fromJson(raw, CodexTokenResponse::class.java)
                    val accessToken = parsed.access_token?.trim().orEmpty()
                    if (accessToken.isBlank()) error("Missing access_token in response")

                    val expiresInSec = parsed.expires_in?.coerceAtLeast(0L) ?: 0L
                    val expiresAtMs = System.currentTimeMillis() + expiresInSec * 1000L

                    val idToken = parsed.id_token?.trim()
                    val refreshToken = parsed.refresh_token?.trim()

                    val claims =
                        parseCodexTokenClaims(idToken) ?: parseCodexTokenClaims(accessToken)

                    CodexOAuthResult(
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        idToken = idToken,
                        expiresAtMs = expiresAtMs,
                        email = claims?.email,
                        accountId = claims?.accountId
                    )
                }
            }
        }
    }

    suspend fun exchangeIFlow(code: String, redirectUri: String): Result<IFlowOAuthResult> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val clientId = resolveIFlowClientId()
                val clientSecret = requireIFlowClientSecret()
                val form =
                    FormBody.Builder()
                        .add("grant_type", "authorization_code")
                        .add("code", code)
                        .add("redirect_uri", redirectUri)
                        .add("client_id", clientId)
                        .add("client_secret", clientSecret)
                        .build()

                val basic =
                    Base64.encodeToString(
                        "${clientId}:${clientSecret}".toByteArray(),
                        Base64.NO_WRAP
                    )

                val request =
                    Request.Builder()
                        .url(IFLOW_TOKEN_URL)
                        .post(form)
                        .addHeader("Accept", "application/json")
                        .addHeader("Content-Type", "application/x-www-form-urlencoded")
                        .addHeader("Authorization", "Basic $basic")
                        .build()

                val token =
                    client.newCall(request).execute().use { response ->
                        val raw = response.body?.string().orEmpty()
                        if (!response.isSuccessful) error("HTTP ${response.code}: $raw")
                        gson.fromJson(raw, IFlowTokenResponse::class.java)
                    }

                val accessToken = token.access_token?.trim().orEmpty()
                if (accessToken.isBlank()) error("Missing access_token in response")

                val expiresInSec = token.expires_in?.coerceAtLeast(0L) ?: 0L
                val expiresAtMs = System.currentTimeMillis() + expiresInSec * 1000L

                val info = fetchIFlowUserInfo(accessToken)
                val apiKey = info.apiKey?.trim().orEmpty()
                if (apiKey.isBlank()) error("Empty apiKey returned by iFlow user info")

                val email = info.email?.trim().takeIf { !it.isNullOrBlank() } ?: info.phone?.trim()

                IFlowOAuthResult(
                    accessToken = accessToken,
                    refreshToken = token.refresh_token?.trim(),
                    apiKey = apiKey,
                    expiresAtMs = expiresAtMs,
                    email = email
                )
            }
        }
    }

    suspend fun refreshCodex(refreshToken: String): Result<CodexOAuthResult> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val token = refreshToken.trim()
                if (token.isBlank()) error("Missing refresh token")

                val form =
                    FormBody.Builder()
                        .add("client_id", CODEX_CLIENT_ID)
                        .add("grant_type", "refresh_token")
                        .add("refresh_token", token)
                        .add("scope", "openid profile email")
                        .build()

                val request =
                    Request.Builder()
                        .url(CODEX_TOKEN_URL)
                        .post(form)
                        .addHeader("Accept", "application/json")
                        .addHeader("Content-Type", "application/x-www-form-urlencoded")
                        .build()

                client.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) error("HTTP ${response.code}: $raw")

                    val parsed = gson.fromJson(raw, CodexTokenResponse::class.java)
                    val accessToken = parsed.access_token?.trim().orEmpty()
                    if (accessToken.isBlank()) error("Missing access_token in response")

                    val expiresInSec = parsed.expires_in?.coerceAtLeast(0L) ?: 0L
                    val expiresAtMs = System.currentTimeMillis() + expiresInSec * 1000L

                    val idToken = parsed.id_token?.trim()
                    val newRefreshToken = parsed.refresh_token?.trim().takeIf { !it.isNullOrBlank() }

                    val claims =
                        parseCodexTokenClaims(idToken) ?: parseCodexTokenClaims(accessToken)

                    CodexOAuthResult(
                        accessToken = accessToken,
                        refreshToken = newRefreshToken ?: token,
                        idToken = idToken,
                        expiresAtMs = expiresAtMs,
                        email = claims?.email,
                        accountId = claims?.accountId
                    )
                }
            }
        }
    }

    suspend fun refreshIFlow(refreshToken: String): Result<IFlowOAuthResult> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val token = refreshToken.trim()
                if (token.isBlank()) error("Missing refresh token")
                val clientId = resolveIFlowClientId()
                val clientSecret = requireIFlowClientSecret()

                val form =
                    FormBody.Builder()
                        .add("grant_type", "refresh_token")
                        .add("refresh_token", token)
                        .add("client_id", clientId)
                        .add("client_secret", clientSecret)
                        .build()

                val basic =
                    Base64.encodeToString(
                        "${clientId}:${clientSecret}".toByteArray(),
                        Base64.NO_WRAP
                    )

                val request =
                    Request.Builder()
                        .url(IFLOW_TOKEN_URL)
                        .post(form)
                        .addHeader("Accept", "application/json")
                        .addHeader("Content-Type", "application/x-www-form-urlencoded")
                        .addHeader("Authorization", "Basic $basic")
                        .build()

                val tokenResp =
                    client.newCall(request).execute().use { response ->
                        val raw = response.body?.string().orEmpty()
                        if (!response.isSuccessful) error("HTTP ${response.code}: $raw")
                        gson.fromJson(raw, IFlowTokenResponse::class.java)
                    }

                val accessToken = tokenResp.access_token?.trim().orEmpty()
                if (accessToken.isBlank()) error("Missing access_token in response")

                val expiresInSec = tokenResp.expires_in?.coerceAtLeast(0L) ?: 0L
                val expiresAtMs = System.currentTimeMillis() + expiresInSec * 1000L

                val info = fetchIFlowUserInfo(accessToken)
                val apiKey = info.apiKey?.trim().orEmpty()
                if (apiKey.isBlank()) error("Empty apiKey returned by iFlow user info")

                val email = info.email?.trim().takeIf { !it.isNullOrBlank() } ?: info.phone?.trim()
                val newRefreshToken = tokenResp.refresh_token?.trim().takeIf { !it.isNullOrBlank() } ?: token

                IFlowOAuthResult(
                    accessToken = accessToken,
                    refreshToken = newRefreshToken,
                    apiKey = apiKey,
                    expiresAtMs = expiresAtMs,
                    email = email
                )
            }
        }
    }

    suspend fun exchangeQwenDeviceCode(
        deviceCode: String,
        pkceCodeVerifier: String,
        pollIntervalSeconds: Int = QWEN_DEFAULT_POLL_INTERVAL_SECONDS
    ): Result<QwenOAuthResult> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val normalizedDeviceCode = deviceCode.trim()
                if (normalizedDeviceCode.isBlank()) error("Missing Qwen device_code")
                val verifier = pkceCodeVerifier.trim()
                if (verifier.isBlank()) error("Missing Qwen code_verifier")
                var currentIntervalMs = pollIntervalSeconds.coerceAtLeast(1) * 1000L

                for (attempt in 0 until QWEN_MAX_POLL_ATTEMPTS) {
                    val form =
                        FormBody.Builder()
                            .add("grant_type", QWEN_DEVICE_GRANT_TYPE)
                            .add("client_id", QWEN_CLIENT_ID)
                            .add("device_code", normalizedDeviceCode)
                            .add("code_verifier", verifier)
                            .build()
                    val request =
                        Request.Builder()
                            .url(QWEN_TOKEN_URL)
                            .post(form)
                            .addHeader("Accept", "application/json")
                            .addHeader("Content-Type", "application/x-www-form-urlencoded")
                            .build()

                    var shouldRetry = false
                    client.newCall(request).execute().use { response ->
                        val raw = response.body?.string().orEmpty()
                        if (response.isSuccessful) {
                            return@runCatching parseQwenTokenResult(raw)
                        }

                        val errorType = parseJsonStringField(raw, "error")?.lowercase().orEmpty()
                        val errorDesc = parseJsonStringField(raw, "error_description").orEmpty()
                        when (errorType) {
                            "authorization_pending" -> {
                                shouldRetry = true
                                return@use
                            }
                            "slow_down" -> {
                                currentIntervalMs = (currentIntervalMs * 3L / 2L).coerceAtMost(10_000L)
                                shouldRetry = true
                                return@use
                            }
                            "expired_token" -> error("Qwen 授权已过期，请重新开始 OAuth")
                            "access_denied" -> error("Qwen 授权被拒绝，请重试")
                        }
                        if (errorDesc.isNotBlank()) {
                            error("Qwen OAuth failed: $errorDesc")
                        }
                        error("HTTP ${response.code}: $raw")
                    }
                    if (shouldRetry) {
                        if (attempt == QWEN_MAX_POLL_ATTEMPTS - 1) {
                            error("Qwen 授权超时，请重试")
                        }
                        delay(currentIntervalMs)
                        continue
                    }
                }
                error("Qwen 授权超时，请重试")
            }
        }
    }

    suspend fun refreshQwen(refreshToken: String): Result<QwenOAuthResult> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val token = refreshToken.trim()
                if (token.isBlank()) error("Missing refresh token")
                val form =
                    FormBody.Builder()
                        .add("grant_type", "refresh_token")
                        .add("refresh_token", token)
                        .add("client_id", QWEN_CLIENT_ID)
                        .build()
                val request =
                    Request.Builder()
                        .url(QWEN_TOKEN_URL)
                        .post(form)
                        .addHeader("Accept", "application/json")
                        .addHeader("Content-Type", "application/x-www-form-urlencoded")
                        .build()

                client.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) error("HTTP ${response.code}: $raw")
                    val parsed = parseQwenTokenResult(raw)
                    parsed.copy(refreshToken = parsed.refreshToken ?: token)
                }
            }
        }
    }

    private fun fetchIFlowUserInfo(accessToken: String): IFlowUserInfoData {
        val url = "${IFLOW_USERINFO_URL}?accessToken=${Uri.encode(accessToken)}"
        val request = Request.Builder().url(url).get().addHeader("Accept", "application/json").build()
        return client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("HTTP ${response.code}: $raw")
            val parsed = gson.fromJson(raw, IFlowUserInfoResponse::class.java)
            if (!parsed.success) error("iFlow user info returned success=false")
            parsed.data ?: error("iFlow user info missing data")
        }
    }

    private fun parseCodexTokenClaims(token: String?): CodexIdClaims? {
        val trimmed = token?.trim().orEmpty()
        if (trimmed.isBlank()) return null

        val payloadJson = parseJwtPayload(trimmed) ?: return null
        val email = payloadJson.get("email")?.asString?.trim()
        val accountId = extractAccountIdFromClaims(payloadJson)

        if (email.isNullOrBlank() && accountId.isNullOrBlank()) return null
        return CodexIdClaims(email = email, accountId = accountId)
    }

    private fun parseJwtPayload(token: String): JsonObject? {
        val parts = token.split(".")
        if (parts.size != 3) return null
        val payload = base64UrlDecode(parts[1]) ?: return null
        return runCatching { JsonParser.parseString(String(payload)).asJsonObject }.getOrNull()
    }

    private fun extractAccountIdFromClaims(payloadJson: JsonObject): String? {
        payloadJson.get("chatgpt_account_id")
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        payloadJson.getAsJsonObject("https://api.openai.com/auth")
            ?.get("chatgpt_account_id")
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        val org0 =
            payloadJson.getAsJsonArray("organizations")
                ?.firstOrNull()
                ?.takeIf { it.isJsonObject }
                ?.asJsonObject
        org0?.get("id")
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        return null
    }

    private fun parseQwenTokenResult(raw: String): QwenOAuthResult {
        val parsed = gson.fromJson(raw, QwenTokenResponse::class.java)
        val accessToken = parsed.access_token?.trim().orEmpty()
        if (accessToken.isBlank()) error("Missing access_token in response")
        val expiresInSec = parsed.expires_in?.coerceAtLeast(0L) ?: 0L
        val expiresAtMs = System.currentTimeMillis() + expiresInSec * 1000L
        val resourceUrl = parsed.resource_url?.trim()
        return QwenOAuthResult(
            accessToken = accessToken,
            refreshToken = parsed.refresh_token?.trim(),
            expiresAtMs = expiresAtMs,
            resourceUrl = resourceUrl,
            apiBaseUrl = resolveQwenResourceBaseUrl(resourceUrl)
        )
    }

    private fun parseJsonStringField(raw: String, key: String): String? {
        return runCatching {
            JsonParser.parseString(raw).asJsonObject.get(key)
                ?.takeIf { !it.isJsonNull }
                ?.asString
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun generateRandomState(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun generatePkceCodeVerifier(): String {
        val bytes = ByteArray(96)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun generatePkceCodeChallenge(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(codeVerifier.toByteArray())
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun base64UrlDecode(input: String): ByteArray? {
        var s = input
        val mod = s.length % 4
        if (mod != 0) s += "=".repeat(4 - mod)
        return runCatching { Base64.decode(s, Base64.URL_SAFE or Base64.NO_WRAP) }.getOrNull()
    }

    data class OAuthStartResult(
        val provider: OAuthProvider,
        val authUrl: String,
        val redirectUri: String,
        val state: String,
        val pkceCodeVerifier: String? = null,
        val deviceCode: String? = null,
        val pollIntervalSeconds: Int? = null
    )

    data class GitHubDeviceCodeStart(
        val domain: String,
        val verificationUri: String,
        val userCode: String,
        val deviceCode: String,
        val pollIntervalSeconds: Int,
        val expiresInSeconds: Int
    )

    data class GitHubDeviceTokenResult(
        val accessToken: String,
        val tokenType: String?,
        val scope: String?
    )

    enum class OAuthProvider {
        Codex,
        IFlow,
        QwenCode
    }

    data class OAuthCallback(
        val code: String?,
        val state: String?,
        val error: String?,
        val errorDescription: String?
    )

    data class CodexOAuthResult(
        val accessToken: String,
        val refreshToken: String?,
        val idToken: String?,
        val expiresAtMs: Long,
        val email: String?,
        val accountId: String?
    )

    data class IFlowOAuthResult(
        val accessToken: String,
        val refreshToken: String?,
        val apiKey: String,
        val expiresAtMs: Long,
        val email: String?
    )

    data class QwenOAuthResult(
        val accessToken: String,
        val refreshToken: String?,
        val expiresAtMs: Long,
        val resourceUrl: String?,
        val apiBaseUrl: String
    )

    private data class CodexTokenResponse(
        val access_token: String?,
        val refresh_token: String?,
        val id_token: String?,
        val expires_in: Long?
    )

    private data class IFlowTokenResponse(
        val access_token: String?,
        val refresh_token: String?,
        val expires_in: Long?,
        val token_type: String?,
        val scope: String?
    )

    private data class QwenDeviceCodeResponse(
        val device_code: String?,
        val verification_uri: String?,
        val verification_uri_complete: String?,
        val interval: Int?
    )

    private data class GitHubDeviceCodeResponse(
        val device_code: String?,
        val user_code: String?,
        val verification_uri: String?,
        val verification_uri_complete: String?,
        val interval: Int?,
        val expires_in: Int?
    )

    private data class GitHubDeviceTokenResponse(
        val access_token: String?,
        val token_type: String?,
        val scope: String?,
        val error: String?,
        val error_description: String?,
        val interval: Int?
    )

    private data class QwenTokenResponse(
        val access_token: String?,
        val refresh_token: String?,
        val token_type: String?,
        val resource_url: String?,
        val expires_in: Long?
    )

    private data class IFlowUserInfoResponse(
        val success: Boolean,
        val data: IFlowUserInfoData?
    )

    private data class IFlowUserInfoData(
        val apiKey: String?,
        val email: String?,
        val phone: String?
    )

    private data class CodexIdClaims(
        val email: String?,
        val accountId: String?
    )

    private fun resolveQwenResourceBaseUrl(resourceUrl: String?): String {
        val raw = resourceUrl?.trim().orEmpty().trimEnd('/')
        if (raw.isBlank()) return QWEN_DEFAULT_API_BASE_URL
        val withScheme =
            when {
                raw.startsWith("http://", ignoreCase = true) ||
                    raw.startsWith("https://", ignoreCase = true) -> raw
                else -> "https://$raw"
            }
        return if (withScheme.endsWith("/v1", ignoreCase = true)) withScheme else "$withScheme/v1"
    }

    private fun resolveIFlowClientId(): String {
        return BuildConfig.IFLOW_CLIENT_ID.trim().ifBlank { DEFAULT_IFLOW_CLIENT_ID }
    }

    private fun requireIFlowClientSecret(): String {
        val secret = BuildConfig.IFLOW_CLIENT_SECRET.trim()
        if (secret.isBlank()) {
            error("Missing IFLOW_CLIENT_SECRET. Please configure it in build environment.")
        }
        return secret
    }

    private fun normalizeGitHubDomain(input: String): String {
        val raw = input.trim()
        if (raw.isBlank()) return "github.com"
        val noScheme = raw.replace(Regex("^https?://", RegexOption.IGNORE_CASE), "")
        return noScheme.trim().trimEnd('/').ifBlank { "github.com" }
    }

    private fun buildGitHubUserAgent(): String {
        val v = BuildConfig.VERSION_NAME.trim().ifBlank { "dev" }
        return "ZionChat/$v"
    }

    companion object {
        private const val CODEX_AUTH_URL = "https://auth.openai.com/oauth/authorize"
        private const val CODEX_TOKEN_URL = "https://auth.openai.com/oauth/token"
        private const val CODEX_CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"
        private const val CODEX_REDIRECT_URI = "http://localhost:1455/auth/callback"
        private const val CODEX_ORIGINATOR = "codex_cli_rs"

        private const val IFLOW_AUTHORIZE_URL = "https://iflow.cn/oauth"
        private const val IFLOW_TOKEN_URL = "https://iflow.cn/oauth/token"
        private const val IFLOW_USERINFO_URL = "https://iflow.cn/api/oauth/getUserInfo"
        private const val DEFAULT_IFLOW_CLIENT_ID = "10009311001"
        private const val IFLOW_CALLBACK_PORT = 11451

        private const val QWEN_DEVICE_CODE_URL = "https://chat.qwen.ai/api/v1/oauth2/device/code"
        private const val QWEN_TOKEN_URL = "https://chat.qwen.ai/api/v1/oauth2/token"
        private const val QWEN_CLIENT_ID = "f0304373b74a44d2b584a3fb70ca9e56"
        private const val QWEN_SCOPE = "openid profile email model.completion"
        private const val QWEN_DEVICE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"
        private const val QWEN_DEFAULT_API_BASE_URL = "https://portal.qwen.ai/v1"
        private const val QWEN_DEFAULT_POLL_INTERVAL_SECONDS = 5
        private const val QWEN_MAX_POLL_ATTEMPTS = 60

        private const val GITHUB_COPILOT_CLIENT_ID = "Ov23li8tweQw6odWQebz"
        private const val GITHUB_COPILOT_SCOPE = "read:user"
        private const val GITHUB_DEVICE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"
        private const val GITHUB_DEFAULT_POLL_INTERVAL_SECONDS = 5
        private const val GITHUB_DEFAULT_EXPIRES_IN_SECONDS = 900
        private const val GITHUB_POLLING_SAFETY_MARGIN_MS = 3000L
    }
}
