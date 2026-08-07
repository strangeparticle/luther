package com.strangeparticle.luther.core.client.provider.anthropic.response

import com.strangeparticle.luther.core.client.provider.ChatResponse
import com.strangeparticle.luther.core.client.provider.ProviderErrorType
import com.strangeparticle.luther.core.client.provider.ProviderException
import com.strangeparticle.luther.core.client.provider.StopReason
import com.strangeparticle.luther.core.client.provider.anthropic.error.AnthropicErrorResponseDto
import com.strangeparticle.luther.core.client.provider.ToolCall
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Parses Anthropic Messages API DTOs into the provider-neutral [ChatResponse] type.
 * Pure function — no IO. AnthropicResponseParserTest contains full JSON response and
 * error examples for this deserialization boundary.
 */
internal object AnthropicResponseParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parseSuccess(body: String): ChatResponse {
        val response = try {
            json.decodeFromString<AnthropicChatCompletionResponseDto>(body)
        } catch (e: SerializationException) {
            throw ProviderException(
                ProviderErrorType.MalformedResponse,
                "Anthropic response did not match expected messages shape: ${e.message}",
                rawProviderMessage = body,
                cause = e,
            )
        }

        val text = response.content
            .filterIsInstance<AnthropicResponseContentBlockDto.Text>()
            .joinToString("") { it.text }
            .takeIf { it.isNotEmpty() }

        val toolCalls = response.content
            .filterIsInstance<AnthropicResponseContentBlockDto.ToolUse>()
            .map { block ->
                ToolCall(
                    id = block.id,
                    name = block.name,
                    argumentsJson = json.encodeToString(JsonObject.serializer(), block.input),
                )
            }

        return ChatResponse(
            text = text,
            toolCalls = toolCalls,
            stopReason = mapStopReason(response.stopReason),
        )
    }

    fun classifyError(httpStatus: Int, body: String?): ProviderException {
        val anthropicError = body?.let { extractAnthropicError(it) }
        val errorType = anthropicError?.let { classifyByAnthropicError(it.type, it.message) }
            ?: classifyHttpStatus(httpStatus)
        val rawProviderMessage = anthropicError?.message ?: body
        return ProviderException(
            classified = errorType,
            message = "Anthropic request failed with HTTP $httpStatus" +
                (rawProviderMessage?.let { ": $it" } ?: ""),
            rawProviderMessage = rawProviderMessage,
        )
    }

    fun parseErrorAndThrow(httpStatus: Int, body: String?): Nothing {
        throw classifyError(httpStatus, body)
    }

    fun parseModelListResponse(body: String): JsonObject = parseRawJsonObjectOrThrow(body)

    private fun extractAnthropicError(body: String) = try {
        json.decodeFromString<AnthropicErrorResponseDto>(body).error
    } catch (_: SerializationException) {
        null
    }

    private fun classifyByAnthropicError(type: String, message: String): ProviderErrorType = when (type) {
        "authentication_error", "permission_error" -> ProviderErrorType.InvalidApiKey
        "rate_limit_error" -> ProviderErrorType.RateLimit
        "api_error", "overloaded_error" -> ProviderErrorType.ProviderUnavailable
        "invalid_request_error" -> {
            val lower = message.lowercase()
            if ("context" in lower || "token" in lower) ProviderErrorType.ContextTooLarge
            else ProviderErrorType.Unknown
        }
        else -> ProviderErrorType.Unknown
    }

    private fun mapStopReason(stopReason: String?): StopReason = when (stopReason) {
        "end_turn", "stop_sequence" -> StopReason.Stop
        "tool_use" -> StopReason.ToolUse
        "max_tokens" -> StopReason.MaxTokens
        else -> StopReason.Other
    }

    private fun classifyHttpStatus(status: Int): ProviderErrorType = when (status) {
        401, 403 -> ProviderErrorType.InvalidApiKey
        429 -> ProviderErrorType.RateLimit
        529 -> ProviderErrorType.ProviderUnavailable
        in 500..599 -> ProviderErrorType.ProviderUnavailable
        else -> ProviderErrorType.Unknown
    }

    private fun parseRawJsonObjectOrThrow(body: String): JsonObject = try {
        json.parseToJsonElement(body) as? JsonObject
            ?: throw ProviderException(
                ProviderErrorType.MalformedResponse,
                "Anthropic response was not a JSON object.",
                rawProviderMessage = body,
            )
    } catch (e: SerializationException) {
        throw ProviderException(
            ProviderErrorType.MalformedResponse,
            "Anthropic response was not valid JSON: ${e.message}",
            rawProviderMessage = body,
            cause = e,
        )
    }
}
