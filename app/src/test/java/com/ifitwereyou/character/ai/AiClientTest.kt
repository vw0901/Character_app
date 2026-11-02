package com.ifitwereyou.character.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class AiClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: AiClient
    private lateinit var config: AiConfig

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = AiClient()
        config = AiConfig(
            baseUrl = server.url("/").toString(),
            apiKey = "test-key",
            model = "mock-chat",
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun streamChunk(content: String): String {
        val escaped = content.replace("\\", "\\\\").replace("\"", "\\\"")
        return "data: {\"choices\":[{\"delta\":{\"content\":\"$escaped\"}}]}\n\n"
    }

    private fun bodyOf(requestIndex: Int): JSONObject =
        JSONObject(server.takeRequest().let { it.body.readUtf8() })

    @Test
    fun streamsOpenAiCompatibleDeltas() = runTest {
        val body = buildString {
            append(streamChunk("连接"))
            append(streamChunk("成功"))
            append("data: [DONE]\n\n")
        }
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body),
        )

        val chunks = client.streamChat(
            config,
            listOf(ChatMessage("user", "hello")),
        ).toList()

        assertEquals("连接成功", chunks.joinToString("") { it.content })
        val request = server.takeRequest()
        assertEquals("/v1/chat/completions", request.path)
        assertEquals("Bearer test-key", request.getHeader("Authorization"))
    }

    @Test
    fun fallsBackToNonStreamWhenStreamIsRejected() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("""{"error":{"message":"stream not supported"}}"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"choices":[{"message":{"role":"assistant","content":"非流式回复"}}]}""",
                ),
        )

        val chunks = client.streamChat(
            config,
            listOf(ChatMessage("user", "hello")),
        ).toList()

        assertEquals("非流式回复", chunks.joinToString("") { it.content })
        assertEquals(2, server.requestCount)
    }

    @Test
    fun authErrorsAreNotRetriedOrDowngraded() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":{"message":"invalid key"}}"""),
        )

        var failure: Throwable? = null
        try {
            client.streamChat(config, listOf(ChatMessage("user", "hello"))).toList()
        } catch (error: Throwable) {
            failure = error
        }

        assertTrue(failure is AiHttpException)
        val httpError = failure as AiHttpException
        assertEquals(401, httpError.status)
        val message = getAiErrorMessage(httpError, "失败")
        assertTrue(message.startsWith("AI 服务鉴权失败"))
        assertTrue(message.contains("invalid key"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun chatOnceReadsMessageContent() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[{"message":{"content":"一次性回复"}}]}"""),
        )

        val reply = client.chatOnce(config, listOf(ChatMessage("user", "hello")))
        assertEquals("一次性回复", reply)
    }

    @Test
    fun listsModelsFromDataArray() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"data":[{"id":"mock-chat"},{"id":"mock-reasoner"}]}"""),
        )

        val models = client.listModels(config.copy(model = ""))
        assertEquals(listOf("mock-chat", "mock-reasoner"), models)
        assertEquals("/v1/models", server.takeRequest().path)
    }

    @Test
    fun parsesSseLinesAndIgnoresDone() {
        assertEquals(
            "你好",
            extractSseText("""data: {"choices":[{"delta":{"content":"你好"}}]}"""),
        )
        assertEquals(
            "你好",
            extractSseText("""data: {"choices":[{"text":"你好"}]}"""),
        )
        assertNull(extractSseText("data: [DONE]"))
        assertNull(extractSseText(": ping"))
        assertNull(extractSseText("""data: {"choices":[{"delta":{}}]}"""))
    }

    @Test
    fun mapsServiceErrorPayloadToReadableMessage() {
        var failure: Throwable? = null
        try {
            extractSseText("""data: {"error":{"message":"quota exceeded"}}""")
        } catch (error: Throwable) {
            failure = error
        }
        assertTrue(failure is AiServiceException)
        assertEquals("quota exceeded", failure?.message)
    }

    @Test
    fun reasoningModelDegradesMaxTokensAndTemperature() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody(
                    """{"error":{"message":"Unsupported parameter: 'max_tokens' is not supported with this model. Use 'max_completion_tokens' instead."}}""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody(
                    """{"error":{"message":"Unsupported value: 'temperature' does not support 0.7 with this model."}}""",
                ),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(streamChunk("推理模型正常")),
        )

        val chunks = client.streamChat(
            config.copy(model = "o3-mini"),
            listOf(ChatMessage("user", "hello")),
            temperature = 0.7,
            maxTokens = 1024,
        ).toList()

        assertEquals("推理模型正常", chunks.joinToString("") { it.content })

        val first = bodyOf(0)
        assertEquals(1024, first.getInt("max_tokens"))
        assertEquals(0.7, first.getDouble("temperature"), 0.0001)

        val second = bodyOf(1)
        assertFalse(second.has("max_tokens"))
        assertEquals(1024, second.getInt("max_completion_tokens"))
        assertEquals(0.7, second.getDouble("temperature"), 0.0001)

        val third = bodyOf(2)
        assertFalse(third.has("temperature"))
        assertEquals(1024, third.getInt("max_completion_tokens"))
    }

    @Test
    fun streamsReasoningContentWithoutLeakingIntoNovelText() = runTest {
        val body = buildString {
            append("data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"先想\"}}]}\n\n")
            append("data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"一下\"}}]}\n\n")
            append(streamChunk("正文"))
            append("data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n")
            append("data: [DONE]\n\n")
        }
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body),
        )

        val chunks = client.streamChat(
            config.copy(model = "deepseek-reasoner"),
            listOf(ChatMessage("user", "hello")),
        ).toList()

        assertEquals("正文", chunks.joinToString("") { it.content })
        assertEquals("先想一下", chunks.joinToString("") { it.reasoning })
        assertEquals("stop", chunks.last().finishReason)
    }

    @Test
    fun readsContentArrayAndReasoningInNonStreamReply() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {"choices":[{"message":{
                      "content":[{"type":"text","text":"第一段"},{"type":"text","text":"第二段"}],
                      "reasoning_content":"内部推理"
                    },"finish_reason":"stop"}]}
                    """.trimIndent(),
                ),
        )

        val result = client.chatOnceResult(config, listOf(ChatMessage("user", "hello")))
        assertEquals("第一段第二段", result.content)
        assertEquals("内部推理", result.reasoning)
        assertEquals("stop", result.finishReason)
    }

    @Test
    fun truncatedOutputRaisesClearErrorWhenRequested() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    streamChunk("半截正文") +
                        "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"length\"}]}\n\n",
                ),
        )

        var failure: Throwable? = null
        try {
            client.streamChat(
                config,
                listOf(ChatMessage("user", "hello")),
                failOnTruncation = true,
            ).toList()
        } catch (error: Throwable) {
            failure = error
        }
        assertTrue(failure is AiTruncatedException)
        assertTrue(failure?.message.orEmpty().contains("截断"))
    }

    @Test
    fun truncationIsToleratedWhenCallerAllowsIt() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    streamChunk("可用正文") +
                        "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"length\"}]}\n\n",
                ),
        )

        val chunks = client.streamChat(
            config,
            listOf(ChatMessage("user", "hello")),
            failOnTruncation = false,
        ).toList()
        assertEquals("可用正文", chunks.joinToString("") { it.content })
    }

    @Test
    fun surfacesProviderErrorDetailForBadRequest() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody(
                    """{"error":{"message":"This model's maximum context length is 8192 tokens.","type":"invalid_request_error"}}""",
                ),
        )

        var failure: Throwable? = null
        try {
            client.chatOnce(config, listOf(ChatMessage("user", "hello")))
        } catch (error: Throwable) {
            failure = error
        }

        assertTrue(failure is AiHttpException)
        val message = getAiErrorMessage(failure!!, "请求失败")
        assertTrue(message.contains("8192"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun retriesRateLimitWithRetryAfterHeader() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "0")
                .setBody("""{"error":{"message":"rate limited"}}"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[{"message":{"content":"重试成功"}}]}"""),
        )

        val reply = client.chatOnce(config, listOf(ChatMessage("user", "hello")))
        assertEquals("重试成功", reply)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun sseErrorBlockDoesNotTriggerAnotherRequest() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: {\"error\":{\"message\":\"quota exceeded\"}}\n\n"),
        )

        var failure: Throwable? = null
        try {
            client.streamChat(config, listOf(ChatMessage("user", "hello"))).toList()
        } catch (error: Throwable) {
            failure = error
        }

        assertTrue(failure is AiServiceException)
        assertTrue(failure?.message.orEmpty().contains("quota exceeded"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun userParametersAreMergedWithSceneDefaults() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(streamChunk("ok")),
        )

        val custom = config.copy(
            temperature = 0.25,
            topP = 0.8,
            maxTokens = 100,
            frequencyPenalty = 0.1,
            presencePenalty = 0.2,
            seed = 42,
            extraParams = """{"reasoning_effort":"low","response_format":{"type":"json_object"}}""",
        )

        client.streamChat(
            custom,
            listOf(ChatMessage("user", "hello")),
            temperature = 0.9,
            maxTokens = 500,
        ).toList()

        val body = bodyOf(0)
        assertEquals(0.25, body.getDouble("temperature"), 0.0001)
        assertEquals(0.8, body.getDouble("top_p"), 0.0001)
        assertEquals(100, body.getInt("max_tokens"))
        assertEquals(0.1, body.getDouble("frequency_penalty"), 0.0001)
        assertEquals(0.2, body.getDouble("presence_penalty"), 0.0001)
        assertEquals(42, body.getInt("seed"))
        assertEquals("low", body.getString("reasoning_effort"))
        assertEquals("json_object", body.getJSONObject("response_format").getString("type"))
        assertTrue(body.getBoolean("stream"))
    }

    @Test
    fun testConnectionExplainsReasoningOnlyReply() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"choices":[{"message":{"content":"","reasoning_content":"想了很久"},"finish_reason":"length"}]}""",
                ),
        )

        val (reply, model) = client.testConnection(config)
        assertEquals("mock-chat", model)
        assertTrue(reply.contains("推理过程"))
        assertTrue(reply.contains("想了很久"))
    }

    @Test
    fun parsesSseWrappedNonStreamBody() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(streamChunk("包在 SSE 里") + streamChunk("的回复")),
        )

        assertEquals(
            "包在 SSE 里的回复",
            client.chatOnce(config, listOf(ChatMessage("user", "hello"))),
        )
    }

    @Test
    fun cancellationClosesBlockingStreamWithoutFallback() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(streamChunk("迟到的内容"))
                .setBodyDelay(2, TimeUnit.SECONDS),
        )

        val job = launch(Dispatchers.IO) {
            client.streamChat(config, listOf(ChatMessage("user", "hello"))).collect { }
        }
        delay(300)
        withTimeout(2_000) { job.cancelAndJoin() }

        assertTrue(job.isCancelled)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun effectiveMaxTokensUsesUserOverride() {
        assertEquals(100, AiClient.effectiveMaxTokens(100, 6000))
        assertEquals(4096, AiClient.effectiveMaxTokens(4096, 1800))
        assertEquals(6000, AiClient.effectiveMaxTokens(null, 6000))
        assertEquals(100, AiClient.effectiveMaxTokens(100, null))
        assertNull(AiClient.effectiveMaxTokens(null, null))
    }
}
