package com.strangeparticle.luther.core.client.provider.anthropic.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class AnthropicToolDto(
    val name: String,
    val description: String,
    @SerialName("input_schema") val inputSchema: JsonObject,
)
