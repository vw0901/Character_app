package com.ifitwereyou.character.ai

import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

open class AiServiceException(message: String) : Exception(message)

class AiHttpException(
    val status: Int,
    message: String,
    /** 服务商返回的错误详情（已截断的响应体摘要），用于排障。 */
    val detail: String = "",
) : Exception(message)

/** 输出因达到 max_tokens / 上下文上限被截断，继续解析只会得到残缺 JSON。 */
class AiTruncatedException(message: String) : AiServiceException(message)

fun httpErrorMessage(status: Int, fallback: String, detail: String = ""): String {
    val brief = detail.replace(Regex("\\s+"), " ").trim().take(300)
    val suffix = if (brief.isBlank()) "" else "（$brief）"
    return when (status) {
        401, 403 -> "AI 服务鉴权失败，请检查 API Key$suffix"
        404 -> "AI 服务地址或模型不存在，请检查 Base URL 与模型名称$suffix"
        429 -> "AI 服务请求过于频繁或额度不足，请稍后再试$suffix"
        else -> if (brief.isBlank()) {
            "$fallback（AI 服务返回 $status）"
        } else {
            "$fallback（AI 服务返回 $status：$brief）"
        }
    }
}

fun getAiErrorMessage(error: Throwable, fallback: String): String {
    val message = error.message.orEmpty()
    return when (error) {
        is AiConfigurationException -> message.ifBlank { NOT_CONFIGURED_MESSAGE }
        is AiTruncatedException -> message.ifBlank { "AI 输出被截断，请调高最大输出 Token 后重试" }
        is AiServiceException -> message.ifBlank { fallback }
        is AiHttpException -> message.ifBlank { httpErrorMessage(error.status, fallback, error.detail) }
        is SocketTimeoutException -> "AI 服务响应超时，请稍后重试"
        is InterruptedIOException -> "AI 服务响应超时，请稍后重试"
        is UnknownHostException, is ConnectException -> "无法连接到 AI 服务，请检查 Base URL 或网络设置"
        is javax.net.ssl.SSLException -> "无法连接到 AI 服务，请检查 Base URL 或网络设置"
        is java.io.IOException -> "无法连接到 AI 服务，请检查 Base URL 或网络设置"
        is IllegalArgumentException -> message.ifBlank { fallback }
        else -> fallback
    }
}
