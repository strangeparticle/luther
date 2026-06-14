package com.strangeparticle.luther.core.session.event

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("ToolCallDeniedChatHistoryItem")
internal data class ToolCallDeniedChatHistoryItemDebugDto(
    override val itemIndex: Int,
    val toolCallId: String,
) : ChatHistoryItemDebugDto {
    companion object {
        fun from(itemIndex: Int, item: ToolCallDeniedChatHistoryItem): ToolCallDeniedChatHistoryItemDebugDto =
            ToolCallDeniedChatHistoryItemDebugDto(itemIndex = itemIndex, toolCallId = item.toolCallId)
    }
}
