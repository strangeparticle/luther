package com.strangeparticle.luther.core.session.event

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("ToolApprovalRequestedChatHistoryItem")
internal data class ToolApprovalRequestedChatHistoryItemDebugDto(
    override val itemIndex: Int,
    val toolCallId: String,
) : ChatHistoryItemDebugDto {
    companion object {
        fun from(itemIndex: Int, item: ToolApprovalRequestedChatHistoryItem): ToolApprovalRequestedChatHistoryItemDebugDto =
            ToolApprovalRequestedChatHistoryItemDebugDto(itemIndex = itemIndex, toolCallId = item.toolCallId)
    }
}
