package com.strangeparticle.luther.core.client.provider.anthropic

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
 * Retry behavior for [AiProviderClientAnthropic]'s shared `postOrThrow`, which now delegates to
 * `postWithRetry` (see ProviderHttpRetryUtil.kt). Both `sendChat` (blocking) and `responseStream`
 * (SSE) funnel through the same `postOrThrow`, so both get retry — streaming only ever retries
 * before the first SSE emit, since the retry loop lives inside the single `postOrThrow` call that
 * happens before any `emit`.
 */
internal class AiProviderClientAnthropicRetryTest {

    private fun emptyRequest() = ChatRequest(
        modelId = "claude-sonnet-4-6",
        systemPrompt = "you are an assistant",
        messages = emptyList(),
        tools = emptyList(),
    )

    private val successBody = """
        {
          "id": "msg_01",
          "type": "message",
          "role": "assistant",
          "model": "claude-sonnet-4-6",
          "content": [{"type": "text", "text": "hello"}],
          "stop_reason": "end_turn",
          "usage": {"input_tokens": 10, "output_tokens": 5}
        }
    """.trimIndent()

    private val streamSse = buildString {
        append("event: content_block_start\ndata: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}\n\n")
        append("event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"Hi\"}}\n\n")
        append("event: message_delta\ndata: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"}}\n\n")
        append("event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n")
    }

    @Test
    fun `retries a 429 with Retry-After then succeeds`() = runTest {
        var callCount = 0
        val client = HttpClient(MockEngine { _ ->
            callCount++
            if (callCount == 1) {
                val body = """{"type":"error","error":{"type":"rate_limit_error","message":"Too many requests"}}"""
                respond(body, HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.RetryAfter, "1"))
            } else {
                respond(successBody, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        })

        val result = AiProviderClientAnthropic(client, apiKeyProvider = { "sk-ant-test" }).sendChat(emptyRequest())

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
            AiProviderClientAnthropic(client, apiKeyProvider = { "sk-ant-test" }).sendChat(emptyRequest())
        }

        assertEquals(ProviderErrorType.ProviderUnavailable, error.classified)
        assertEquals(3, callCount)
    }

    @Test
    fun `does not retry a 401`() = runTest {
        var callCount = 0
        val client = HttpClient(MockEngine { _ ->
            callCount++
            val body = """{"type":"error","error":{"type":"authentication_error","message":"Invalid key"}}"""
            respond(body, HttpStatusCode.Unauthorized, headersOf(HttpHeaders.ContentType, "application/json"))
        })

        val error = assertFailsWith<ProviderException> {
            AiProviderClientAnthropic(client, apiKeyProvider = { "sk-ant-test" }).sendChat(emptyRequest())
        }

        assertEquals(ProviderErrorType.InvalidApiKey, error.classified)
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

        val events = AiProviderClientAnthropic(client, apiKeyProvider = { "sk-ant-test" })
            .responseStream(emptyRequest()).toList()

        assertEquals(2, callCount)
        assertEquals(ChatResponseEvent.TextDelta("Hi"), events.first())
        val completed = assertIs<ChatResponseEvent.Completed>(events.last())
        assertEquals("Hi", completed.response.text)
    }
}
