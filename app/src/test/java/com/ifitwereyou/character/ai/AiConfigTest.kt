package com.ifitwereyou.character.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiConfigTest {

    @Test
    fun parsesLegacyConfigWithoutAdvancedParameters() {
        val config = SettingsStore.parseSavedConfig(
            """{"baseUrl":"https://api.deepseek.com/v1","apiKey":"k","model":"deepseek-chat","searchProvider":"none"}""",
        )
        assertNotNull(config)
        assertNull(config?.temperature)
        assertNull(config?.topP)
        assertNull(config?.maxTokens)
        assertNull(config?.frequencyPenalty)
        assertNull(config?.presencePenalty)
        assertNull(config?.seed)
        assertEquals("", config?.extraParams)
    }

    @Test
    fun parsesAdvancedParametersFromSavedJson() {
        val config = SettingsStore.parseSavedConfig(
            """
            {"baseUrl":"https://api.example.com/v1","model":"m",
             "temperature":0.7,"topP":0.9,"maxTokens":4096,
             "frequencyPenalty":0.1,"presencePenalty":-0.2,"seed":42,
             "extraParams":"{\"reasoning_effort\":\"low\"}"}
            """.trimIndent(),
        )
        assertEquals(0.7, config?.temperature ?: -1.0, 0.0001)
        assertEquals(0.9, config?.topP ?: -1.0, 0.0001)
        assertEquals(4096, config?.maxTokens)
        assertEquals(0.1, config?.frequencyPenalty ?: -1.0, 0.0001)
        assertEquals(-0.2, config?.presencePenalty ?: 0.0, 0.0001)
        assertEquals(42, config?.seed)
        assertEquals("""{"reasoning_effort":"low"}""", config?.extraParams)
    }

    @Test
    fun trimmedNormalizesSearchProviderCase() {
        val config = AiConfig(
            baseUrl = "  https://api.example.com/v1 ",
            apiKey = " k ",
            model = " m ",
            searchProvider = "TaViLy",
            tavilyApiKey = " tvly ",
        ).trimmed()
        assertEquals("https://api.example.com/v1", config.baseUrl)
        assertEquals("k", config.apiKey)
        assertEquals("m", config.model)
        assertEquals("tavily", config.searchProvider)
        assertEquals("tvly", config.tavilyApiKey)
    }

    @Test
    fun resolvesAllUserParameters() {
        val resolved = resolveAiConfig(
            AiConfig(
                baseUrl = "https://api.example.com",
                model = "m",
                temperature = 0.4,
                topP = 0.9,
                maxTokens = 1234,
                frequencyPenalty = 0.5,
                presencePenalty = -0.5,
                seed = 7,
                extraParams = """{"reasoning_effort":"low"}""",
            ),
        )
        assertEquals(0.4, resolved.temperature ?: -1.0, 0.0001)
        assertEquals(0.9, resolved.topP ?: -1.0, 0.0001)
        assertEquals(1234, resolved.maxTokens)
        assertEquals(0.5, resolved.frequencyPenalty ?: 0.0, 0.0001)
        assertEquals(-0.5, resolved.presencePenalty ?: 0.0, 0.0001)
        assertEquals(7, resolved.seed)
        assertEquals("low", resolved.extraParams.getString("reasoning_effort"))
    }

    @Test
    fun withoutSamplingOverridesKeepsOutputCapAndExtraParams() {
        val config = AiConfig(
            temperature = 0.9,
            topP = 0.9,
            frequencyPenalty = 0.5,
            presencePenalty = 0.5,
            maxTokens = 512,
            seed = 1,
            extraParams = """{"reasoning_effort":"low"}""",
        ).withoutSamplingOverrides()
        assertNull(config.temperature)
        assertNull(config.topP)
        assertNull(config.frequencyPenalty)
        assertNull(config.presencePenalty)
        assertEquals(512, config.maxTokens)
        assertEquals(1, config.seed)
        assertEquals("""{"reasoning_effort":"low"}""", config.extraParams)
    }

    @Test
    fun validatesParameterRanges() {
        assertNotNull(validateAiParams(AiConfig(temperature = 2.5)))
        assertNotNull(validateAiParams(AiConfig(temperature = -0.1)))
        assertNotNull(validateAiParams(AiConfig(topP = 1.2)))
        assertNotNull(validateAiParams(AiConfig(maxTokens = 0)))
        assertNotNull(validateAiParams(AiConfig(frequencyPenalty = 3.0)))
        assertNotNull(validateAiParams(AiConfig(presencePenalty = -3.0)))
        assertNull(validateAiParams(AiConfig(temperature = 0.7, topP = 0.9, maxTokens = 4096)))
    }

    @Test
    fun validatesExtraParamsIsJsonObjectAndProtectsCoreFields() {
        assertNull(validateAiParams(AiConfig(extraParams = """{"reasoning_effort":"low"}""")))
        assertNotNull(validateAiParams(AiConfig(extraParams = "not-json")))
        assertNotNull(validateAiParams(AiConfig(extraParams = """["not","object"]""")))
        assertNotNull(validateAiParams(AiConfig(extraParams = """{"model":"other"}""")))
        assertNotNull(validateAiParams(AiConfig(extraParams = """{"messages":[]}""")))
        assertNotNull(validateAiParams(AiConfig(extraParams = """{"stream":true}""")))
    }

    @Test
    fun parsesExtraParamsAndRejectsBrokenJson() {
        assertTrue(parseExtraParams("").isEmpty())
        assertEquals("low", parseExtraParams("""{"reasoning_effort":"low"}""").getString("reasoning_effort"))
        var failure: Throwable? = null
        try {
            parseExtraParams("{oops")
        } catch (error: Throwable) {
            failure = error
        }
        assertTrue(failure is AiConfigurationException)
    }

    @Test
    fun identifiesInsecurePublicCleartextHosts() {
        assertTrue(isInsecurePublicBaseUrl("http://api.example.com/v1"))
        assertTrue(isInsecurePublicBaseUrl("http://203.0.113.9/v1"))
        assertFalse(isInsecurePublicBaseUrl("https://api.example.com/v1"))
        assertFalse(isInsecurePublicBaseUrl("http://127.0.0.1:11434/v1"))
        assertFalse(isInsecurePublicBaseUrl("http://localhost:8000/v1"))
        assertFalse(isInsecurePublicBaseUrl("http://[::1]:8000/v1"))
        assertFalse(isInsecurePublicBaseUrl("http://10.0.2.2:4901/v1"))
        assertFalse(isInsecurePublicBaseUrl("http://192.168.1.8:8000/v1"))
        assertFalse(isInsecurePublicBaseUrl("http://172.20.0.3:8000/v1"))
    }
}
