package com.strangeparticle.luther.core.session.event

import com.strangeparticle.luther.core.client.provider.ToolCall

internal data class AssistantRespondedChatHistoryItem(
    val text: String?,
    val toolCalls: List<ToolCall>,
) : ChatHistoryItem
