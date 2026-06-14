package com.strangeparticle.luther.core.session.event

internal data class ToolCallFailedChatHistoryItem(
    val toolCallId: String,
    val providerContent: String,
    val message: String,
) : ChatHistoryItem
