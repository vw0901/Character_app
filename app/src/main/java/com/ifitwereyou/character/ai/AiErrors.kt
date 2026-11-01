package com.ifitwereyou.character.ai

import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class AiServiceException(message: String) : Exception(message)

class AiHttpException(
    val status: Int,
    message: String,
) : Exception(message)

fun httpErrorMessage(status: Int, fallback: String): String = when (status) {
    401, 403 -> "AI 服务鉴权失败，请检查 API Key"
    404 -> "AI 服务地址或模型不存在，请检查 Base URL 与模型名称"
    429 -> "AI 服务请求过于频繁或额度不足，请稍后再试"
    else -> "$fallback（AI 服务返回 $status）"
}

fun getAiErrorMessage(error: Throwable, fallback: String): String {
    val message = error.message.orEmpty()
    return when (error) {
        is AiConfigurationException -> message.ifBlank { NOT_CONFIGURED_MESSAGE }
        is AiServiceException -> message.ifBlank { fallback }
        is AiHttpException -> httpErrorMessage(error.status, fallback)
        is SocketTimeoutException -> "AI 服务响应超时，请稍后重试"
        is InterruptedIOException -> "AI 服务响应超时，请稍后重试"
        is UnknownHostException, is ConnectException -> "无法连接到 AI 服务，请检查 Base URL 或网络设置"
        is javax.net.ssl.SSLException -> "无法连接到 AI 服务，请检查 Base URL 或网络设置"
        is java.io.IOException -> "无法连接到 AI 服务，请检查 Base URL 或网络设置"
        is IllegalArgumentException -> message.ifBlank { fallback }
        else -> fallback
    }
}
