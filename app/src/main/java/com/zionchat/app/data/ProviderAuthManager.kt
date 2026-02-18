package com.zionchat.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProviderAuthManager(
    private val repository: AppRepository,
    private val oauthClient: OAuthClient
) {
    suspend fun ensureValidProvider(provider: ProviderConfig, forceRefresh: Boolean = false): ProviderConfig {
        val oauthProvider = provider.oauthProvider?.trim()?.lowercase().orEmpty()
        if (oauthProvider.isBlank()) return provider

        var patched = provider
        if (oauthProvider == "codex") {
            val needsAccountId = patched.oauthAccountId.isNullOrBlank()
            val needsEmail = patched.oauthEmail.isNullOrBlank()
            if (needsAccountId || needsEmail) {
                val tokenForFallback = patched.oauthAccessToken ?: patched.apiKey
                val accountId = oauthClient.extractCodexAccountId(patched.oauthIdToken, tokenForFallback)
                val email = oauthClient.extractCodexEmail(patched.oauthIdToken, tokenForFallback)
                val updated =
                    patched.copy(
                        oauthAccountId = if (needsAccountId) accountId ?: patched.oauthAccountId else patched.oauthAccountId,
                        oauthEmail = if (needsEmail) email ?: patched.oauthEmail else patched.oauthEmail
                    )
                if (updated != patched) {
                    patched = updated
                    withContext(Dispatchers.IO) { repository.upsertProvider(updated) }
                }
            }
        }

        val refreshToken = patched.oauthRefreshToken?.trim().orEmpty()
        if (refreshToken.isBlank()) return patched

        val expiresAtMs = patched.oauthExpiresAtMs ?: Long.MAX_VALUE
        val now = System.currentTimeMillis()
        val shouldRefresh = forceRefresh || expiresAtMs <= now + EXPIRY_SKEW_MS
        if (!shouldRefresh) return patched

        return withContext(Dispatchers.IO) {
            val updated =
                when (oauthProvider) {
                    "codex" ->
                        oauthClient.refreshCodex(refreshToken).getOrThrow().let { token ->
                            patched.copy(
                                apiKey = token.accessToken,
                                oauthAccessToken = token.accessToken,
                                oauthRefreshToken = token.refreshToken ?: patched.oauthRefreshToken,
                                oauthIdToken = token.idToken ?: patched.oauthIdToken,
                                oauthAccountId = token.accountId ?: patched.oauthAccountId,
                                oauthEmail = token.email ?: patched.oauthEmail,
                                oauthExpiresAtMs = token.expiresAtMs
                            )
                        }
                    "iflow" ->
                        oauthClient.refreshIFlow(refreshToken).getOrThrow().let { token ->
                            patched.copy(
                                apiKey = token.apiKey,
                                oauthAccessToken = token.accessToken,
                                oauthRefreshToken = token.refreshToken ?: patched.oauthRefreshToken,
                                oauthEmail = token.email ?: patched.oauthEmail,
                                oauthExpiresAtMs = token.expiresAtMs
                            )
                        }
                    "qwen" ->
                        oauthClient.refreshQwen(refreshToken).getOrThrow().let { token ->
                            patched.copy(
                                apiUrl = token.apiBaseUrl.ifBlank { patched.apiUrl },
                                apiKey = token.accessToken,
                                oauthAccessToken = token.accessToken,
                                oauthRefreshToken = token.refreshToken ?: patched.oauthRefreshToken,
                                oauthExpiresAtMs = token.expiresAtMs
                            )
                        }
                    else -> patched
                }

            if (updated != patched) {
                repository.upsertProvider(updated)
            }
            updated
        }
    }

    private companion object {
        private const val EXPIRY_SKEW_MS = 60_000L
    }
}
