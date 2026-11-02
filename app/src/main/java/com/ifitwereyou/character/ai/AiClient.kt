package com.ifitwereyou.character.ai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** 一段流式输出。reasoning 只在推理模型返回时存在，不会进入剧情正文。 */
data class ChatChunk(
    val content: String = "",
    val reasoning: String = "",
    val finishReason: String? = null,
)

data class ChatResult(
    val content: String,
    val reasoning: String = "",
    val finishReason: String? = null,
)

/**
 * OpenAI 兼容接口客户端。
 *
 * 行为：
 * - 流式失败且尚未收到任何内容时，退回一次非流式请求；
 * - 401/403/429 不降级；5xx/408/409 最多重试 2 次（429 尊重 Retry-After）；
 * - 400 时按服务商错误信息逐项降级参数（temperature / max_tokens / 自定义参数等），
 *   兼容 o1/o3、GPT-5、DeepSeek reasoner 等对参数敏感或自带推理过程的模型；
 * - finish_reason=length 时抛出明确的截断异常，避免把残缺 JSON 当成解析失败。
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
        temperature: Double? = 0.7,
        maxTokens: Int? = null,
        callTimeoutSeconds: Long? = null,
        fallbackLabel: String = "AI 请求失败",
        failOnTruncation: Boolean = false,
    ): Flow<ChatChunk> = flow {
        val resolved = resolveAiConfig(config)
        val body = buildBody(resolved.model, resolved, messages, temperature, maxTokens, stream = true)
        var emittedContent = false
        var emittedReasoning = false
        var finishReason: String? = null
        try {
            val result = requestCompletion(
                resolved = resolved,
                body = body,
                stream = true,
                callTimeoutSeconds = callTimeoutSeconds,
                fallbackLabel = fallbackLabel,
            )
            // 响应返回后 readLine() 是阻塞的；用独立的取消观察者直接取消 Call，
            // 避免 invokeOnCompletion 要等阻塞读结束才触发。
            val watcher = CoroutineScope(currentCoroutineContext()).launch(
                start = CoroutineStart.UNDISPATCHED,
            ) {
                try {
                    awaitCancellation()
                } finally {
                    result.cancel()
                }
            }
            try {
                result.response.use { resp ->
                    val contentType = resp.header("Content-Type").orEmpty()
                    val bodyStream = resp.body ?: throw AiServiceException("AI 服务未返回响应内容")

                    if (contentType.contains("event-stream", ignoreCase = true) ||
                        contentType.contains("stream", ignoreCase = true)
                    ) {
                        val reader = BufferedReader(
                            InputStreamReader(bodyStream.byteStream(), StandardCharsets.UTF_8),
                        )
                        reader.use { input ->
                            while (currentCoroutineContext().isActive) {
                                val line = input.readLine() ?: break
                                val chunk = parseSseLine(line) ?: continue
                                if (chunk.content.isNotEmpty()) emittedContent = true
                                if (chunk.reasoning.isNotEmpty()) emittedReasoning = true
                                if (chunk.finishReason != null) finishReason = chunk.finishReason
                                if (chunk.content.isNotEmpty() ||
                                    chunk.reasoning.isNotEmpty() ||
                                    chunk.finishReason != null
                                ) {
                                    emit(chunk)
                                }
                            }
                        }
                    } else {
                        val raw = bodyStream.string()
                        val result = parseCompletion(raw)
                        if (result.content.isNotEmpty()) emittedContent = true
                        if (result.reasoning.isNotEmpty()) emittedReasoning = true
                        finishReason = result.finishReason
                        if (result.content.isNotEmpty() ||
                            result.reasoning.isNotEmpty() ||
                            result.finishReason != null
                        ) {
                            emit(ChatChunk(result.content, result.reasoning, result.finishReason))
                        }
                    }
                }
            } finally {
                watcher.cancel()
            }

            currentCoroutineContext().ensureActive()
            if (finishReason == "length") {
                if (failOnTruncation || !emittedContent) throw AiTruncatedException(truncationMessage())
            }
            if (!emittedContent && finishReason == "content_filter") {
                throw AiServiceException("AI 输出被服务商内容审核拦截，请调整提示词后重试")
            }
            if (!emittedContent && emittedReasoning) {
                throw AiServiceException(
                    reasoningOnlyMessage(ChatResult("", finishReason = finishReason)),
                )
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            currentCoroutineContext().ensureActive()
            if (error is AiTruncatedException) throw error
            if (emittedContent || error is AiConfigurationException) throw error
            if (error is AiHttpException && error.status in setOf(401, 403, 429)) throw error
            // 服务商已经明确返回错误块（或已收到推理内容但没给出正文）时不再盲目重发。
            if (error is AiServiceException) throw error
            if (!shouldFallback(error)) throw error

            // 部分兼容接口不支持流式：退回一次性请求，别让整轮冒险失败。
            val result = chatOnceResultInternal(
                resolved = resolved,
                messages = messages,
                temperature = temperature,
                maxTokens = maxTokens,
                callTimeoutSeconds = callTimeoutSeconds,
                fallbackLabel = fallbackLabel,
            )
            when {
                result.content.isNotEmpty() -> {
                    emit(ChatChunk(result.content, result.reasoning, result.finishReason))
                    if (failOnTruncation && result.finishReason == "length") {
                        throw AiTruncatedException(truncationMessage())
                    }
                }
                result.reasoning.isNotBlank() -> throw AiServiceException(reasoningOnlyMessage(result))
                else -> throw error
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun chatOnce(
        config: AiConfig,
        messages: List<ChatMessage>,
        temperature: Double? = 0.3,
        maxTokens: Int? = null,
        callTimeoutSeconds: Long? = null,
        fallbackLabel: String = "AI 请求失败",
        failOnTruncation: Boolean = false,
    ): String = chatOnceResult(
        config = config,
        messages = messages,
        temperature = temperature,
        maxTokens = maxTokens,
        callTimeoutSeconds = callTimeoutSeconds,
        fallbackLabel = fallbackLabel,
        failOnTruncation = failOnTruncation,
    ).content

    suspend fun chatOnceResult(
        config: AiConfig,
        messages: List<ChatMessage>,
        temperature: Double? = 0.3,
        maxTokens: Int? = null,
        callTimeoutSeconds: Long? = null,
        fallbackLabel: String = "AI 请求失败",
        failOnTruncation: Boolean = false,
    ): ChatResult {
        val resolved = resolveAiConfig(config)
        val result = chatOnceResultInternal(
            resolved = resolved,
            messages = messages,
            temperature = temperature,
            maxTokens = maxTokens,
            callTimeoutSeconds = callTimeoutSeconds,
            fallbackLabel = fallbackLabel,
        )
        if (failOnTruncation && result.finishReason == "length") {
            throw AiTruncatedException(truncationMessage())
        }
        if (result.content.isEmpty() && result.reasoning.isNotBlank()) {
            throw AiServiceException(reasoningOnlyMessage(result))
        }
        return result
    }

    private suspend fun chatOnceResultInternal(
        resolved: ResolvedAiConfig,
        messages: List<ChatMessage>,
        temperature: Double?,
        maxTokens: Int?,
        callTimeoutSeconds: Long?,
        fallbackLabel: String,
    ): ChatResult = withContext(Dispatchers.IO) {
        val body = buildBody(resolved.model, resolved, messages, temperature, maxTokens, stream = false)
        val result = requestCompletion(
            resolved = resolved,
            body = body,
            stream = false,
            callTimeoutSeconds = callTimeoutSeconds,
            fallbackLabel = fallbackLabel,
        )
        result.use { parseCompletion(it.response.body?.string().orEmpty()) }
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
                    val detail = extractApiErrorDetail(resp.body?.string().orEmpty())
                    val message = httpErrorMessage(status, "获取模型列表失败", detail)
                    if (status !in RETRYABLE_STATUS || attempt >= MAX_RETRIES) {
                        throw AiHttpException(status, message, detail)
                    }
                    if (attempt < MAX_RETRIES) {
                        delay(retryDelayMillis(resp.header("Retry-After"), attempt))
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
        // 连接测试不继承全局输出上限，避免用户把上限设成 32 时推理模型连测试都答不出来。
        val result = chatOnceResultInternal(
            resolved = resolved.copy(maxTokens = null),
            messages = Prompts.aiTestMessages(),
            temperature = 0.0,
            maxTokens = 256,
            callTimeoutSeconds = 60,
            fallbackLabel = "AI 连接测试失败",
        )
        val reply = result.content.trim().ifBlank {
            val reasoning = result.reasoning.trim()
            if (reasoning.isNotEmpty()) {
                "（模型只返回了推理过程，没有给出最终回答，可调高最大输出 Token）" +
                    reasoning.take(120)
            } else if (result.finishReason == "length") {
                "（模型输出达到长度上限，没有给出最终回答，请调高最大输出 Token）"
            } else {
                ""
            }
        }
        return reply to resolved.model
    }

    private suspend fun requestCompletion(
        resolved: ResolvedAiConfig,
        body: JSONObject,
        stream: Boolean,
        callTimeoutSeconds: Long?,
        fallbackLabel: String,
    ): AiResponse {
        val client = clientFor(callTimeoutSeconds)
        var attempt = 0
        var paramAttempt = 0
        var droppedAllOptional = false
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
                val result = client.executeAsyncCall(request)
                val response = result.response
                if (response.isSuccessful) return result

                val status = response.code
                val retryAfter = response.header("Retry-After")
                val detail = try {
                    extractApiErrorDetail(response.body?.string().orEmpty())
                } catch (_: Exception) {
                    ""
                }
                result.close()

                if (status == 400 && paramAttempt < MAX_PARAM_RETRIES) {
                    val action = dropRejectedParameter(body, detail, droppedAllOptional)
                    if (action != null) {
                        paramAttempt++
                        droppedAllOptional = droppedAllOptional || action.second
                        continue
                    }
                }
                if (status in RETRYABLE_STATUS && attempt < MAX_RETRIES) {
                    attempt++
                    delay(retryDelayMillis(retryAfter, attempt))
                    continue
                }
                throw AiHttpException(
                    status = status,
                    message = httpErrorMessage(status, fallbackLabel, detail),
                    detail = detail,
                )
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

    /**
     * 400 时按错误信息丢弃服务商不接受的参数。
     *
     * 返回 null 表示没有可再降级的参数；second=true 表示这次做的是「清空所有可选参数」的兜底。
     */
    private fun dropRejectedParameter(
        body: JSONObject,
        detail: String,
        alreadyDroppedAll: Boolean,
    ): Pair<String, Boolean>? {
        val lower = detail.lowercase()

        // 流式本身不被支持：交给外层降级为非流式，删参数没有意义。
        if (lower.contains("stream") && !lower.contains("max_tokens")) return null
        // 上下文超限：删参数不能解决，直接透出服务商原因。
        if (lower.contains("context_length") ||
            lower.contains("context length") ||
            lower.contains("maximum context") ||
            lower.contains("too long")
        ) {
            return null
        }

        // OpenAI 推理模型：max_tokens 改名 max_completion_tokens。
        if (lower.contains("max_completion_tokens")) {
            if (body.has("max_completion_tokens")) {
                body.remove("max_completion_tokens")
                return "max_completion_tokens" to false
            }
            val value = body.opt("max_tokens")
            if (value != null) {
                body.remove("max_tokens")
                body.put("max_completion_tokens", value)
                return "max_tokens->max_completion_tokens" to false
            }
        }

        val named = listOf(
            "temperature",
            "top_p",
            "frequency_penalty",
            "presence_penalty",
            "seed",
            "response_format",
            "reasoning_effort",
        )
        for (key in named) {
            if (body.has(key) && lower.contains(key)) {
                body.remove(key)
                return key to false
            }
        }

        if (body.has("max_tokens") && lower.contains("max_tokens")) {
            body.remove("max_tokens")
            return "max_tokens" to false
        }

        if (alreadyDroppedAll) return null
        // 未知 400：清空 model/messages/stream 之外的所有参数再试一次。
        val removable = buildList {
            val keys = body.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key !in CORE_FIELDS) add(key)
            }
        }
        if (removable.isEmpty()) return null
        removable.forEach(body::remove)
        return "optional-params" to true
    }

    private fun buildBody(
        model: String,
        resolved: ResolvedAiConfig,
        messages: List<ChatMessage>,
        sceneTemperature: Double?,
        sceneMaxTokens: Int?,
        stream: Boolean,
    ): JSONObject = JSONObject().apply {
        // 先合并用户自定义参数，再写入核心字段；核心字段优先，避免误覆盖。
        val extraKeys = resolved.extraParams.keys()
        while (extraKeys.hasNext()) {
            val key = extraKeys.next()
            if (key !in CORE_FIELDS) put(key, resolved.extraParams.get(key))
        }

        put("model", model)
        put("messages", JSONArray().apply {
            messages.forEach { message ->
                put(JSONObject().apply {
                    put("role", message.role)
                    put("content", message.content)
                })
            }
        })

        val temperature = resolved.temperature ?: sceneTemperature
        if (temperature != null) put("temperature", temperature)
        resolved.topP?.let { put("top_p", it) }
        resolved.frequencyPenalty?.let { put("frequency_penalty", it) }
        resolved.presencePenalty?.let { put("presence_penalty", it) }
        resolved.seed?.let { put("seed", it) }

        val maxTokens = effectiveMaxTokens(resolved.maxTokens, sceneMaxTokens)
        if (maxTokens != null && maxTokens > 0) put("max_tokens", maxTokens)
        put("stream", stream)
    }

    private fun parseCompletion(raw: String): ChatResult {
        if (raw.isBlank()) return ChatResult("")

        // 某些兼容服务在非流式请求里仍返回 SSE 包装。
        if (raw.trimStart().startsWith("data:")) {
            val content = StringBuilder()
            val reasoning = StringBuilder()
            var finishReason: String? = null
            raw.lineSequence().forEach { line ->
                val chunk = parseSseLine(line) ?: return@forEach
                content.append(chunk.content)
                reasoning.append(chunk.reasoning)
                if (chunk.finishReason != null) finishReason = chunk.finishReason
            }
            return ChatResult(content.toString(), reasoning.toString(), finishReason)
        }

        return try {
            val payload = JSONObject(raw)
            val error = payload.opt("error")
            if (error != null) {
                throw AiServiceException(extractErrorMessage(error).ifBlank { "AI 服务返回错误" })
            }
            val choice = payload.optJSONArray("choices")?.optJSONObject(0) ?: return ChatResult("")
            val message = choice.optJSONObject("message")
            val content = extractText(message?.opt("content"))
                .ifEmpty { extractText(choice.opt("text")) }
            val reasoning = extractText(message?.opt("reasoning_content"))
                .ifEmpty { extractText(message?.opt("reasoning")) }
            ChatResult(
                content = content,
                reasoning = reasoning,
                finishReason = choice.optString("finish_reason").takeIf { it.isNotBlank() },
            )
        } catch (error: AiServiceException) {
            throw error
        } catch (_: Exception) {
            ChatResult("")
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
        private const val MAX_PARAM_RETRIES = 4
        private const val RETRY_DELAY_MS = 500L
        private const val MAX_RETRY_AFTER_MS = 10_000L
        private val RETRYABLE_STATUS = setOf(408, 409, 429, 500, 502, 503, 504)
        private val CORE_FIELDS = setOf("model", "messages", "stream")
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        internal fun effectiveMaxTokens(global: Int?, scene: Int?): Int? = global ?: scene

        private fun truncationMessage(): String =
            "AI 输出达到长度上限被截断。请调高「最大输出 Token」或换用上下文更大的模型后重试"

        private fun reasoningOnlyMessage(result: ChatResult): String =
            if (result.finishReason == "length") {
                "推理模型把 Token 预算用在了推理过程上，没有产出最终回答。请调高「最大输出 Token」后重试"
            } else {
                "模型只返回了推理过程，没有给出最终回答。请换用非推理模型，或调高最大输出 Token 后重试"
            }

        private fun shouldFallback(error: Throwable): Boolean = when (error) {
            is AiHttpException -> error.status in setOf(400, 404, 405, 415, 422, 501)
            is java.io.IOException -> true
            else -> false
        }

        private fun retryDelayMillis(retryAfter: String?, attempt: Int): Long {
            val base = RETRY_DELAY_MS * attempt
            if (retryAfter.isNullOrBlank()) return base
            val seconds = retryAfter.trim().toLongOrNull()
            val fromHeader = if (seconds != null) {
                seconds * 1000
            } else {
                try {
                    val at = ZonedDateTime.parse(retryAfter.trim(), DateTimeFormatter.RFC_1123_DATE_TIME)
                    at.toInstant().toEpochMilli() - System.currentTimeMillis()
                } catch (_: Exception) {
                    return base
                }
            }
            return fromHeader.coerceIn(0L, MAX_RETRY_AFTER_MS)
        }
    }
}

/**
 * 从一行 SSE 里取出增量内容：`data: {...}`。
 * 空行、注释行、[DONE] 与不含内容的块返回 null；错误块抛出可读异常。
 */
internal fun parseSseLine(line: String): ChatChunk? {
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
        throw AiServiceException(extractErrorMessage(error).ifBlank { "AI 服务返回错误" })
    }
    val choice = payload.optJSONArray("choices")?.optJSONObject(0) ?: return null
    val delta = choice.optJSONObject("delta")
    val content = extractText(delta?.opt("content"))
        .ifEmpty { extractText(choice.opt("text")) }
    val reasoning = extractText(delta?.opt("reasoning_content"))
        .ifEmpty { extractText(delta?.opt("reasoning")) }
    val finishReason = choice.optString("finish_reason").takeIf { it.isNotBlank() }
    if (content.isEmpty() && reasoning.isEmpty() && finishReason == null) return null
    return ChatChunk(content, reasoning, finishReason)
}

/** 兼容旧调用：只取正文增量。 */
internal fun extractSseText(line: String): String? =
    parseSseLine(line)?.content?.takeIf { it.isNotEmpty() }

/** content 可能是字符串、数组（多段 text）或对象；统一抽取为纯文本。 */
internal fun extractText(value: Any?): String = when (value) {
    null, JSONObject.NULL -> ""
    is String -> value
    is JSONArray -> buildString {
        for (index in 0 until value.length()) {
            when (val item = value.opt(index)) {
                is String -> append(item)
                is JSONObject -> append(extractText(item.opt("text")))
            }
        }
    }
    is JSONObject -> extractText(value.opt("text"))
    else -> ""
}

/** 从错误块 / 完整响应体里抽取服务商错误信息。 */
internal fun extractErrorMessage(error: Any?): String = when (error) {
    null, JSONObject.NULL -> ""
    is String -> error
    is JSONObject -> {
        val message = extractText(error.opt("message"))
        if (message.isNotBlank()) {
            message
        } else {
            val code = extractText(error.opt("code"))
            val type = extractText(error.opt("type"))
            listOf(code, type).filter { it.isNotBlank() }.joinToString(" · ")
        }
    }
    else -> ""
}

/**
 * 从 HTTP 错误响应体里抽出可展示的 message。
 * 只读取响应体，不会拼接请求头或 API Key；最多保留 300 字。
 */
internal fun extractApiErrorDetail(raw: String): String {
    val text = raw.trim()
    if (text.isEmpty()) return ""
    val candidate = try {
        val root = JSONObject(text)
        val fromError = extractErrorMessage(root.opt("error"))
        if (fromError.isNotBlank()) {
            fromError
        } else {
            extractText(root.opt("message"))
                .ifEmpty { extractText(root.opt("detail")) }
                .ifEmpty { extractText(root.opt("msg")) }
        }
    } catch (_: Exception) {
        // HTML / 纯文本错误页：去掉标签再截断。
        text.replace(Regex("<[^>]+>"), " ")
    }
    return candidate.replace(Regex("\\s+"), " ").trim().take(300)
}
