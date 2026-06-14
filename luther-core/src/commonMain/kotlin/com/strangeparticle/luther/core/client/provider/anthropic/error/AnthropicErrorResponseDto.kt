package com.strangeparticle.luther.core.client.provider.anthropic.error

import kotlinx.serialization.Serializable

@Serializable
internal data class AnthropicErrorResponseDto(
    val type: String,
    val error: AnthropicErrorDto,
)
