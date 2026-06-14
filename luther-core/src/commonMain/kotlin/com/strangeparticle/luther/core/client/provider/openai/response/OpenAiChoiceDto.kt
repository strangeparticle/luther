package com.strangeparticle.luther.core.client.provider.openai.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class OpenAiChoiceDto(
    val message: com.strangeparticle.luther.core.client.provider.openai.response.OpenAiResponseMessageDto,
    @SerialName("finish_reason")
    val finishReason: String? = null,
)
