package com.strangeparticle.luther.core.session.event

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("LocalCommandRespondedChatHistoryItem")
internal data class LocalCommandRespondedChatHistoryItemDebugDto(
    override val itemIndex: Int,
    val commandText: String,
    val responseText: String,
    val responseKind: String,
) : ChatHistoryItemDebugDto {
    companion object {
        fun from(itemIndex: Int, item: LocalCommandRespondedChatHistoryItem): LocalCommandRespondedChatHistoryItemDebugDto =
            LocalCommandRespondedChatHistoryItemDebugDto(
                itemIndex = itemIndex,
                commandText = item.commandText,
                responseText = item.responseText,
                responseKind = item.kind.name,
            )
    }
}
