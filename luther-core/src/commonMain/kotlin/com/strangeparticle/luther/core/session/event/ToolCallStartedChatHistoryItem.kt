package com.strangeparticle.luther.core.session.event

import com.strangeparticle.luther.core.client.provider.ToolCall

internal data class ToolCallStartedChatHistoryItem(val toolCall: ToolCall) : ChatHistoryItem
