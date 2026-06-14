package com.strangeparticle.luther.core.session.event

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("StateSnapshotAddedChatHistoryItem")
internal data class StateSnapshotAddedChatHistoryItemDebugDto(
    override val itemIndex: Int,
    val snapshotJson: String,
) : ChatHistoryItemDebugDto {
    companion object {
        fun from(itemIndex: Int, item: StateSnapshotAddedChatHistoryItem): StateSnapshotAddedChatHistoryItemDebugDto =
            StateSnapshotAddedChatHistoryItemDebugDto(itemIndex = itemIndex, snapshotJson = item.snapshotJson)
    }
}
