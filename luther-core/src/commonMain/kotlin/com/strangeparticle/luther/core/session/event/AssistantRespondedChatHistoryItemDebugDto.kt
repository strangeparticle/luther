package com.strangeparticle.luther.core.session.event

import com.strangeparticle.luther.core.toolcall.ToolCallDebugDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("AssistantRespondedChatHistoryItem")
internal data class AssistantRespondedChatHistoryItemDebugDto(
    override val itemIndex: Int,
    val text: String?,
    val toolCalls: List<ToolCallDebugDto>,
) : ChatHistoryItemDebugDto {
    companion object {
        fun from(itemIndex: Int, item: AssistantRespondedChatHistoryItem): AssistantRespondedChatHistoryItemDebugDto =
            AssistantRespondedChatHistoryItemDebugDto(
                itemIndex = itemIndex,
                text = item.text,
                toolCalls = item.toolCalls.map(ToolCallDebugDto::from),
            )
    }
}
