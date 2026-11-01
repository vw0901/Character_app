package com.ifitwereyou.character.ai

import com.ifitwereyou.character.data.SearchData
import com.ifitwereyou.character.data.SearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * 检索阶段：
 * 配置了 Tavily 就联网检索并整理摘要；没有检索结果或检索失败时，由模型知识兜底。
 * 与原项目 `/api/search` 的行为一致。
 */
class SearchService(
    private val aiClient: AiClient,
    baseHttp: OkHttpClient? = null,
) {
    private val http: OkHttpClient = baseHttp ?: OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    suspend fun searchNovel(config: AiConfig, novelName: String): SearchData {
        // 先校验一次配置，保持原接口「未配置 AI 服务」的报错优先级。
        resolveAiConfig(config)
        val query = "$novelName 小说 作者 完结状态 世界观设定"
        var results: List<SearchResult> = emptyList()
        var summary = ""

        if (config.searchProvider == "tavily" && config.tavilyApiKey.isNotBlank()) {
            try {
                val tavily = tavilySearch(config, query)
                results = tavily.first
                summary = tavily.second
                if (results.isNotEmpty() && summary.isBlank()) {
                    summary = try {
                        aiClient.chatOnce(
                            config = config,
                            messages = Prompts.searchSummaryMessages(query, results),
                            temperature = 0.3,
                            maxTokens = 800,
                            fallbackLabel = "整理检索结果失败",
                        )
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        snippetSummary(results)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Tavily 不可用时回退到模型知识，不中断流程。
            }
        }

        if (summary.isBlank()) {
            val reply = aiClient.chatOnce(
                config = config,
                messages = Prompts.searchFallbackMessages(novelName),
                temperature = 0.3,
                maxTokens = 800,
                fallbackLabel = "搜索小说信息失败",
            )
            summary = Prompts.parseFallbackSummary(reply)
        }

        return SearchData(novelName = novelName, summary = summary, results = results)
    }

    private suspend fun tavilySearch(
        config: AiConfig,
        query: String,
    ): Pair<List<SearchResult>, String> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("query", query)
            put("max_results", 10)
            put("include_answer", true)
            put("search_depth", "basic")
        }
        val request = Request.Builder()
            .url(TAVILY_URL)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Authorization", "Bearer ${config.tavilyApiKey.trim()}")
            .header("User-Agent", "novel-ai/1.0")
            .build()

        http.executeAsync(request).use { response ->
            if (!response.isSuccessful) {
                throw java.io.IOException("Tavily HTTP ${response.code}")
            }
            val root = JSONObject(response.body?.string().orEmpty())
            val rawItems = root.optJSONArray("results")
            val results = mutableListOf<SearchResult>()
            if (rawItems != null) {
                for (index in 0 until rawItems.length()) {
                    val item = rawItems.optJSONObject(index) ?: continue
                    val url = item.opt("url") as? String ?: ""
                    results.add(
                        SearchResult(
                            title = item.opt("title") as? String ?: "",
                            url = url,
                            snippet = (item.opt("content") as? String)
                                ?: (item.opt("snippet") as? String)
                                ?: "",
                            siteName = siteName(url),
                        ),
                    )
                }
            }
            val answer = (root.opt("answer") as? String).orEmpty().trim()
            results to answer
        }
    }

    private fun siteName(url: String): String {
        return try {
            val host = URI(url).host?.lowercase().orEmpty()
            if (host.startsWith("www.")) host.removePrefix("www.") else host
        } catch (_: Exception) {
            ""
        }
    }

    private fun snippetSummary(results: List<SearchResult>): String =
        results.take(5)
            .filter { it.snippet.isNotBlank() }
            .joinToString("\n\n") { "${it.title}：${it.snippet}" }

    companion object {
        private const val TAVILY_URL = "https://api.tavily.com/search"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
