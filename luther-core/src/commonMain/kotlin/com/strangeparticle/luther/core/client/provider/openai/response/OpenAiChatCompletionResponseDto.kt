package com.strangeparticle.luther.core.client.provider.openai.response

import kotlinx.serialization.Serializable

@Serializable
internal data class OpenAiChatCompletionResponseDto(
    val choices: List<com.strangeparticle.luther.core.client.provider.openai.response.OpenAiChoiceDto>,
)
