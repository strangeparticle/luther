package com.strangeparticle.luther.core.client.provider.openai

import com.strangeparticle.luther.core.client.provider.ChatRequest
import com.strangeparticle.luther.core.client.provider.ChatResponseEvent
import com.strangeparticle.luther.core.client.provider.ProviderErrorType
import com.strangeparticle.luther.core.client.provider.ProviderException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * Retry behavior for [AiProviderClientOpenAi]'s shared `postOrThrow`, which now delegates to
 * `postWithRetry` (see ProviderHttpRetryUtil.kt). Both `sendChat` (blocking) and `responseStream`
 * (SSE) funnel through the same `postOrThrow`, so both get retry — streaming only ever retries
 * before the first SSE emit, since the retry loop lives inside the single `postOrThrow` call that
 * happens before any `emit`.
 */
internal class AiProviderClientOpenAiRetryTest {

    private fun emptyRequest() = ChatRequest(
        modelId = "gpt-5",
        systemPrompt = "you are an assistant",
        messages = emptyList(),
        tools = emptyList(),
    )

    private val successBody = """{"choices":[{"message":{"content":"hello"},"finish_reason":"stop"}]}"""

    private val streamSse = buildString {
        append("data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hi\"},\"finish_reason\":null}]}\n\n")
        append("data: {\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n")
        append("data: [DONE]\n\n")
    }

    @Test
    fun `retries a 429 with Retry-After then succeeds`() = runTest {
        var callCount = 0
        val client = HttpClient(MockEngine { _ ->
            callCount++
            if (callCount == 1) {
                respond(
                    """{"error":{"message":"slow down"}}""",
                    HttpStatusCode.TooManyRequests,
                    headersOf(HttpHeaders.RetryAfter, "1"),
                )
            } else {
                respond(successBody, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        })

        val result = AiProviderClientOpenAi(client, apiKeyProvider = { "sk-test" }).sendChat(emptyRequest())

        assertEquals("hello", result.text)
        assertEquals(2, callCount)
    }

    @Test
    fun `gives up after repeated 503s`() = runTest {
        var callCount = 0
        val client = HttpClient(MockEngine { _ ->
            callCount++
            respond("service unavailable", HttpStatusCode.ServiceUnavailable)
        })

        val error = assertFailsWith<ProviderException> {
            AiProviderClientOpenAi(client, apiKeyProvider = { "sk-test" }).sendChat(emptyRequest())
        }

        assertEquals(ProviderErrorType.ProviderUnavailable, error.classified)
        assertEquals(3, callCount)
    }

    @Test
    fun `does not retry a quota error`() = runTest {
        var callCount = 0
        val client = HttpClient(MockEngine { _ ->
            callCount++
            val body = """{"error":{"message":"You exceeded your quota","code":"insufficient_quota"}}"""
            respond(body, HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.ContentType, "application/json"))
        })

        val error = assertFailsWith<ProviderException> {
            AiProviderClientOpenAi(client, apiKeyProvider = { "sk-test" }).sendChat(emptyRequest())
        }

        assertEquals(ProviderErrorType.QuotaExceeded, error.classified)
        assertEquals(1, callCount)
    }

    @Test
    fun `streaming retries a 503 before emitting then streams`() = runTest {
        var callCount = 0
        val client = HttpClient(MockEngine { _ ->
            callCount++
            if (callCount == 1) {
                respond("service unavailable", HttpStatusCode.ServiceUnavailable)
            } else {
                respond(streamSse, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/event-stream"))
            }
        })

        val events = AiProviderClientOpenAi(client, apiKeyProvider = { "sk-test" })
            .responseStream(emptyRequest()).toList()

        assertEquals(2, callCount)
        assertEquals(ChatResponseEvent.TextDelta("Hi"), events.first())
        val completed = assertIs<ChatResponseEvent.Completed>(events.last())
        assertEquals("Hi", completed.response.text)
    }
}
