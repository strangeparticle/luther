package com.strangeparticle.luther.core.client.provider.anthropic

import com.strangeparticle.luther.core.client.provider.ProviderErrorType
import com.strangeparticle.luther.core.client.provider.ProviderException
import com.strangeparticle.luther.core.client.provider.StopReason
import com.strangeparticle.luther.core.client.provider.anthropic.response.AnthropicResponseParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [AnthropicResponseParser]. Each test includes a representative JSON body so
 * the test doubles as documentation of the Anthropic Messages API wire shapes decoded
 * at this boundary.
 */
internal class AnthropicResponseParserTest {

    // ── parseSuccess ─────────────────────────────────────────────────────────

    @Test
    fun `parseSuccess_textOnlyResponse returns text and empty toolCalls`() {
        val body = """
            {
              "id": "msg_01",
              "type": "message",
              "role": "assistant",
              "model": "claude-sonnet-4-6",
              "content": [{"type": "text", "text": "An activator maps a coordinate to an action."}],
              "stop_reason": "end_turn",
              "usage": {"input_tokens": 100, "output_tokens": 20}
            }
        """.trimIndent()

        val result = AnthropicResponseParser.parseSuccess(body)

        assertEquals("An activator maps a coordinate to an action.", result.text)
        assertTrue(result.toolCalls.isEmpty())
        assertEquals(StopReason.Stop, result.stopReason)
    }

    @Test
    fun `parseSuccess_toolUseOnlyResponse returns toolCalls and null text`() {
        val body = """
            {
              "id": "msg_02",
              "type": "message",
              "role": "assistant",
              "model": "claude-sonnet-4-6",
              "content": [
                {
                  "type": "tool_use",
                  "id": "toolu_01",
                  "name": "add_app",
                  "input": {"tab_id": "tab-1", "id": "grafana", "name": "Grafana", "display_message": "Added"}
                }
              ],
              "stop_reason": "tool_use",
              "usage": {"input_tokens": 200, "output_tokens": 50}
            }
        """.trimIndent()

        val result = AnthropicResponseParser.parseSuccess(body)

        assertNull(result.text)
        assertEquals(1, result.toolCalls.size)
        assertEquals("toolu_01", result.toolCalls[0].id)
        assertEquals("add_app", result.toolCalls[0].name)
        assertEquals(StopReason.ToolUse, result.stopReason)
    }

    @Test
    fun `parseSuccess_toolUseInput is serialized to a JSON string`() {
        val body = """
            {
              "id": "msg_03",
              "type": "message",
              "role": "assistant",
              "model": "claude-sonnet-4-6",
              "content": [
                {
                  "type": "tool_use",
                  "id": "toolu_01",
                  "name": "add_app",
                  "input": {"tab_id": "tab-1", "id": "grafana", "name": "Grafana", "display_message": "Added"}
                }
              ],
              "stop_reason": "tool_use",
              "usage": {"input_tokens": 200, "output_tokens": 50}
            }
        """.trimIndent()

        val result = AnthropicResponseParser.parseSuccess(body)

        val argsJson = result.toolCalls[0].argumentsJson
        assertTrue(argsJson.contains("grafana"), "argumentsAsJsonString must contain the input values")
        assertTrue(argsJson.startsWith("{"), "argumentsAsJsonString must be a JSON object string")
    }

    @Test
    fun `parseSuccess_mixedTextAndToolUse returns both text and toolCalls`() {
        val body = """
            {
              "id": "msg_04",
              "type": "message",
              "role": "assistant",
              "model": "claude-sonnet-4-6",
              "content": [
                {"type": "text", "text": "I'll add that app now."},
                {"type": "tool_use", "id": "toolu_01", "name": "add_app", "input": {"tab_id": "t1", "id": "a1", "name": "App", "display_message": "x"}}
              ],
              "stop_reason": "tool_use",
              "usage": {"input_tokens": 300, "output_tokens": 80}
            }
        """.trimIndent()

        val result = AnthropicResponseParser.parseSuccess(body)

        assertEquals("I'll add that app now.", result.text)
        assertEquals(1, result.toolCalls.size)
        assertEquals("add_app", result.toolCalls[0].name)
    }

    @Test
    fun `parseSuccess_multipleToolUse returns all toolCalls`() {
        val body = """
            {
              "id": "msg_05",
              "type": "message",
              "role": "assistant",
              "model": "claude-sonnet-4-6",
              "content": [
                {"type": "tool_use", "id": "id1", "name": "add_app", "input": {"tab_id": "t1", "id": "a1", "name": "App1", "display_message": "x"}},
                {"type": "tool_use", "id": "id2", "name": "add_resource", "input": {"tab_id": "t1", "id": "r1", "name": "Res1", "display_message": "x"}}
              ],
              "stop_reason": "tool_use",
              "usage": {"input_tokens": 400, "output_tokens": 100}
            }
        """.trimIndent()

        val result = AnthropicResponseParser.parseSuccess(body)

        assertEquals(2, result.toolCalls.size)
        assertEquals("id1", result.toolCalls[0].id)
        assertEquals("id2", result.toolCalls[1].id)
    }

    @Test
    fun `parseSuccess_stopReasonMapping covers all cases`() {
        fun stopReasonFor(reason: String): StopReason {
            val body = """{"id":"m","type":"message","role":"assistant","model":"claude","content":[{"type":"text","text":"ok"}],"stop_reason":"$reason"}"""
            return AnthropicResponseParser.parseSuccess(body).stopReason
        }

        assertEquals(StopReason.Stop, stopReasonFor("end_turn"))
        assertEquals(StopReason.Stop, stopReasonFor("stop_sequence"))
        assertEquals(StopReason.ToolUse, stopReasonFor("tool_use"))
        assertEquals(StopReason.MaxTokens, stopReasonFor("max_tokens"))
        assertEquals(StopReason.Other, stopReasonFor("unknown_reason"))
    }

    @Test
    fun `parseSuccess_malformedBodyThrowsMalformedResponse`() {
        val error = assertFailsWith<ProviderException> {
            AnthropicResponseParser.parseSuccess("not json")
        }
        assertEquals(ProviderErrorType.MalformedResponse, error.classified)
    }

    // ── parseErrorAndThrow ───────────────────────────────────────────────────

    @Test
    fun `parseErrorAndThrow_authenticationError maps to InvalidApiKey`() {
        val body = """{"type":"error","error":{"type":"authentication_error","message":"Invalid API key"}}"""
        val error = assertFailsWith<ProviderException> {
            AnthropicResponseParser.parseErrorAndThrow(401, body)
        }
        assertEquals(ProviderErrorType.InvalidApiKey, error.classified)
    }

    @Test
    fun `parseErrorAndThrow_permissionError maps to InvalidApiKey`() {
        val body = """{"type":"error","error":{"type":"permission_error","message":"Forbidden"}}"""
        val error = assertFailsWith<ProviderException> {
            AnthropicResponseParser.parseErrorAndThrow(403, body)
        }
        assertEquals(ProviderErrorType.InvalidApiKey, error.classified)
    }

    @Test
    fun `parseErrorAndThrow_rateLimitError maps to RateLimit`() {
        val body = """{"type":"error","error":{"type":"rate_limit_error","message":"Too many requests"}}"""
        val error = assertFailsWith<ProviderException> {
            AnthropicResponseParser.parseErrorAndThrow(429, body)
        }
        assertEquals(ProviderErrorType.RateLimit, error.classified)
    }

    @Test
    fun `parseErrorAndThrow_overloadedError maps to ProviderUnavailable on status 529`() {
        val body = """{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}"""
        val error = assertFailsWith<ProviderException> {
            AnthropicResponseParser.parseErrorAndThrow(529, body)
        }
        assertEquals(ProviderErrorType.ProviderUnavailable, error.classified)
    }

    @Test
    fun `parseErrorAndThrow_invalidRequestWithContextInMessage maps to ContextTooLarge`() {
        val body = """{"type":"error","error":{"type":"invalid_request_error","message":"prompt is too long: 200000 tokens exceeds context window"}}"""
        val error = assertFailsWith<ProviderException> {
            AnthropicResponseParser.parseErrorAndThrow(400, body)
        }
        assertEquals(ProviderErrorType.ContextTooLarge, error.classified)
    }

    @Test
    fun `parseErrorAndThrow_fallsBackToHttpStatusWhenBodyIsUnparseable`() {
        val error = assertFailsWith<ProviderException> {
            AnthropicResponseParser.parseErrorAndThrow(429, "not json")
        }
        assertEquals(ProviderErrorType.RateLimit, error.classified)
    }

    @Test
    fun `parseErrorAndThrow_500 maps to ProviderUnavailable`() {
        val error = assertFailsWith<ProviderException> {
            AnthropicResponseParser.parseErrorAndThrow(500, null)
        }
        assertEquals(ProviderErrorType.ProviderUnavailable, error.classified)
    }
}
