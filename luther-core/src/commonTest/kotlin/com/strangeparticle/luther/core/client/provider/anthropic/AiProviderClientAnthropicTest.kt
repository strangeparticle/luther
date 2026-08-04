package com.strangeparticle.luther.core.client.provider.anthropic

import com.strangeparticle.luther.core.client.provider.ChatRequest
import com.strangeparticle.luther.core.client.provider.ProviderErrorType
import com.strangeparticle.luther.core.client.provider.ProviderException
import com.strangeparticle.luther.core.client.provider.StopReason
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [AiProviderClientAnthropic]. Wires the client to a Ktor [MockEngine] so we can assert
 * on request shape (URL, headers, body) and inject the provider responses we want.
 */
internal class AiProviderClientAnthropicTest {

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

    private val modelListBody = """
        {"data":[{"type":"model","id":"claude-sonnet-4-6","display_name":"Claude Sonnet 4.6"}]}
    """.trimIndent()

    // ── sendChat ─────────────────────────────────────────────────────────

    @Test
    fun `sendChat posts to messages endpoint`() = runTest {
        var capturedUrl: String? = null
        val client = HttpClient(MockEngine { request ->
            capturedUrl = request.url.toString()
            respond(successBody, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        AiProviderClientAnthropic(client, apiKeyProvider = { "sk-ant-test" }).sendChat(emptyRequest())

        assertEquals("https://api.anthropic.com/v1/messages", capturedUrl)
    }

    @Test
    fun `sendChat includes x-api-key header`() = runTest {
        var capturedKey: String? = null
        val client = HttpClient(MockEngine { request ->
            capturedKey = request.headers["x-api-key"]
            respond(successBody, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        AiProviderClientAnthropic(client, apiKeyProvider = { "sk-ant-test" }).sendChat(emptyRequest())

        assertEquals("sk-ant-test", capturedKey)
    }

    @Test
    fun `sendChat includes anthropic-version header`() = runTest {
        var capturedVersion: String? = null
        val client = HttpClient(MockEngine { request ->
            capturedVersion = request.headers["anthropic-version"]
            respond(successBody, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        AiProviderClientAnthropic(client, apiKeyProvider = { "sk-ant-test" }).sendChat(emptyRequest())

        assertEquals(AiProviderClientAnthropic.ANTHROPIC_VERSION, capturedVersion)
    }

    @Test
    fun `sendChat does not include Authorization Bearer header`() = runTest {
        var capturedAuth: String? = null
        val client = HttpClient(MockEngine { request ->
            capturedAuth = request.headers[HttpHeaders.Authorization]
            respond(successBody, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        AiProviderClientAnthropic(client, apiKeyProvider = { "sk-ant-test" }).sendChat(emptyRequest())

        assertTrue(capturedAuth == null, "Anthropic uses x-api-key, not Authorization: Bearer")
    }

    @Test
    fun `sendChat returns parsed response on success`() = runTest {
        val client = HttpClient(MockEngine { respond(successBody, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) })
        val result = AiProviderClientAnthropic(client, apiKeyProvider = { "sk-ant-test" }).sendChat(emptyRequest())

        assertEquals("hello", result.text)
        assertEquals(StopReason.Stop, result.stopReason)
    }

    @Test
    fun `sendChat throws InvalidApiKey when key is missing`() = runTest {
        val client = HttpClient(MockEngine { respond("", HttpStatusCode.OK) })
        val error = assertFailsWith<ProviderException> {
            AiProviderClientAnthropic(client, apiKeyProvider = { null }).sendChat(emptyRequest())
        }
        assertEquals(ProviderErrorType.InvalidApiKey, error.classified)
    }

    @Test
    fun `sendChat classifies 401 as InvalidApiKey`() = runTest {
        val body = """{"type":"error","error":{"type":"authentication_error","message":"Invalid key"}}"""
        val client = HttpClient(MockEngine { respond(body, HttpStatusCode.Unauthorized, headersOf(HttpHeaders.ContentType, "application/json")) })
        val error = assertFailsWith<ProviderException> {
            AiProviderClientAnthropic(client, apiKeyProvider = { "bad-key" }).sendChat(emptyRequest())
        }
        assertEquals(ProviderErrorType.InvalidApiKey, error.classified)
    }

    @Test
    fun `sendChat classifies 429 as RateLimit`() = runTest {
        val body = """{"type":"error","error":{"type":"rate_limit_error","message":"Too many requests"}}"""
        val client = HttpClient(MockEngine { respond(body, HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.ContentType, "application/json")) })
        val error = assertFailsWith<ProviderException> {
            AiProviderClientAnthropic(client, apiKeyProvider = { "sk-ant-test" }).sendChat(emptyRequest())
        }
        assertEquals(ProviderErrorType.RateLimit, error.classified)
    }

    @Test
    fun `sendChat classifies 529 as ProviderUnavailable`() = runTest {
        val body = """{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}"""
        val client = HttpClient(MockEngine { respond(body, HttpStatusCode(529, "Overloaded"), headersOf(HttpHeaders.ContentType, "application/json")) })
        val error = assertFailsWith<ProviderException> {
            AiProviderClientAnthropic(client, apiKeyProvider = { "sk-ant-test" }).sendChat(emptyRequest())
        }
        assertEquals(ProviderErrorType.ProviderUnavailable, error.classified)
    }

    @Test
    fun `sendChat classifies network exception as Network error`() = runTest {
        val client = HttpClient(MockEngine { throw Exception("connection refused") })
        val error = assertFailsWith<ProviderException> {
            AiProviderClientAnthropic(client, apiKeyProvider = { "sk-ant-test" }).sendChat(emptyRequest())
        }
        assertEquals(ProviderErrorType.Network, error.classified)
    }

    @Test
    fun `sendChat propagates CancellationException`() = runTest {
        val client = HttpClient(MockEngine { throw CancellationException("cancelled") })
        assertFailsWith<CancellationException> {
            AiProviderClientAnthropic(client, apiKeyProvider = { "sk-ant-test" }).sendChat(emptyRequest())
        }
    }

    // ── listModels ────────────────────────────────────────────────────────────

    @Test
    fun `listModels returns filtered and sorted models`() = runTest {
        val client = HttpClient(MockEngine { respond(modelListBody, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) })
        val models = AiProviderClientAnthropic(client, apiKeyProvider = { "sk-ant-test" }).listModels()

        assertEquals(1, models.size)
        assertEquals("claude-sonnet-4-6", models[0].id)
    }

    @Test
    fun `listModels includes x-api-key and anthropic-version headers`() = runTest {
        var capturedKey: String? = null
        var capturedVersion: String? = null
        val client = HttpClient(MockEngine { request ->
            capturedKey = request.headers["x-api-key"]
            capturedVersion = request.headers["anthropic-version"]
            respond(modelListBody, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        AiProviderClientAnthropic(client, apiKeyProvider = { "sk-ant-test" }).listModels()

        assertEquals("sk-ant-test", capturedKey)
        assertEquals(AiProviderClientAnthropic.ANTHROPIC_VERSION, capturedVersion)
    }

    @Test
    fun `listModels throws InvalidApiKey when key is blank`() = runTest {
        val client = HttpClient(MockEngine { respond("", HttpStatusCode.OK) })
        val error = assertFailsWith<ProviderException> {
            AiProviderClientAnthropic(client, apiKeyProvider = { "" }).listModels()
        }
        assertEquals(ProviderErrorType.InvalidApiKey, error.classified)
    }
}
