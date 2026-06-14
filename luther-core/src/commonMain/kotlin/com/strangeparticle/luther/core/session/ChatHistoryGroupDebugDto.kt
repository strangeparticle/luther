package com.strangeparticle.luther.core.session

import com.strangeparticle.luther.core.session.event.toDebugDto
import kotlinx.serialization.Serializable

@Serializable
internal data class ChatHistoryGroupDebugDto(
    val groupIndex: Int,
    val type: String,
    val items: List<com.strangeparticle.luther.core.session.event.ChatHistoryItemDebugDto>,
) {
    companion object {
        fun from(groupIndex: Int, group: ChatHistoryGroup): ChatHistoryGroupDebugDto = ChatHistoryGroupDebugDto(
            groupIndex = groupIndex,
            type = group.type.name,
            items = group.items.mapIndexed { itemIndex, item -> item.toDebugDto(itemIndex) },
        )
    }
}
