package com.strangeparticle.luther.core.session.event

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("ToolCallFailedChatHistoryItem")
internal data class ToolCallFailedChatHistoryItemDebugDto(
    override val itemIndex: Int,
    val toolCallId: String,
    val providerContent: String,
    val message: String,
) : ChatHistoryItemDebugDto {
    companion object {
        fun from(itemIndex: Int, item: ToolCallFailedChatHistoryItem): ToolCallFailedChatHistoryItemDebugDto =
            ToolCallFailedChatHistoryItemDebugDto(
                itemIndex = itemIndex,
                toolCallId = item.toolCallId,
                providerContent = item.providerContent,
                message = item.message,
            )
    }
}
