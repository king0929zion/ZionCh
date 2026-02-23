package com.zionchat.app.data

fun isLikelyVisionModel(model: ModelConfig): Boolean {
    val signal = "${model.id} ${model.displayName} ${extractRemoteModelId(model.id)}".lowercase()
    return signal.contains("vision") ||
        signal.contains("vl") ||
        signal.contains("image") ||
        signal.contains("multimodal") ||
        signal.contains("omni") ||
        signal.contains("gpt-4o") ||
        signal.contains("gpt4o") ||
        signal.contains("gemini") ||
        signal.contains("claude-3") ||
        signal.contains("claude 3") ||
        signal.contains("qwen-vl") ||
        signal.contains("llava")
}
