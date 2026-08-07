package com.strangeparticle.luther.core.client.provider.openai.response

import com.strangeparticle.luther.core.client.provider.ProviderErrorType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Tests for the non-throwing [OpenAiResponseParser.classifyError]. This is the
 * classification the retry layer inspects without triggering a throw; its results
 * must match what [OpenAiResponseParser.parseErrorAndThrow] produces for the same
 * inputs (see OpenAiResponseParserTest for the throw-site equivalents).
 */
internal class OpenAiResponseParserClassifyTest {

    @Test
    fun `HTTP 401 with no body classifies as InvalidApiKey`() {
        val exception = OpenAiResponseParser.classifyError(401, null)

        assertEquals(ProviderErrorType.InvalidApiKey, exception.classified)
    }

    @Test
    fun `rate_limit_exceeded code classifies as RateLimit`() {
        val body = """{"error":{"message":"rate limited","type":"rate_limit_error","code":"rate_limit_exceeded"}}"""

        val exception = OpenAiResponseParser.classifyError(429, body)

        assertEquals(ProviderErrorType.RateLimit, exception.classified)
        assertNotNull(exception.rawProviderMessage)
    }

    @Test
    fun `insufficient_quota code classifies as QuotaExceeded`() {
        val body = """{"error":{"message":"You exceeded your current quota","type":"insufficient_quota","code":"insufficient_quota"}}"""

        val exception = OpenAiResponseParser.classifyError(429, body)

        assertEquals(ProviderErrorType.QuotaExceeded, exception.classified)
        assertNotNull(exception.rawProviderMessage)
    }

    @Test
    fun `HTTP 503 with no body classifies as ProviderUnavailable`() {
        val exception = OpenAiResponseParser.classifyError(503, null)

        assertEquals(ProviderErrorType.ProviderUnavailable, exception.classified)
    }
}
