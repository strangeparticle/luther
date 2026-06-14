package com.strangeparticle.luther.core.client.provider.anthropic.request

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * A single message in an Anthropic request. [content] is [kotlinx.serialization.json.JsonPrimitive]
 * for a plain-text turn or [kotlinx.serialization.json.JsonArray] of typed content blocks when the
 * turn contains tool use, tool results, or mixed content. Anthropic's message content field is
 * genuinely polymorphic at the wire level, making JsonElement the appropriate type here.
 */
@Serializable
internal data class AnthropicMessageDto(
    val role: String,
    val content: JsonElement,
)
