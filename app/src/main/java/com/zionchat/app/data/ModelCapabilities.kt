package com.zionchat.app.data

fun normalizeInputModality(raw: String?): String {
    return when (raw?.trim()?.lowercase()) {
        "text", "text-only", "text_only" -> "text"
        "text-image", "text_image", "image", "vision", "multimodal" -> "text-image"
        else -> "text-image"
    }
}

fun isLikelyVisionModel(model: ModelConfig): Boolean {
    val modality = normalizeInputModality(model.inputModality)
    if (modality == "text-image") return true
    if (modality == "text") return false

    val signal = "${model.id} ${model.displayName} ${extractRemoteModelId(model.id)}".lowercase()
    return signal.contains("vision") ||
        signal.contains("vl") ||
        signal.contains("image") ||
        signal.contains("multimodal") ||
        signal.contains("omni") ||
        signal.contains("gpt-4o") ||
        signal.contains("gpt4o") ||
        signal.contains("gpt-4.1") ||
        signal.contains("gpt4.1") ||
        signal.contains("gpt-4.5") ||
        signal.contains("gpt4.5") ||
        signal.contains("gemini") ||
        signal.contains("pixtral") ||
        signal.contains("claude-3") ||
        signal.contains("claude 3") ||
        signal.contains("qwen-vl") ||
        signal.contains("llava")
}
