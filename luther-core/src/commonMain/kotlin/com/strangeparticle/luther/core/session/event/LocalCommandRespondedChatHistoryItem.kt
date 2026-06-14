package com.strangeparticle.luther.core.session.event

data class LocalCommandRespondedChatHistoryItem(
    val commandText: String,
    val responseText: String,
    val kind: LocalCommandResponseKind,
) : ChatHistoryItem
