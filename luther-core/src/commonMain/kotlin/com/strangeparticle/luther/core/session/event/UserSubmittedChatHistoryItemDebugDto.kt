package com.strangeparticle.luther.core.session.event

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("UserSubmittedChatHistoryItem")
internal data class UserSubmittedChatHistoryItemDebugDto(
    override val itemIndex: Int,
    val text: String,
) : ChatHistoryItemDebugDto {
    companion object {
        fun from(itemIndex: Int, item: UserSubmittedChatHistoryItem): UserSubmittedChatHistoryItemDebugDto =
            UserSubmittedChatHistoryItemDebugDto(itemIndex = itemIndex, text = item.text)
    }
}
