package com.strangeparticle.luther.core.client.provider.openai.response

import com.strangeparticle.luther.core.client.provider.ChatResponse
import com.strangeparticle.luther.core.client.provider.ProviderErrorType
import com.strangeparticle.luther.core.client.provider.ProviderException
import com.strangeparticle.luther.core.client.provider.StopReason
import com.strangeparticle.luther.core.client.provider.ToolCall
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Parses OpenAI chat-completions DTOs into the provider-neutral [ChatResponse]
 * type. Pure function — no IO. OpenAiResponseParserTest contains full JSON
 * response and error examples for this deserialization boundary.
 *
 * Per spec §3.3.
 */
internal object OpenAiResponseParser {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Parse a successful (HTTP 200) OpenAI response body into a [ChatResponse].
     *
     * The body is `{"choices": [{ "message": { "content": ..., "tool_calls": [...] }, "finish_reason": "..." }], ...}`.
     * Multiple choices are theoretically possible but we only ever request a single
     * choice and consume index 0.
     */
    fun parseSuccess(body: String): ChatResponse {
        val response = try {
            // OpenAiResponseParserTest documents the provider JSON structures decoded into this DTO hierarchy.
            json.decodeFromString<com.strangeparticle.luther.core.client.provider.openai.response.OpenAiChatCompletionResponseDto>(body)
        } catch (e: SerializationException) {
            throw ProviderException(
                ProviderErrorType.MalformedResponse,
                "OpenAI response did not match expected chat-completions shape: ${e.message}",
                rawProviderMessage = body,
                cause = e,
            )
        }
        val firstChoice = response.choices.firstOrNull()
            ?: throw ProviderException(
                ProviderErrorType.MalformedResponse,
                "OpenAI response has empty `choices` array.",
                rawProviderMessage = body,
            )
        val toolCalls = firstChoice.message.toolCalls?.map(::parseToolCall) ?: emptyList()
        return ChatResponse(
            text = firstChoice.message.content,
            toolCalls = toolCalls,
            stopReason = mapStopReason(firstChoice.finishReason),
        )
    }

    /**
     * Map a non-2xx HTTP response to a [ProviderException] and throw. [httpStatus] is the
     * HTTP status code; [body] is the raw response body (may be JSON or plain text).
     * Caller should pass null [body] for transport-level failures.
     *
     * Classification consults the body's `error.code` / `error.type` first so
     * provider-specific cases like `context_length_exceeded` and `insufficient_quota`
     * map to the right [ProviderErrorType] (the HTTP status alone can't distinguish a
     * quota exhaustion from a transient rate limit on 429, or a context-too-large
     * from any other 400). Falls back to HTTP-status-based classification.
     */
    fun parseErrorAndThrow(httpStatus: Int, body: String?): Nothing {
        val openAiError = body?.let { extractOpenAiError(it) }
        val errorClass = openAiError?.let { classifyByOpenAiError(it) }
            ?: classifyHttpStatus(httpStatus)
        val rawProviderMessage = openAiError?.message
            ?: body
        throw ProviderException(
            classified = errorClass,
            message = "OpenAI request failed with HTTP $httpStatus" +
                (rawProviderMessage?.let { ": $it" } ?: ""),
            rawProviderMessage = rawProviderMessage,
        )
    }

    private fun extractOpenAiError(body: String): com.strangeparticle.luther.core.client.provider.openai.error.OpenAiErrorDto? {
        return try {
            // OpenAiResponseParserTest includes complete error-envelope JSON examples for this DTO.
            json.decodeFromString<com.strangeparticle.luther.core.client.provider.openai.error.OpenAiErrorResponseDto>(body).error
        } catch (_: SerializationException) {
            null
        }
    }

    private fun classifyByOpenAiError(error: com.strangeparticle.luther.core.client.provider.openai.error.OpenAiErrorDto): ProviderErrorType? {
        // `code` is the more granular field (e.g. "context_length_exceeded",
        // "insufficient_quota", "rate_limit_exceeded", "invalid_api_key");
        // `type` is the broader category (e.g. "invalid_request_error").
        // Match `code` first.
        when (error.code) {
            "context_length_exceeded" -> return ProviderErrorType.ContextTooLarge
            "insufficient_quota" -> return ProviderErrorType.QuotaExceeded
            "rate_limit_exceeded" -> return ProviderErrorType.RateLimit
            "invalid_api_key", "invalid_token" -> return ProviderErrorType.InvalidApiKey
        }
        when (error.type) {
            "insufficient_quota" -> return ProviderErrorType.QuotaExceeded
            "invalid_request_error" -> {
                // Fall through to HTTP-status-based classification — `invalid_request_error`
                // covers many distinct cases (bad input, missing fields, unsupported model)
                // that we don't have specific [AiErrorClass] entries for.
                return null
            }
        }
        return null
    }

    private fun parseToolCall(toolCall: com.strangeparticle.luther.core.client.provider.openai.response.OpenAiResponseToolCallDto): ToolCall {
        // OpenAI sends arguments as a JSON-encoded string, even though it's structured.
        val argumentsRaw = toolCall.function.arguments
        try {
            json.parseToJsonElement(argumentsRaw) as? JsonObject
                ?: throw ProviderException(
                    ProviderErrorType.MalformedResponse,
                    "OpenAI tool_call arguments are not a JSON object: $argumentsRaw",
                )
        } catch (e: kotlinx.serialization.SerializationException) {
            throw ProviderException(
                ProviderErrorType.MalformedResponse,
                "OpenAI tool_call arguments are not valid JSON: $argumentsRaw",
                rawProviderMessage = argumentsRaw,
                cause = e,
            )
        }
        return ToolCall(
            id = toolCall.id,
            name = toolCall.function.name,
            argumentsJson = argumentsRaw,
        )
    }

    private fun mapStopReason(finishReason: String?): StopReason = when (finishReason) {
        "stop" -> StopReason.Stop
        "tool_calls" -> StopReason.ToolUse
        "length" -> StopReason.MaxTokens
        else -> StopReason.Other
    }

    private fun classifyHttpStatus(status: Int): ProviderErrorType = when (status) {
        401, 403 -> ProviderErrorType.InvalidApiKey
        429 -> ProviderErrorType.RateLimit
        in 500..599 -> ProviderErrorType.ProviderUnavailable
        else -> ProviderErrorType.Unknown
    }

}
