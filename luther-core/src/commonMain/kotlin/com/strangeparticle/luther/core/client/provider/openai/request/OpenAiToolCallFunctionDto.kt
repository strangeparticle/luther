package com.strangeparticle.luther.core.client.provider.openai.request

import kotlinx.serialization.Serializable

@Serializable
internal data class OpenAiToolCallFunctionDto(
    val name: String,
    val arguments: String,
)
