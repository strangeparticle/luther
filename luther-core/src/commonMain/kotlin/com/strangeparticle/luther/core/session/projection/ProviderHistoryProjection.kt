package com.strangeparticle.luther.core.session.projection

import com.strangeparticle.luther.core.client.provider.ChatMessage
import com.strangeparticle.luther.core.session.event.ChatHistoryItem
import com.strangeparticle.luther.core.session.event.AssistantErroredChatHistoryItem
import com.strangeparticle.luther.core.session.event.AssistantRespondedChatHistoryItem
import com.strangeparticle.luther.core.session.event.LocalCommandRespondedChatHistoryItem
import com.strangeparticle.luther.core.session.event.LocalCommandSubmittedChatHistoryItem
import com.strangeparticle.luther.core.session.event.ProviderModelChangedChatHistoryItem
import com.strangeparticle.luther.core.session.event.StateSnapshotAddedChatHistoryItem
import com.strangeparticle.luther.core.session.event.ToolApprovalRequestedChatHistoryItem
import com.strangeparticle.luther.core.session.event.ToolApprovalRespondedChatHistoryItem
import com.strangeparticle.luther.core.session.event.ToolCallCompletedChatHistoryItem
import com.strangeparticle.luther.core.session.event.ToolCallDeniedChatHistoryItem
import com.strangeparticle.luther.core.session.event.ToolCallFailedChatHistoryItem
import com.strangeparticle.luther.core.session.event.ToolCallStartedChatHistoryItem
import com.strangeparticle.luther.core.session.event.UserSubmittedChatHistoryItem

fun buildProviderHistory(events: List<ChatHistoryItem>): List<ChatMessage> = events.mapNotNull { event ->
    when (event) {
        is StateSnapshotAddedChatHistoryItem -> ChatMessage.SystemState(event.snapshotJson)
        is UserSubmittedChatHistoryItem -> ChatMessage.User(event.text)
        is AssistantRespondedChatHistoryItem -> ChatMessage.Assistant(event.text, event.toolCalls)
        is ToolCallCompletedChatHistoryItem -> ChatMessage.ToolResult(event.toolCallId, event.providerContent)
        is ToolCallFailedChatHistoryItem -> ChatMessage.ToolResult(event.toolCallId, event.providerContent)
        is AssistantErroredChatHistoryItem,
        is LocalCommandRespondedChatHistoryItem,
        is LocalCommandSubmittedChatHistoryItem,
        is ProviderModelChangedChatHistoryItem,
        is ToolApprovalRequestedChatHistoryItem,
        is ToolApprovalRespondedChatHistoryItem,
        is ToolCallDeniedChatHistoryItem,
        is ToolCallStartedChatHistoryItem -> null
    }
}
