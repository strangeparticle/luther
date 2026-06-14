package com.strangeparticle.luther.core.client.provider.anthropic.error

import kotlinx.serialization.Serializable

@Serializable
internal data class AnthropicErrorDto(
    val type: String,
    val message: String,
)
