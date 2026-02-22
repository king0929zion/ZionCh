package com.zionchat.app.data

data class ProviderPreset(
    val id: String,
    val name: String,
    val type: String,
    val apiUrl: String,
    val iconAsset: String?
)

val DEFAULT_PROVIDER_PRESETS: List<ProviderPreset> = listOf(
    ProviderPreset(
        id = "kimi",
        name = "Kimi",
        type = "openai",
        apiUrl = "https://api.moonshot.cn/v1",
        iconAsset = "kimi-color.svg"
    ),
    ProviderPreset(
        id = "openai",
        name = "OpenAI",
        type = "openai",
        apiUrl = "https://api.openai.com/v1",
        iconAsset = "openai.svg"
    ),
    ProviderPreset(
        id = "codex",
        name = "Codex",
        type = "codex",
        apiUrl = "https://chatgpt.com/backend-api/codex",
        iconAsset = "codex.svg"
    ),
    ProviderPreset(
        id = "github_copilot",
        name = "GitHub Copilot",
        type = "openai",
        apiUrl = "https://api.githubcopilot.com",
        iconAsset = "github.svg"
    ),
    ProviderPreset(
        id = "qwen_code",
        name = "Qwen Code",
        type = "openai",
        apiUrl = "https://portal.qwen.ai/v1",
        iconAsset = "qwen-color.svg"
    ),
    ProviderPreset(
        id = "anthropic",
        name = "Anthropic",
        type = "anthropic",
        apiUrl = "https://api.anthropic.com/v1",
        iconAsset = "anthropic.svg"
    ),
    ProviderPreset(
        id = "gemini",
        name = "Gemini",
        type = "google",
        apiUrl = "https://generativelanguage.googleapis.com/v1beta",
        iconAsset = "gemini-color.svg"
    ),
    ProviderPreset(
        id = "minimax",
        name = "MiniMax",
        type = "openai",
        apiUrl = "https://api.minimax.chat/v1",
        iconAsset = "minimax-color.svg"
    ),
    ProviderPreset(
        id = "siliconflow",
        name = "SiliconFlow",
        type = "openai",
        apiUrl = "https://api.siliconflow.cn/v1",
        iconAsset = "siliconflow.svg"
    ),
    ProviderPreset(
        id = "deepseek",
        name = "DeepSeek",
        type = "openai",
        apiUrl = "https://api.deepseek.com/v1",
        iconAsset = "deepseek-color.svg"
    ),
    ProviderPreset(
        id = "openrouter",
        name = "OpenRouter",
        type = "openai",
        apiUrl = "https://openrouter.ai/api/v1",
        iconAsset = "openrouter.svg"
    ),
    ProviderPreset(
        id = "alibabacloud",
        name = "Qwen",
        type = "openai",
        apiUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        iconAsset = "qwen-color.svg"
    ),
    ProviderPreset(
        id = "doubao",
        name = "Doubao",
        type = "openai",
        apiUrl = "https://ark.cn-beijing.volces.com/api/v3",
        iconAsset = "doubao-color.svg"
    ),
    ProviderPreset(
        id = "nvidia",
        name = "NVIDIA",
        type = "openai",
        apiUrl = "https://integrate.api.nvidia.com/v1",
        iconAsset = "nvidia-color.svg"
    ),
    ProviderPreset(
        id = "modelscope",
        name = "ModelScope",
        type = "openai",
        apiUrl = "https://api-inference.modelscope.cn/v1",
        iconAsset = "modelscope.svg"
    ),
    ProviderPreset(
        id = "iflow",
        name = "iFlow",
        type = "openai",
        apiUrl = "https://apis.iflow.cn/v1",
        iconAsset = "iflow.svg"
    ),
    ProviderPreset(
        id = "longcat",
        name = "LongCat",
        type = "openai",
        apiUrl = "https://api.longcat.chat/openai/v1",
        iconAsset = "longcat.svg"
    ),
    ProviderPreset(
        id = "gmi",
        name = "GMI",
        type = "openai",
        apiUrl = "https://api.gmi-serving.com/v1",
        iconAsset = "gmi.svg"
    ),
    ProviderPreset(
        id = "moonshot",
        name = "Moonshot",
        type = "openai",
        apiUrl = "https://api.moonshot.cn/v1",
        iconAsset = "moonshot.svg"
    ),
    ProviderPreset(
        id = "zhipu",
        name = "Zhipu",
        type = "openai",
        apiUrl = "https://open.bigmodel.cn/api/paas/v4",
        iconAsset = "zhipu-color.svg"
    ),
    ProviderPreset(
        id = "stepfun",
        name = "StepFun",
        type = "openai",
        apiUrl = "https://api.stepfun.com/v1",
        iconAsset = "stepfun-color.svg"
    ),
    ProviderPreset(
        id = "hunyuan",
        name = "Hunyuan",
        type = "openai",
        apiUrl = "https://api.hunyuan.cloud.tencent.com/v1",
        iconAsset = "hunyuan-color.svg"
    ),
    ProviderPreset(
        id = "grok2api",
        name = "Grok",
        type = "grok2api",
        apiUrl = "https://grok.com",
        iconAsset = "grok.svg"
    ),
    ProviderPreset(
        id = "xai",
        name = "xAI",
        type = "openai",
        apiUrl = "https://api.x.ai/v1",
        iconAsset = "xai.svg"
    ),
)

fun findProviderPreset(presetId: String?): ProviderPreset? {
    val rawId = presetId?.trim().orEmpty()
    if (rawId.isBlank()) return null
    val id = when (rawId.lowercase()) {
        "bytedance" -> "doubao"
        "github-copilot" -> "github_copilot"
        "grok" -> "grok2api"
        "qwen" -> "qwen_code"
        else -> rawId
    }
    return DEFAULT_PROVIDER_PRESETS.firstOrNull { it.id == id }
}

fun resolveProviderIconAsset(provider: ProviderConfig): String? {
    provider.iconAsset?.takeIf { it.isNotBlank() }?.let { return it.trim() }
    provider.presetId?.let { presetId ->
        findProviderPreset(presetId)?.iconAsset?.let { return it }
    }
    return computeAIIconAssetByName(provider.name)
}

private fun computeAIIconAssetByName(name: String): String? {
    val lowerName = name.lowercase()
    return when {
        PATTERN_COPILOT.containsMatchIn(lowerName) -> "github.svg"
        PATTERN_CODEX.containsMatchIn(lowerName) -> "codex.svg"
        PATTERN_LONGCAT.containsMatchIn(lowerName) -> "longcat.svg"
        PATTERN_IFLOW.containsMatchIn(lowerName) -> "iflow.svg"
        PATTERN_MODELSCOPE.containsMatchIn(lowerName) -> "modelscope.svg"
        PATTERN_GMI.containsMatchIn(lowerName) -> "gmi.svg"
        PATTERN_NVIDIA.containsMatchIn(lowerName) -> "nvidia-color.svg"
        PATTERN_KIMI.containsMatchIn(lowerName) -> "kimi-color.svg"
        PATTERN_GROK.containsMatchIn(lowerName) -> "grok.svg"
        PATTERN_OPENAI.containsMatchIn(lowerName) -> "openai.svg"
        PATTERN_ANTHROPIC.containsMatchIn(lowerName) -> "anthropic.svg"
        PATTERN_GEMINI.containsMatchIn(lowerName) -> "gemini-color.svg"
        PATTERN_MINIMAX.containsMatchIn(lowerName) -> "minimax-color.svg"
        PATTERN_DEEPSEEK.containsMatchIn(lowerName) -> "deepseek-color.svg"
        PATTERN_OPENROUTER.containsMatchIn(lowerName) -> "openrouter.svg"
        PATTERN_QWEN.containsMatchIn(lowerName) -> "qwen-color.svg"
        PATTERN_DOUBAO.containsMatchIn(lowerName) -> "doubao-color.svg"
        PATTERN_SILLICON_CLOUD.containsMatchIn(lowerName) -> "siliconflow.svg"
        PATTERN_ZHIPU.containsMatchIn(lowerName) -> "zhipu-color.svg"
        PATTERN_MOONSHOT.containsMatchIn(lowerName) -> "moonshot.svg"
        PATTERN_STEP.containsMatchIn(lowerName) -> "stepfun-color.svg"
        PATTERN_HUNYUAN.containsMatchIn(lowerName) -> "hunyuan-color.svg"
        PATTERN_XAI.containsMatchIn(lowerName) -> "xai.svg"
        else -> null
    }
}

private val PATTERN_COPILOT = Regex("copilot")
private val PATTERN_CODEX = Regex("codex")
private val PATTERN_LONGCAT = Regex("longcat")
private val PATTERN_IFLOW = Regex("iflow")
private val PATTERN_MODELSCOPE = Regex("modelscope")
private val PATTERN_GMI = Regex("\\bgmi\\b|gmi-serving")
private val PATTERN_NVIDIA = Regex("nvidia")
private val PATTERN_KIMI = Regex("\\bkimi\\b")
private val PATTERN_GROK = Regex("grok")
private val PATTERN_OPENAI = Regex("(gpt|openai|o\\d)")
private val PATTERN_ANTHROPIC = Regex("anthropic|claude")
private val PATTERN_GEMINI = Regex("(gemini|nano-banana)")
private val PATTERN_MINIMAX = Regex("minimax")
private val PATTERN_DEEPSEEK = Regex("deepseek")
private val PATTERN_OPENROUTER = Regex("openrouter")
private val PATTERN_ZHIPU = Regex("zhipu|智谱|glm")
private val PATTERN_DOUBAO = Regex("doubao|豆包|bytedance|火山")
private val PATTERN_QWEN = Regex("qwen|aliyun|阿里云|百炼")
private val PATTERN_SILLICON_CLOUD = Regex("silicon|硅基")
private val PATTERN_HUNYUAN = Regex("hunyuan|tencent")
private val PATTERN_XAI = Regex("xai")
private val PATTERN_MOONSHOT = Regex("moonshot|月之暗面")
private val PATTERN_STEP = Regex("step|阶跃")
