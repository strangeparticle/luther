package com.strangeparticle.luther.core.session.event

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("LocalCommandSubmittedChatHistoryItem")
internal data class LocalCommandSubmittedChatHistoryItemDebugDto(
    override val itemIndex: Int,
    val commandText: String,
    val source: String,
) : ChatHistoryItemDebugDto {
    companion object {
        fun from(itemIndex: Int, item: LocalCommandSubmittedChatHistoryItem): LocalCommandSubmittedChatHistoryItemDebugDto =
            LocalCommandSubmittedChatHistoryItemDebugDto(
                itemIndex = itemIndex,
                commandText = item.commandText,
                source = item.source.name,
            )
    }
}
