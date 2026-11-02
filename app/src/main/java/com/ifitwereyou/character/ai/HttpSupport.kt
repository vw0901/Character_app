package com.ifitwereyou.character.ai

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.Closeable
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 已完成的请求。除了 Response 还保留 Call，
 * 便于流式读取被取消时用 call.cancel() 真正中断阻塞的 readLine()。
 */
internal class AiResponse(
    val response: Response,
    private val call: Call,
) : Closeable {
    override fun close() {
        response.close()
    }

    fun cancel() {
        // 只取消 Call：并发调用 response.close() 会和读取线程争抢 Okio 超时器，
        // 触发 "Unbalanced enter/exit"。Call.cancel() 会关闭底层 socket 并唤醒 readLine。
        call.cancel()
    }
}

internal suspend fun OkHttpClient.executeAsyncCall(request: Request): AiResponse =
    suspendCancellableCoroutine { continuation ->
        val call = newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) {
                    continuation.resume(AiResponse(response, call))
                } else {
                    response.close()
                    call.cancel()
                }
            }
        })
    }

internal suspend fun OkHttpClient.executeAsync(request: Request): Response =
    executeAsyncCall(request).response

internal fun Request.Builder.bearer(apiKey: String): Request.Builder =
    header("Authorization", "Bearer ${apiKey.ifBlank { "sk-local" }}")
        .header("User-Agent", "novel-ai/1.0")
