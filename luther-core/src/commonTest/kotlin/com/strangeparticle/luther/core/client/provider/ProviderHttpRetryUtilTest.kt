package com.strangeparticle.luther.core.client.provider

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

/**
 * Tests for [postWithRetry] and [parseRetryAfter], run in isolation from any real provider
 * client. `performPost` is driven by a stateful closure over a [MockEngine]-backed [HttpClient]
 * so each test can script a sequence of responses (and/or transport exceptions) and assert on
 * both the outcome and the number of attempts made.
 */
internal class ProviderHttpRetryUtilTest {

    // A minimal, deliberately simple classifier so these tests don't depend on any real
    // provider's error-body format: 429 -> RateLimit (or QuotaExceeded if the body mentions
    // quota), 503 -> ProviderUnavailable, 401 -> InvalidApiKey, anything else -> Unknown.
    private fun testClassifyError(status: Int, body: String): ProviderException {
        return when {
            status == 429 && body.contains("quota") ->
                ProviderException(ProviderErrorType.QuotaExceeded, "Quota exceeded", rawProviderMessage = body)
            status == 429 ->
                ProviderException(ProviderErrorType.RateLimit, "Rate limited", rawProviderMessage = body)
            status == 503 ->
                ProviderException(ProviderErrorType.ProviderUnavailable, "Provider unavailable", rawProviderMessage = body)
            status == 401 ->
                ProviderException(ProviderErrorType.InvalidApiKey, "Invalid api key", rawProviderMessage = body)
            else ->
                ProviderException(ProviderErrorType.Unknown, "Unknown error $status", rawProviderMessage = body)
        }
    }

    @Test
    fun `429 with Retry-After 1 then 200`() = runTest {
        var callCount = 0
        val client = HttpClient(MockEngine { _ ->
            callCount++
            if (callCount == 1) {
                respond("rate limited", HttpStatusCode.TooManyRequests, headersOf(HttpHeaders.RetryAfter, "1"))
            } else {
                respond("ok", HttpStatusCode.OK)
            }
        })

        val response = postWithRetry(
            performPost = { client.get("http://test") },
            classifyError = ::testClassifyError,
        )

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(2, callCount)
    }

    @Test
    fun `three retryable 503s exhaust attempts`() = runTest {
        var callCount = 0
        val client = HttpClient(MockEngine { _ ->
            callCount++
            respond("unavailable", HttpStatusCode.ServiceUnavailable)
        })

        val exception = assertFailsWith<ProviderException> {
            postWithRetry(
                performPost = { client.get("http://test") },
                classifyError = ::testClassifyError,
            )
        }

        assertEquals(ProviderErrorType.ProviderUnavailable, exception.classified)
        assertEquals(3, callCount)
    }

    @Test
    fun `a non-retryable 401 does not retry`() = runTest {
        var callCount = 0
        val client = HttpClient(MockEngine { _ ->
            callCount++
            respond("bad key", HttpStatusCode.Unauthorized)
        })

        val exception = assertFailsWith<ProviderException> {
            postWithRetry(
                performPost = { client.get("http://test") },
                classifyError = ::testClassifyError,
            )
        }

        assertEquals(ProviderErrorType.InvalidApiKey, exception.classified)
        assertEquals(1, callCount)
    }

    @Test
    fun `a transport exception then 200 is retried`() = runTest {
        var callCount = 0
        val client = HttpClient(MockEngine { _ ->
            callCount++
            if (callCount == 1) {
                throw RuntimeException("connection reset")
            } else {
                respond("ok", HttpStatusCode.OK)
            }
        })

        val response = postWithRetry(
            performPost = { client.get("http://test") },
            classifyError = ::testClassifyError,
        )

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ok", response.bodyAsText())
        assertEquals(2, callCount)
    }

    @Test
    fun `insufficient_quota is not retried`() = runTest {
        var callCount = 0
        val client = HttpClient(MockEngine { _ ->
            callCount++
            respond("""{"error":"insufficient_quota"}""", HttpStatusCode.TooManyRequests)
        })

        val exception = assertFailsWith<ProviderException> {
            postWithRetry(
                performPost = { client.get("http://test") },
                classifyError = ::testClassifyError,
            )
        }

        assertEquals(ProviderErrorType.QuotaExceeded, exception.classified)
        assertEquals(1, callCount)
    }

    @Test
    fun `retries complete under a fixed seed random without throwing`() = runTest {
        var callCount = 0
        val client = HttpClient(MockEngine { _ ->
            callCount++
            if (callCount < 3) {
                respond("unavailable", HttpStatusCode.ServiceUnavailable)
            } else {
                respond("ok", HttpStatusCode.OK)
            }
        })

        val response = postWithRetry(
            policy = RetryPolicy(maxAttempts = 5),
            performPost = { client.get("http://test") },
            classifyError = ::testClassifyError,
            random = Random(42),
        )

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(3, callCount)
    }

    // ── parseRetryAfter ──────────────────────────────────────────────────

    @Test
    fun `parseRetryAfter numeric delta seconds`() {
        assertEquals(5.seconds, parseRetryAfter("5"))
    }

    @Test
    fun `parseRetryAfter blank returns null`() {
        assertNull(parseRetryAfter(""))
    }

    @Test
    fun `parseRetryAfter garbage returns null`() {
        assertNull(parseRetryAfter("garbage"))
    }

    @Test
    fun `parseRetryAfter null returns null`() {
        assertNull(parseRetryAfter(null))
    }
}
