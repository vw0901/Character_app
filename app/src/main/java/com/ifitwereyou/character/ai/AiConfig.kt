package com.ifitwereyou.character.ai

import android.content.Context

data class AiConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val searchProvider: String = "none",
    val tavilyApiKey: String = "",
) {
    fun trimmed(): AiConfig = copy(
        baseUrl = baseUrl.trim(),
        apiKey = apiKey.trim(),
        model = model.trim(),
        searchProvider = if (searchProvider == "tavily") "tavily" else "none",
        tavilyApiKey = tavilyApiKey.trim(),
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
        description = "Kimi 开放平台",
        baseUrl = "https://api.moonshot.cn/v1",
        model = "moonshot-v1-8k",
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
                    searchProvider = if (value.opt("searchProvider") == "tavily") "tavily" else "none",
                    tavilyApiKey = (value.opt("tavilyApiKey") as? String).orEmpty(),
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
