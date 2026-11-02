package com.ifitwereyou.character.ai

import org.json.JSONObject
import java.net.URI

const val NOT_CONFIGURED_MESSAGE = "尚未配置 AI 服务，请先在「AI 设置」中添加 API 与模型"

private val VERSION_SEGMENT = Regex("(?i)(?:^|/)v\\d+(?:beta|alpha)?(?:/|$)")
private val ENDPOINT_SUFFIXES = listOf("/chat/completions", "/completions", "/models")

class AiConfigurationException(message: String) : IllegalArgumentException(message)

data class ResolvedAiConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val searchProvider: String,
    val tavilyApiKey: String,
    val temperature: Double? = null,
    val topP: Double? = null,
    val maxTokens: Int? = null,
    val frequencyPenalty: Double? = null,
    val presencePenalty: Double? = null,
    val seed: Int? = null,
    val extraParams: JSONObject = JSONObject(),
)

fun normalizeBaseUrl(raw: String): String {
    val value = raw.trim().trimEnd('/')
    if (value.isEmpty()) throw AiConfigurationException("Base URL 不能为空")

    val parsed = try {
        URI(value)
    } catch (_: Exception) {
        throw AiConfigurationException("Base URL 必须是以 http:// 或 https:// 开头的完整地址")
    }
    if (parsed.scheme?.lowercase() !in setOf("http", "https") || parsed.rawAuthority.isNullOrBlank()) {
        throw AiConfigurationException("Base URL 必须是以 http:// 或 https:// 开头的完整地址")
    }
    if (parsed.rawQuery != null || parsed.rawFragment != null) {
        throw AiConfigurationException("Base URL 不能包含查询参数或片段")
    }

    var path = (parsed.rawPath ?: "").trimEnd('/')
    for (suffix in ENDPOINT_SUFFIXES) {
        if (path.lowercase().endsWith(suffix)) {
            path = path.dropLast(suffix.length).trimEnd('/')
            break
        }
    }

    path = when {
        path.isEmpty() -> "/v1"
        VERSION_SEGMENT.containsMatchIn(path) -> path
        else -> "$path/v1"
    }

    return "${parsed.scheme.lowercase()}://${parsed.rawAuthority}$path"
}

fun completionsUrl(baseUrl: String): String = "${normalizeBaseUrl(baseUrl)}/chat/completions"

fun modelsUrl(baseUrl: String): String = "${normalizeBaseUrl(baseUrl)}/models"

/** 明文 HTTP 且目标不是本机 / 局域网时，API Key 有被同网络截获的风险。 */
fun isInsecurePublicBaseUrl(raw: String): Boolean {
    val parsed = try {
        URI(raw.trim())
    } catch (_: Exception) {
        return false
    }
    if (parsed.scheme?.lowercase() != "http") return false
    val host = parsed.host?.lowercase()?.removePrefix("[")?.removeSuffix("]").orEmpty()
    if (host.isEmpty()) return false
    if (host == "localhost" || host == "::1" || host == "10.0.2.2" || host.endsWith(".local")) return false
    if (host.startsWith("127.") || host.startsWith("10.") || host.startsWith("192.168.")) return false
    if (host.startsWith("169.254.")) return false
    if (host.contains(":") &&
        (host.startsWith("fe80:") || host.startsWith("fc") || host.startsWith("fd"))
    ) {
        return false
    }
    if (host.startsWith("172.")) {
        val second = host.split('.').getOrNull(1)?.toIntOrNull()
        if (second != null && second in 16..31) return false
    }
    return true
}

fun resolveAiConfig(config: AiConfig?, requireModel: Boolean = true): ResolvedAiConfig {
    val source = config?.trimmed() ?: AiConfig()
    val baseUrl = source.baseUrl
    val model = source.model
    if (baseUrl.isEmpty() || (requireModel && model.isEmpty())) {
        throw AiConfigurationException(NOT_CONFIGURED_MESSAGE)
    }
    return ResolvedAiConfig(
        baseUrl = normalizeBaseUrl(baseUrl),
        apiKey = source.apiKey,
        model = model,
        searchProvider = source.searchProvider.lowercase(),
        tavilyApiKey = source.tavilyApiKey,
        temperature = source.temperature,
        topP = source.topP,
        maxTokens = source.maxTokens,
        frequencyPenalty = source.frequencyPenalty,
        presencePenalty = source.presencePenalty,
        seed = source.seed,
        extraParams = parseExtraParams(source.extraParams),
    )
}
