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
    val lowerName = name.trim().lowercase()
    val lowerPreset = presetId?.trim()?.lowercase().orEmpty()
    val lowerUrl = apiUrl.trim().lowercase()
    val localGateway =
        lowerUrl.contains("localhost") ||
            lowerUrl.contains("127.0.0.1") ||
            lowerUrl.contains("10.0.2.2") ||
            lowerUrl.contains("host.docker.internal")
    if (localGateway && (lowerName.contains("grok") || lowerName.contains("xai"))) return true
    if (localGateway && (lowerPreset == "xai" || lowerPreset == "grok2api" || lowerPreset == "grok")) return true
    if (lowerUrl.contains("api.x.ai") && (lowerName.contains("grok") || lowerName.contains("xai"))) return true
    return false
}
