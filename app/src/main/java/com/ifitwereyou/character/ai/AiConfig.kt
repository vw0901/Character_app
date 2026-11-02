package com.ifitwereyou.character.ai

import android.content.Context
import org.json.JSONObject

/**
 * 一份 AI 配置。
 *
 * [temperature] 等采样参数为 null 时表示「跟随各业务场景的默认值」；
 * 填写后统一覆盖创作类请求。目标判定等确定性任务会主动清空采样参数。
 * [maxTokens] 是全局输出上限：填写后覆盖场景上限（便于推理模型调高预算），留空则使用场景值。
 */
data class AiConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val searchProvider: String = "none",
    val tavilyApiKey: String = "",
    val temperature: Double? = null,
    val topP: Double? = null,
    val maxTokens: Int? = null,
    val frequencyPenalty: Double? = null,
    val presencePenalty: Double? = null,
    val seed: Int? = null,
    val extraParams: String = "",
) {
    fun trimmed(): AiConfig = copy(
        baseUrl = baseUrl.trim(),
        apiKey = apiKey.trim(),
        model = model.trim(),
        searchProvider = if (searchProvider.trim().lowercase() == "tavily") "tavily" else "none",
        tavilyApiKey = tavilyApiKey.trim(),
        extraParams = extraParams.trim(),
    )

    /** 目标判定、连接测试等场景不继承用户的创作采样参数。 */
    fun withoutSamplingOverrides(): AiConfig = copy(
        temperature = null,
        topP = null,
        frequencyPenalty = null,
        presencePenalty = null,
    )
}

data class AiPreset(
    val id: String,
    val name: String,
    val description: String,
    val baseUrl: String,
    val model: String,
    val apiKey: String? = null,
)

val AI_PRESETS = listOf(
    AiPreset(
        id = "openai",
        name = "OpenAI",
        description = "官方 OpenAI 兼容接口",
        baseUrl = "https://api.openai.com/v1",
        model = "gpt-4o-mini",
    ),
    AiPreset(
        id = "deepseek",
        name = "DeepSeek",
        description = "DeepSeek 开放平台",
        baseUrl = "https://api.deepseek.com/v1",
        model = "deepseek-chat",
    ),
    AiPreset(
        id = "ollama",
        name = "Ollama",
        description = "本地模型，API Key 可留空",
        baseUrl = "http://127.0.0.1:11434/v1",
        model = "qwen2.5:14b",
        apiKey = "ollama",
    ),
    AiPreset(
        id = "ark",
        name = "火山方舟",
        description = "需要填写推理接入点 ID 作为模型",
        baseUrl = "https://ark.cn-beijing.volces.com/api/v3",
        model = "",
    ),
    AiPreset(
        id = "dashscope",
        name = "阿里百炼",
        description = "通义千问兼容模式",
        baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        model = "qwen-plus",
    ),
    AiPreset(
        id = "siliconflow",
        name = "硅基流动",
        description = "SiliconCloud 模型广场",
        baseUrl = "https://api.siliconflow.cn/v1",
        model = "Qwen/Qwen2.5-7B-Instruct",
    ),
    AiPreset(
        id = "moonshot",
        name = "Moonshot",
        description = "Kimi 开放平台（32k 上下文）",
        baseUrl = "https://api.moonshot.cn/v1",
        model = "moonshot-v1-32k",
    ),
    AiPreset(
        id = "zhipu",
        name = "智谱",
        description = "GLM 开放平台",
        baseUrl = "https://open.bigmodel.cn/api/paas/v4",
        model = "glm-4-flash",
    ),
)

const val DEFAULT_PRESET_ID = "openai"

fun emptyAiConfig(): AiConfig = AiConfig()

fun initialSettingsForm(): AiConfig {
    val preset = AI_PRESETS.first { it.id == DEFAULT_PRESET_ID }
    return AiConfig(
        baseUrl = preset.baseUrl,
        apiKey = preset.apiKey.orEmpty(),
        model = preset.model,
        searchProvider = "none",
        tavilyApiKey = "",
    )
}

/** 校验用户填写的采样参数；返回 null 表示通过。 */
fun validateAiParams(config: AiConfig): String? {
    config.temperature?.let {
        if (it.isNaN() || it < 0.0 || it > 2.0) return "Temperature 需在 0.0–2.0 之间"
    }
    config.topP?.let {
        if (it.isNaN() || it < 0.0 || it > 1.0) return "Top P 需在 0.0–1.0 之间"
    }
    config.maxTokens?.let {
        if (it < 1 || it > 200_000) return "最大输出 Token 需在 1–200000 之间"
    }
    config.frequencyPenalty?.let {
        if (it.isNaN() || it < -2.0 || it > 2.0) return "Frequency Penalty 需在 -2.0–2.0 之间"
    }
    config.presencePenalty?.let {
        if (it.isNaN() || it < -2.0 || it > 2.0) return "Presence Penalty 需在 -2.0–2.0 之间"
    }
    if (config.extraParams.isBlank()) return null

    val extra = try {
        JSONObject(config.extraParams)
    } catch (_: Exception) {
        return "自定义参数必须是合法的 JSON 对象，例如 {\"reasoning_effort\":\"low\"}"
    }
    for (protected in listOf("model", "messages", "stream")) {
        if (extra.has(protected)) return "自定义参数不能覆盖 $protected"
    }
    return null
}

/** 把自定义参数解析成 JSON；空字符串返回空对象。 */
internal fun parseExtraParams(raw: String): JSONObject {
    if (raw.isBlank()) return JSONObject()
    return try {
        JSONObject(raw)
    } catch (_: Exception) {
        throw AiConfigurationException("自定义参数必须是合法的 JSON 对象")
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(): AiConfig? {
        val raw = prefs.getString(KEY_CONFIG, null) ?: return null
        return parseSavedConfig(raw)
    }

    fun write(config: AiConfig) {
        val normalized = config.trimmed()
        val json = org.json.JSONObject().apply {
            put("baseUrl", normalized.baseUrl)
            put("apiKey", normalized.apiKey)
            put("model", normalized.model)
            put("searchProvider", normalized.searchProvider)
            put("tavilyApiKey", normalized.tavilyApiKey)
            normalized.temperature?.let { put("temperature", it) }
            normalized.topP?.let { put("topP", it) }
            normalized.maxTokens?.let { put("maxTokens", it) }
            normalized.frequencyPenalty?.let { put("frequencyPenalty", it) }
            normalized.presencePenalty?.let { put("presencePenalty", it) }
            normalized.seed?.let { put("seed", it) }
            if (normalized.extraParams.isNotBlank()) put("extraParams", normalized.extraParams)
        }
        prefs.edit().putString(KEY_CONFIG, json.toString()).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_CONFIG).apply()
    }

    companion object {
        private const val PREFS_NAME = "novel-ai-settings"
        private const val KEY_CONFIG = "novel-ai-config-v1"

        /** 与原项目 localStorage 的 normalize 规则保持一致。 */
        fun parseSavedConfig(raw: String): AiConfig? {
            return try {
                val value = org.json.JSONObject(raw)
                val baseUrl = (value.opt("baseUrl") as? String).orEmpty().trim()
                val model = (value.opt("model") as? String).orEmpty().trim()
                if (baseUrl.isEmpty() || model.isEmpty()) return null // 未配置完整时视为没有配置
                AiConfig(
                    baseUrl = baseUrl,
                    apiKey = (value.opt("apiKey") as? String).orEmpty(),
                    model = model,
                    searchProvider = if ((value.opt("searchProvider") as? String)?.lowercase() == "tavily") {
                        "tavily"
                    } else {
                        "none"
                    },
                    tavilyApiKey = (value.opt("tavilyApiKey") as? String).orEmpty(),
                    temperature = value.optNullableDouble("temperature"),
                    topP = value.optNullableDouble("topP"),
                    maxTokens = value.optNullableInt("maxTokens"),
                    frequencyPenalty = value.optNullableDouble("frequencyPenalty"),
                    presencePenalty = value.optNullableDouble("presencePenalty"),
                    seed = value.optNullableInt("seed"),
                    extraParams = (value.opt("extraParams") as? String).orEmpty(),
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

private fun JSONObject.optNullableDouble(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    val value = optDouble(key, Double.NaN)
    return if (value.isNaN()) null else value
}

private fun JSONObject.optNullableInt(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    val value = optInt(key, Int.MIN_VALUE)
    return if (value == Int.MIN_VALUE) null else value
}
