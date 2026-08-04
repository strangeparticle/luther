package com.strangeparticle.luther.core.client.provider.openai

import com.strangeparticle.luther.core.client.provider.ChatRequest
import com.strangeparticle.luther.core.client.provider.ProviderErrorType
import com.strangeparticle.luther.core.client.provider.ProviderException
import com.strangeparticle.luther.core.client.provider.StopReason
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests for [com.strangeparticle.luther.core.client.provider.openai.AiProviderClientOpenAi]. Wires the client to a Ktor [MockEngine] so we can
 * assert on the request shape (URL, headers, body) and inject the responses we
 * want.
 */
internal class AiProviderClientOpenAiTest {

    private fun emptyRequest() = ChatRequest(
        modelId = "gpt-5",
        systemPrompt = "you are an assistant",
        messages = emptyList(),
        tools = emptyList(),
    )

    private fun mockClient(
        respondWith: (io.ktor.client.request.HttpRequestData) -> io.ktor.client.engine.mock.MockRequestHandleScope.() -> io.ktor.client.request.HttpResponseData,
    ): HttpClient {
        val engine = MockEngine { request ->
            respondWith(request)()
        }
        return HttpClient(engine)
    }

    @Test
    fun `sendChat posts to chat completions endpoint with bearer auth`() = runTest {
        var capturedUrl: String? = null
        var capturedAuth: String? = null
        val client = HttpClient(MockEngine { request ->
            capturedUrl = request.url.toString()
            capturedAuth = request.headers[HttpHeaders.Authorization]
            respond(
                content = """{"choices":[{"message":{"content":"hi"},"finish_reason":"stop"}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        })
        val sut =
            com.strangeparticle.luther.core.client.provider.openai.AiProviderClientOpenAi(
                client,
                apiKeyProvider = { "sk-test" })

        sut.sendChat(emptyRequest())

        assertEquals("https://api.openai.com/v1/chat/completions", capturedUrl)
        assertEquals("Bearer sk-test", capturedAuth)
    }

    @Test
    fun `sendChat returns parsed response on 200`() = runTest {
        val client = HttpClient(MockEngine {
            respond(
                """{"choices":[{"message":{"content":"hello"},"finish_reason":"stop"}]}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        })
        val sut =
            com.strangeparticle.luther.core.client.provider.openai.AiProviderClientOpenAi(
                client,
                apiKeyProvider = { "sk-test" })

        val response = sut.sendChat(emptyRequest())

        assertEquals("hello", response.text)
        assertTrue(response.toolCalls.isEmpty())
        assertEquals(StopReason.Stop, response.stopReason)
    }

    @Test
    fun `sendChat throws InvalidApiKey on 401`() = runTest {
        val client = HttpClient(MockEngine {
            respond(
                """{"error":{"message":"Invalid API key"}}""",
                HttpStatusCode.Unauthorized,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        })
        val sut =
            com.strangeparticle.luther.core.client.provider.openai.AiProviderClientOpenAi(
                client,
                apiKeyProvider = { "sk-test" })

        val ex = assertFailsWith<ProviderException> { sut.sendChat(emptyRequest()) }
        assertEquals(ProviderErrorType.InvalidApiKey, ex.classified)
        assertEquals("Invalid API key", ex.rawProviderMessage)
    }

    @Test
    fun `sendChat throws RateLimit on 429`() = runTest {
        val client = HttpClient(MockEngine {
            respond("""{"error":{"message":"slow down"}}""", HttpStatusCode.TooManyRequests)
        })
        val sut =
            com.strangeparticle.luther.core.client.provider.openai.AiProviderClientOpenAi(
                client,
                apiKeyProvider = { "sk-test" })

        val ex = assertFailsWith<ProviderException> { sut.sendChat(emptyRequest()) }
        assertEquals(ProviderErrorType.RateLimit, ex.classified)
    }

    @Test
    fun `sendChat throws ProviderUnavailable on 5xx`() = runTest {
        val client = HttpClient(MockEngine {
            respond("internal", HttpStatusCode.InternalServerError)
        })
        val sut =
            com.strangeparticle.luther.core.client.provider.openai.AiProviderClientOpenAi(
                client,
                apiKeyProvider = { "sk-test" })

        val ex = assertFailsWith<ProviderException> { sut.sendChat(emptyRequest()) }
        assertEquals(ProviderErrorType.ProviderUnavailable, ex.classified)
    }

    @Test
    fun `sendChat throws Network on transport exception`() = runTest {
        val client = HttpClient(MockEngine {
            throw RuntimeException("connection refused")
        })
        val sut =
            com.strangeparticle.luther.core.client.provider.openai.AiProviderClientOpenAi(
                client,
                apiKeyProvider = { "sk-test" })

        val ex = assertFailsWith<ProviderException> { sut.sendChat(emptyRequest()) }
        assertEquals(ProviderErrorType.Network, ex.classified)
    }

    @Test
    fun `sendChat throws InvalidApiKey when apiKey is null`() = runTest {
        val client = HttpClient(MockEngine {
            error("sendChat should never call the engine when api key is missing")
        })
        val sut =
            com.strangeparticle.luther.core.client.provider.openai.AiProviderClientOpenAi(
                client,
                apiKeyProvider = { null })

        val ex = assertFailsWith<ProviderException> { sut.sendChat(emptyRequest()) }
        assertEquals(ProviderErrorType.InvalidApiKey, ex.classified)
    }

    @Test
    fun `listModels GETs models endpoint and returns filtered, sorted models`() = runTest {
        var capturedMethod: HttpMethod? = null
        var capturedUrl: String? = null
        val client = HttpClient(MockEngine { request ->
            capturedMethod = request.method
            capturedUrl = request.url.toString()
            respond(
                """
                {"object":"list","data":[
                  {"id":"gpt-5","object":"model"},
                  {"id":"text-embedding-3-large","object":"model"},
                  {"id":"gpt-4o","object":"model"}
                ]}
                """.trimIndent(),
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        })
        val sut =
            com.strangeparticle.luther.core.client.provider.openai.AiProviderClientOpenAi(
                client,
                apiKeyProvider = { "sk-list-test" })

        val models = sut.listModels()

        assertEquals(HttpMethod.Get, capturedMethod)
        assertEquals("https://api.openai.com/v1/models", capturedUrl)
        assertEquals(listOf("gpt-5", "gpt-4o"), models.map { it.id })
    }

    @Test
    fun `listModels throws InvalidApiKey on blank apiKey`() = runTest {
        val client = HttpClient(MockEngine {
            error("should not reach engine when api key is blank")
        })
        val sut =
            com.strangeparticle.luther.core.client.provider.openai.AiProviderClientOpenAi(
                client,
                apiKeyProvider = { null })

        val ex = assertFailsWith<ProviderException> { sut.listModels() }
        assertEquals(ProviderErrorType.InvalidApiKey, ex.classified)
    }

    @Test
    fun `listModels throws InvalidApiKey on 401`() = runTest {
        val client = HttpClient(MockEngine {
            respond(
                """{"error":{"message":"bad key"}}""",
                HttpStatusCode.Unauthorized,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        })
        val sut =
            com.strangeparticle.luther.core.client.provider.openai.AiProviderClientOpenAi(
                client,
                apiKeyProvider = { "sk-test" })

        val ex = assertFailsWith<ProviderException> { sut.listModels() }
        assertEquals(ProviderErrorType.InvalidApiKey, ex.classified)
    }

    @Test
    fun `sendChat propagates CancellationException without reclassifying as Network`() = runTest {
        val client = HttpClient(MockEngine {
            // Simulate the in-flight request being cancelled — the engine throws
            // a CancellationException, which should bubble up uncaught.
            throw kotlinx.coroutines.CancellationException("turn cancelled by user")
        })
        val sut =
            com.strangeparticle.luther.core.client.provider.openai.AiProviderClientOpenAi(
                client,
                apiKeyProvider = { "sk-test" })

        // The expectation is the CancellationException — NOT an AiException.
        assertFailsWith<kotlinx.coroutines.CancellationException> {
            sut.sendChat(emptyRequest())
        }
    }

    @Test
    fun `listModels propagates CancellationException without reclassifying as Network`() = runTest {
        val client = HttpClient(MockEngine {
            throw kotlinx.coroutines.CancellationException("settings dialog cancelled fetch")
        })
        val sut =
            com.strangeparticle.luther.core.client.provider.openai.AiProviderClientOpenAi(
                client,
                apiKeyProvider = { "sk-test" })

        assertFailsWith<kotlinx.coroutines.CancellationException> {
            sut.listModels()
        }
    }
}
