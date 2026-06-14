package com.strangeparticle.luther.core.client.provider.openai.request

import kotlinx.serialization.Serializable

@Serializable
internal data class OpenAiToolCallDto(
    val id: String,
    val type: String,
    val function: com.strangeparticle.luther.core.client.provider.openai.request.OpenAiToolCallFunctionDto,
)
