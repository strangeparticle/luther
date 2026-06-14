package com.strangeparticle.luther.core.client.provider.anthropic.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonObject

@Serializable
@JsonClassDiscriminator("type")
internal sealed class AnthropicResponseContentBlockDto {

    @Serializable
    @SerialName("text")
    data class Text(val text: String) : AnthropicResponseContentBlockDto()

    @Serializable
    @SerialName("tool_use")
    data class ToolUse(
        val id: String,
        val name: String,
        val input: JsonObject, // opaque args blob — structure is tool-specific, unknown at provider level
    ) : AnthropicResponseContentBlockDto()
}
