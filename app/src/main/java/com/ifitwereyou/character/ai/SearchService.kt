package com.ifitwereyou.character.ai

import com.ifitwereyou.character.data.SearchData
import com.ifitwereyou.character.data.SearchResult
import com.ifitwereyou.character.data.clipCodePoints
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
        val queries = listOf(
            "$novelName 小说 作者 完结状态 类型 简介 结局",
            "$novelName 世界观 力量体系 势力 人物关系 设定",
            "$novelName 名场面 经典台词 剧情",
        )
        var results: List<SearchResult> = emptyList()
        var tavilyAnswer = ""

        if (config.searchProvider == "tavily" && config.tavilyApiKey.isNotBlank()) {
            // 三个角度并行检索，避免串行等待；单路失败不影响其余结果。
            val responses = coroutineScope {
                queries.map { query -> async { safeTavilySearch(config, query) } }.awaitAll()
            }.filterNotNull()
            results = mergeResults(responses.flatMap { it.first })
            tavilyAnswer = responses
                .mapNotNull { (_, answer) -> answer.takeIf { it.isNotBlank() } }
                .joinToString("\n\n")
                .trim()
        }

        val summary = if (results.isNotEmpty()) {
            try {
                val synthesized = aiClient.chatOnce(
                    config = config,
                    messages = Prompts.searchSummaryMessages(queries.first(), results, tavilyAnswer),
                    temperature = 0.3,
                    maxTokens = 1400,
                    fallbackLabel = "整理检索结果失败",
                ).trim()
                synthesized.ifBlank { fallbackSummary(tavilyAnswer, results) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                fallbackSummary(tavilyAnswer, results)
            }
        } else {
            val reply = aiClient.chatOnce(
                config = config,
                messages = Prompts.searchFallbackMessages(novelName),
                temperature = 0.3,
                maxTokens = 1400,
                fallbackLabel = "搜索小说信息失败",
            )
            Prompts.parseFallbackSummary(reply)
        }

        return SearchData(novelName = novelName, summary = summary, results = results)
    }

    private suspend fun safeTavilySearch(
        config: AiConfig,
        query: String,
    ): Pair<List<SearchResult>, String>? = try {
        tavilySearch(config, query)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    /** 多路检索结果去重、截断，优先保留 URL 唯一的条目。 */
    private fun mergeResults(all: List<SearchResult>): List<SearchResult> {
        val seen = HashSet<String>()
        return all.asSequence()
            .filter { it.title.isNotBlank() || it.snippet.isNotBlank() }
            .map { it.copy(snippet = clipCodePoints(it.snippet, 800)) }
            .filter { seen.add(it.url.ifBlank { it.title }) }
            .take(MAX_MERGED_RESULTS)
            .toList()
    }

    private fun fallbackSummary(tavilyAnswer: String, results: List<SearchResult>): String =
        tavilyAnswer.ifBlank { snippetSummary(results) }

    private suspend fun tavilySearch(
        config: AiConfig,
        query: String,
    ): Pair<List<SearchResult>, String> = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("query", query)
            put("max_results", 8)
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
        results.take(6)
            .filter { it.snippet.isNotBlank() }
            .joinToString("\n\n") { "${it.title}：${it.snippet}" }

    companion object {
        private const val TAVILY_URL = "https://api.tavily.com/search"
        private const val MAX_MERGED_RESULTS = 16
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
