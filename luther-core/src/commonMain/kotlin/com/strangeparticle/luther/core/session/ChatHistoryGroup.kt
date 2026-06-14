package com.strangeparticle.luther.core.session

import com.strangeparticle.luther.core.session.event.ChatHistoryItem

enum class ChatHistoryGroupType {
    // Currently only holds provider/model change entries, so it's named for that one concrete
    // item. If other session-state-like history items appear (e.g. system prompt or temperature
    // changes), this is the natural place to generalize the group into something broader like
    // SESSION_STATE. Kept specific until that second case actually exists.
    PROVIDER_MODEL_CHANGE,
    AI_INTERACTION,
    LOCAL_COMMAND,
}

data class ChatHistoryGroup(
    val type: ChatHistoryGroupType,
    val items: List<ChatHistoryItem>,
)
