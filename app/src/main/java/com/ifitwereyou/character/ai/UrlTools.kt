package com.ifitwereyou.character.ai

import java.net.URI

const val NOT_CONFIGURED_MESSAGE = "尚未配置 AI 服务，请先在「AI 设置」中添加 API 与模型"
const val DEFAULT_AI_TIMEOUT_SECONDS = 300.0

private val VERSION_SEGMENT = Regex("(?i)(?:^|/)v\\d+(?:beta|alpha)?(?:/|$)")
private val ENDPOINT_SUFFIXES = listOf("/chat/completions", "/completions", "/models")

class AiConfigurationException(message: String) : IllegalArgumentException(message)

data class ResolvedAiConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val timeoutSeconds: Double,
    val searchProvider: String,
    val tavilyApiKey: String,
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
        timeoutSeconds = DEFAULT_AI_TIMEOUT_SECONDS,
        searchProvider = source.searchProvider.lowercase(),
        tavilyApiKey = source.tavilyApiKey,
    )
}
