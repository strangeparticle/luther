package com.strangeparticle.luther.core.client.provider.anthropic.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Top-level Anthropic Messages API response DTO.
 * AnthropicResponseParserTest documents the full JSON shapes decoded into this DTO hierarchy.
 */
@Serializable
internal data class AnthropicChatCompletionResponseDto(
    val id: String,
    val content: List<AnthropicResponseContentBlockDto>,
    @SerialName("stop_reason") val stopReason: String? = null,
)
