package com.strangeparticle.luther.core.session.event

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("ToolCallCompletedChatHistoryItem")
internal data class ToolCallCompletedChatHistoryItemDebugDto(
    override val itemIndex: Int,
    val toolCallId: String,
    val providerContent: String,
    val transcriptOutput: String,
    val endsTurn: Boolean,
) : ChatHistoryItemDebugDto {
    companion object {
        fun from(itemIndex: Int, item: ToolCallCompletedChatHistoryItem): ToolCallCompletedChatHistoryItemDebugDto =
            ToolCallCompletedChatHistoryItemDebugDto(
                itemIndex = itemIndex,
                toolCallId = item.toolCallId,
                providerContent = item.providerContent,
                transcriptOutput = item.transcriptOutput,
                endsTurn = item.endsTurn,
            )
    }
}
