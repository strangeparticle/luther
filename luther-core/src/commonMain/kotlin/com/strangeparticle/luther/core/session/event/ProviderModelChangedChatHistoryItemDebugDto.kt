package com.strangeparticle.luther.core.session.event

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("ProviderModelChangedChatHistoryItem")
internal data class ProviderModelChangedChatHistoryItemDebugDto(
    override val itemIndex: Int,
    val providerLabel: String,
    val modelLabel: String,
    val displayText: String,
) : ChatHistoryItemDebugDto {
    companion object {
        fun from(itemIndex: Int, item: ProviderModelChangedChatHistoryItem): ProviderModelChangedChatHistoryItemDebugDto =
            ProviderModelChangedChatHistoryItemDebugDto(
                itemIndex = itemIndex,
                providerLabel = item.providerLabel,
                modelLabel = item.modelLabel,
                displayText = item.displayText,
            )
    }
}
