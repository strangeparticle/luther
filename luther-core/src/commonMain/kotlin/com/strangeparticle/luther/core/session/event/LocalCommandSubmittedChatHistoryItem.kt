package com.strangeparticle.luther.core.session.event

data class LocalCommandSubmittedChatHistoryItem(
    val commandText: String,
    val source: LocalCommandSource,
) : ChatHistoryItem
