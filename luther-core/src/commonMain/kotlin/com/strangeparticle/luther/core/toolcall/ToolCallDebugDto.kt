package com.strangeparticle.luther.core.toolcall

import com.strangeparticle.luther.core.client.provider.ToolCall
import kotlinx.serialization.Serializable

@Serializable
internal data class ToolCallDebugDto(
    val toolCallId: String,
    val toolName: String,
    val argumentsAsJsonString: String,
) {
    companion object {
        fun from(toolCall: ToolCall): ToolCallDebugDto = ToolCallDebugDto(
            toolCallId = toolCall.id,
            toolName = toolCall.name,
            argumentsAsJsonString = toolCall.argumentsJson,
        )
    }
}
