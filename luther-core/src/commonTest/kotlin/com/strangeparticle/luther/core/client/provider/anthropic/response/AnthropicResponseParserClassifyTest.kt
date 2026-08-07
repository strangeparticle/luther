package com.strangeparticle.luther.core.client.provider.anthropic.response

import com.strangeparticle.luther.core.client.provider.ProviderErrorType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Tests for the non-throwing [AnthropicResponseParser.classifyError]. This is the
 * classification the retry layer inspects without triggering a throw; its results
 * must match what [AnthropicResponseParser.parseErrorAndThrow] produces for the
 * same inputs (see AnthropicResponseParserTest for the throw-site equivalents).
 */
internal class AnthropicResponseParserClassifyTest {

    @Test
    fun `HTTP 401 with no body classifies as InvalidApiKey`() {
        val exception = AnthropicResponseParser.classifyError(401, null)

        assertEquals(ProviderErrorType.InvalidApiKey, exception.classified)
    }

    @Test
    fun `HTTP 429 with no body classifies as RateLimit`() {
        val exception = AnthropicResponseParser.classifyError(429, null)

        assertEquals(ProviderErrorType.RateLimit, exception.classified)
    }

    @Test
    fun `HTTP 529 with no body classifies as ProviderUnavailable`() {
        val exception = AnthropicResponseParser.classifyError(529, null)

        assertEquals(ProviderErrorType.ProviderUnavailable, exception.classified)
    }

    @Test
    fun `overloaded_error body classifies as ProviderUnavailable`() {
        val body = """{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}"""

        val exception = AnthropicResponseParser.classifyError(529, body)

        assertEquals(ProviderErrorType.ProviderUnavailable, exception.classified)
        assertNotNull(exception.rawProviderMessage)
    }

    @Test
    fun `invalid_request_error body mentioning context classifies as ContextTooLarge`() {
        val body = """{"type":"error","error":{"type":"invalid_request_error","message":"prompt is too long: 200000 tokens exceeds context window"}}"""

        val exception = AnthropicResponseParser.classifyError(400, body)

        assertEquals(ProviderErrorType.ContextTooLarge, exception.classified)
        assertNotNull(exception.rawProviderMessage)
    }
}
