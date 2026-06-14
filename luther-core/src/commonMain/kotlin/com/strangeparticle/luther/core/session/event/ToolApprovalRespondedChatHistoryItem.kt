package com.strangeparticle.luther.core.session.event

internal data class ToolApprovalRespondedChatHistoryItem(
    val toolCallId: String,
    val approved: Boolean,
) : ChatHistoryItem
