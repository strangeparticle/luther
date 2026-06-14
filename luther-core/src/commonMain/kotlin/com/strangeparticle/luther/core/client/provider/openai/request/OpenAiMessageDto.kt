package com.strangeparticle.luther.core.client.provider.openai.request

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
internal data class OpenAiMessageDto(
    val role: String,
    val content: JsonElement? = null,
    @SerialName("tool_calls")
    val toolCalls: List<com.strangeparticle.luther.core.client.provider.openai.request.OpenAiToolCallDto>? = null,
    @SerialName("tool_call_id")
    val toolCallId: String? = null,
)
