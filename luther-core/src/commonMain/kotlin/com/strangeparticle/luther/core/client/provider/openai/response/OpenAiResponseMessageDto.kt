package com.strangeparticle.luther.core.client.provider.openai.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class OpenAiResponseMessageDto(
    val role: String? = null,
    val content: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<com.strangeparticle.luther.core.client.provider.openai.response.OpenAiResponseToolCallDto>? = null,
)
