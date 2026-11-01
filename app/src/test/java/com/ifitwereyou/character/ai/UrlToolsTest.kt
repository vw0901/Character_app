package com.ifitwereyou.character.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UrlToolsTest {

    @Test
    fun addsDefaultVersionPath() {
        assertEquals("http://127.0.0.1:11434/v1", normalizeBaseUrl("http://127.0.0.1:11434"))
        assertEquals("https://api.example.com/v1", normalizeBaseUrl("https://api.example.com/"))
    }

    @Test
    fun keepsExistingVersionSegment() {
        assertEquals("https://api.deepseek.com/v1", normalizeBaseUrl("https://api.deepseek.com/v1/"))
        assertEquals(
            "https://ark.cn-beijing.volces.com/api/v3",
            normalizeBaseUrl("https://ark.cn-beijing.volces.com/api/v3"),
        )
    }

    @Test
    fun appendsVersionToNonVersionPath() {
        assertEquals("http://localhost:8000/api/v1", normalizeBaseUrl("http://localhost:8000/api"))
    }

    @Test
    fun stripsEndpointSuffixes() {
        assertEquals(
            "https://api.example.com/v1",
            normalizeBaseUrl("https://api.example.com/v1/chat/completions"),
        )
        assertEquals(
            "https://api.example.com/v1",
            normalizeBaseUrl("https://api.example.com/v1/models"),
        )
    }

    @Test
    fun rejectsInvalidUrls() {
        assertThrows(AiConfigurationException::class.java) { normalizeBaseUrl("") }
        assertThrows(AiConfigurationException::class.java) { normalizeBaseUrl("ftp://example.com") }
        assertThrows(AiConfigurationException::class.java) { normalizeBaseUrl("https://example.com/v1?x=1") }
        assertThrows(AiConfigurationException::class.java) { normalizeBaseUrl("https://example.com/v1#frag") }
    }

    @Test
    fun configRequiresBaseAndModel() {
        assertThrows(AiConfigurationException::class.java) {
            resolveAiConfig(AiConfig(baseUrl = "http://localhost:11434", model = ""))
        }
        assertThrows(AiConfigurationException::class.java) {
            resolveAiConfig(AiConfig(baseUrl = "", model = "qwen"))
        }
    }

    @Test
    fun resolvesSearchProviderAndEndpoint() {
        val resolved = resolveAiConfig(
            AiConfig(
                baseUrl = "https://api.example.com",
                model = "m",
                apiKey = "k",
                searchProvider = "tavily",
                tavilyApiKey = "tvly",
            ),
        )
        assertEquals("https://api.example.com/v1", resolved.baseUrl)
        assertEquals("tavily", resolved.searchProvider)
        assertEquals("https://api.example.com/v1/chat/completions", completionsUrl(resolved.baseUrl))
        assertEquals("https://api.example.com/v1/models", modelsUrl(resolved.baseUrl))
    }
}
