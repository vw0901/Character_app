package com.ifitwereyou.character.ai

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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

        assertEquals(listOf("连接", "成功"), chunks)
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

        assertEquals(listOf("非流式回复"), chunks)
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
        assertEquals("AI 服务鉴权失败，请检查 API Key", getAiErrorMessage(httpError, "失败"))
        assertEquals(1, server.requestCount)    }

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
}
