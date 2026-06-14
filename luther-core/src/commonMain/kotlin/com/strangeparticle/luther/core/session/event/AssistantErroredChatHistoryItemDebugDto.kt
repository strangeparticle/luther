package com.strangeparticle.luther.core.session.event

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("AssistantErroredChatHistoryItem")
internal data class AssistantErroredChatHistoryItemDebugDto(
    override val itemIndex: Int,
    val message: String,
) : ChatHistoryItemDebugDto {
    companion object {
        fun from(itemIndex: Int, item: AssistantErroredChatHistoryItem): AssistantErroredChatHistoryItemDebugDto =
            AssistantErroredChatHistoryItemDebugDto(itemIndex = itemIndex, message = item.message)
    }
}
