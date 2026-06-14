package com.strangeparticle.luther.core.session.event

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("ToolApprovalRespondedChatHistoryItem")
internal data class ToolApprovalRespondedChatHistoryItemDebugDto(
    override val itemIndex: Int,
    val toolCallId: String,
    val approved: Boolean,
) : ChatHistoryItemDebugDto {
    companion object {
        fun from(itemIndex: Int, item: ToolApprovalRespondedChatHistoryItem): ToolApprovalRespondedChatHistoryItemDebugDto =
            ToolApprovalRespondedChatHistoryItemDebugDto(itemIndex = itemIndex, toolCallId = item.toolCallId, approved = item.approved)
    }
}
