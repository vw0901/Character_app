package com.ifitwereyou.character.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * OpenAI 兼容接口客户端。
 *
 * 与原项目后端的行为保持一致：
 * - 流式失败且尚未收到任何内容时，退回一次非流式请求；
 * - 鉴权 / 权限 / 限流错误不重试、不降级；
 * - 模型不接受 max_tokens 时去掉该参数重试一次。
 */
class AiClient(
    baseClient: OkHttpClient? = null,
) {
    private val http: OkHttpClient = baseClient ?: OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private fun clientFor(callTimeoutSeconds: Long?): OkHttpClient =
        if (callTimeoutSeconds == null) http
        else http.newBuilder().callTimeout(callTimeoutSeconds, TimeUnit.SECONDS).build()

    fun streamChat(
        config: AiConfig,
        messages: List<ChatMessage>,
        temperature: Double = 0.7,
        maxTokens: Int? = null,
        callTimeoutSeconds: Long? = null,
        fallbackLabel: String = "AI 请求失败",
    ): Flow<String> = flow {
        val resolved = resolveAiConfig(config)
        val body = buildBody(resolved.model, messages, temperature, maxTokens, stream = true)
        var emitted = false
        try {
            val response = requestCompletion(
                resolved = resolved,
                body = body,
                stream = true,
                callTimeoutSeconds = callTimeoutSeconds,
                fallbackLabel = fallbackLabel,
            )
            response.use { resp ->
                val contentType = resp.header("Content-Type").orEmpty()
                val bodyStream = resp.body
                if (bodyStream == null) throw AiServiceException("AI 服务未返回响应内容")

                if (contentType.contains("event-stream", ignoreCase = true) ||
                    contentType.contains("stream", ignoreCase = true)
                ) {
                    val reader = BufferedReader(InputStreamReader(bodyStream.byteStream(), StandardCharsets.UTF_8))
                    reader.use { input ->
                        while (true) {
                            val line = input.readLine() ?: break
                            val text = extractSseText(line) ?: continue
                            if (text.isNotEmpty()) {
                                emitted = true
                                emit(text)
                            }
                        }
                    }
                } else {
                    val raw = bodyStream.string()
                    val text = if (raw.trimStart().startsWith("data:")) {
                        raw.lineSequence().mapNotNull { extractSseText(it) }.joinToString("")
                    } else {
                        parseCompletionText(raw)
                    }
                    if (text.isNotEmpty()) {
                        emitted = true
                        emit(text)
                    }
                }
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (emitted || error is AiConfigurationException) throw error
            if (error is AiHttpException && error.status in setOf(401, 403, 429)) throw error
            // 部分兼容接口不支持流式：退回一次性请求，别让整轮冒险失败。
            val text = chatOnceInternal(
                resolved = resolved,
                messages = messages,
                temperature = temperature,
                maxTokens = maxTokens,
                callTimeoutSeconds = callTimeoutSeconds,
                fallbackLabel = fallbackLabel,
            )
            if (text.isNotEmpty()) {
                emit(text)
            } else {
                throw error
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun chatOnce(
        config: AiConfig,
        messages: List<ChatMessage>,
        temperature: Double = 0.3,
        maxTokens: Int? = null,
        callTimeoutSeconds: Long? = null,
        fallbackLabel: String = "AI 请求失败",
    ): String {
        val resolved = resolveAiConfig(config)
        return chatOnceInternal(resolved, messages, temperature, maxTokens, callTimeoutSeconds, fallbackLabel)
    }

    private suspend fun chatOnceInternal(
        resolved: ResolvedAiConfig,
        messages: List<ChatMessage>,
        temperature: Double,
        maxTokens: Int?,
        callTimeoutSeconds: Long?,
        fallbackLabel: String,
    ): String = withContext(Dispatchers.IO) {
        val body = buildBody(resolved.model, messages, temperature, maxTokens, stream = false)
        val response = requestCompletion(
            resolved = resolved,
            body = body,
            stream = false,
            callTimeoutSeconds = callTimeoutSeconds,
            fallbackLabel = fallbackLabel,
        )
        response.use { resp ->
            parseCompletionText(resp.body?.string().orEmpty())
        }
    }

    suspend fun listModels(config: AiConfig): List<String> = withContext(Dispatchers.IO) {
        val resolved = resolveAiConfig(config, requireModel = false)
        val request = Request.Builder()
            .url(modelsUrl(resolved.baseUrl))
            .get()
            .bearer(resolved.apiKey)
            .build()

        val client = clientFor(60)
        var lastError: Throwable? = null
        for (attempt in 0..MAX_RETRIES) {
            try {
                client.executeAsync(request).use { resp ->
                    if (resp.isSuccessful) {
                        return@withContext parseModels(resp.body?.string().orEmpty())
                    }
                    val status = resp.code
                    if (status !in RETRYABLE_STATUS || attempt >= MAX_RETRIES) {
                        throw AiHttpException(status, httpErrorMessage(status, "获取模型列表失败"))
                    }
                }
            } catch (error: AiHttpException) {
                throw error
            } catch (error: java.io.IOException) {
                lastError = error
                if (attempt >= MAX_RETRIES) throw error
            }
            if (attempt < MAX_RETRIES) delay(RETRY_DELAY_MS * (attempt + 1))
        }
        throw lastError ?: AiServiceException("获取模型列表失败")
    }

    suspend fun testConnection(config: AiConfig): Pair<String, String> {
        val resolved = resolveAiConfig(config)
        val reply = chatOnceInternal(
            resolved = resolved,
            messages = Prompts.aiTestMessages(),
            temperature = 0.0,
            maxTokens = 32,
            callTimeoutSeconds = 60,
            fallbackLabel = "AI 连接测试失败",
        )
        return reply.trim() to resolved.model
    }

    private suspend fun requestCompletion(
        resolved: ResolvedAiConfig,
        body: JSONObject,
        stream: Boolean,
        callTimeoutSeconds: Long?,
        fallbackLabel: String,
    ): Response {
        val client = clientFor(callTimeoutSeconds)
        var attempt = 0
        var retriedWithoutMaxTokens = false
        while (true) {
            val request = Request.Builder()
                .url(completionsUrl(resolved.baseUrl))
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .bearer(resolved.apiKey)
                .apply {
                    if (stream) header("Accept", "text/event-stream")
                }
                .build()
            try {
                val response = client.executeAsync(request)
                if (response.isSuccessful) return response

                val status = response.code
                val detail = try {
                    response.body?.string().orEmpty()
                } catch (_: Exception) {
                    ""
                }
                response.close()

                if (status == 400 && body.has("max_tokens") && !retriedWithoutMaxTokens &&
                    detail.lowercase().contains("max_tokens")
                ) {
                    body.remove("max_tokens")
                    retriedWithoutMaxTokens = true
                    continue
                }
                if (status in RETRYABLE_STATUS && attempt < MAX_RETRIES) {
                    attempt++
                    delay(RETRY_DELAY_MS * attempt)
                    continue
                }
                throw AiHttpException(status, httpErrorMessage(status, fallbackLabel))
            } catch (error: AiHttpException) {
                throw error
            } catch (error: java.io.IOException) {
                if (attempt < MAX_RETRIES) {
                    attempt++
                    delay(RETRY_DELAY_MS * attempt)
                } else {
                    throw error
                }
            }
        }
    }

    private fun buildBody(
        model: String,
        messages: List<ChatMessage>,
        temperature: Double,
        maxTokens: Int?,
        stream: Boolean,
    ): JSONObject = JSONObject().apply {
        put("model", model)
        put("messages", JSONArray().apply {
            messages.forEach { message ->
                put(JSONObject().apply {
                    put("role", message.role)
                    put("content", message.content)
                })
            }
        })
        put("temperature", temperature)
        put("stream", stream)
        if (maxTokens != null && maxTokens > 0) put("max_tokens", maxTokens)
    }

    private fun parseCompletionText(raw: String): String {
        if (raw.isBlank()) return ""
        return try {
            val payload = JSONObject(raw)
            val choice = payload.optJSONArray("choices")?.optJSONObject(0) ?: return ""
            val message = choice.optJSONObject("message")
            val content = message?.opt("content")
            when (content) {
                is String -> content
                else -> choice.optString("text", "")
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun parseModels(raw: String): List<String> {
        val payload = try {
            JSONObject(raw)
        } catch (_: Exception) {
            throw AiServiceException("AI 服务返回的模型列表格式无效")
        }
        val array = payload.optJSONArray("data") ?: payload.optJSONArray("models") ?: return emptyList()
        val models = mutableListOf<String>()
        for (index in 0 until array.length()) {
            when (val item = array.opt(index)) {
                is String -> models.add(item)
                is JSONObject -> (item.opt("id") as? String)?.let { models.add(it) }
            }
        }
        return models
    }

    companion object {
        private const val MAX_RETRIES = 2
        private const val RETRY_DELAY_MS = 500L
        private val RETRYABLE_STATUS = setOf(408, 409, 429, 500, 502, 503, 504)
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

/**
 * 从一行 SSE 里取出增量文本：`data: {...}`。
 * 空行、注释行、[DONE] 与不含内容的块返回 null；错误块抛出可读异常。
 */
internal fun extractSseText(line: String): String? {
    if (!line.startsWith("data:")) return null
    val raw = line.removePrefix("data:").trim()
    if (raw.isEmpty() || raw == "[DONE]") return null
    val payload = try {
        JSONObject(raw)
    } catch (_: Exception) {
        return null
    }
    val error = payload.opt("error")
    if (error != null) {
        val message = when (error) {
            is String -> error
            is JSONObject -> (error.opt("message") as? String).orEmpty()
            else -> ""
        }
        throw AiServiceException(message.ifBlank { "AI 服务返回错误" })
    }
    val choice = payload.optJSONArray("choices")?.optJSONObject(0) ?: return null
    val delta = choice.optJSONObject("delta")
    val content = delta?.opt("content")
    if (content is String && content.isNotEmpty()) return content
    val text = choice.opt("text")
    return if (text is String && text.isNotEmpty()) text else null
}
