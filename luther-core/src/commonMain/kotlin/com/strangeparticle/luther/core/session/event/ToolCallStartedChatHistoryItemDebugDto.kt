package com.strangeparticle.luther.core.session.event

import com.strangeparticle.luther.core.toolcall.ToolCallDebugDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("ToolCallStartedChatHistoryItem")
internal data class ToolCallStartedChatHistoryItemDebugDto(
    override val itemIndex: Int,
    val toolCall: ToolCallDebugDto,
) : ChatHistoryItemDebugDto {
    companion object {
        fun from(itemIndex: Int, item: ToolCallStartedChatHistoryItem): ToolCallStartedChatHistoryItemDebugDto =
            ToolCallStartedChatHistoryItemDebugDto(
                itemIndex = itemIndex,
                toolCall = ToolCallDebugDto.from(item.toolCall),
            )
    }
}
