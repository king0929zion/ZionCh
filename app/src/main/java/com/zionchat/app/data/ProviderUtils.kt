package com.zionchat.app.data

fun ProviderConfig.isCodexProvider(): Boolean {
    if (type.trim().equals("codex", ignoreCase = true)) return true
    if (presetId?.trim()?.equals("codex", ignoreCase = true) == true) return true
    if (oauthProvider?.trim()?.equals("codex", ignoreCase = true) == true) return true
    if (apiUrl.contains("/backend-api/codex", ignoreCase = true)) return true
    return false
}

fun ProviderConfig.isGrok2ApiProvider(): Boolean {
    val normalizedType = type.trim()
    if (normalizedType.equals("grok2api", ignoreCase = true)) return true
    if (normalizedType.equals("grok", ignoreCase = true)) return true
    if (presetId?.trim()?.equals("grok2api", ignoreCase = true) == true) return true
    if (presetId?.trim()?.equals("grok", ignoreCase = true) == true) return true
    if (apiUrl.contains("grok2api", ignoreCase = true)) return true
    return false
}
