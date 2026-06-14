package com.strangeparticle.luther.core.client.provider.openai.response

import kotlinx.serialization.Serializable

@Serializable
internal data class OpenAiResponseToolCallFunctionDto(
    val name: String,
    val arguments: String = "{}",
)
