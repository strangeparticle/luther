package com.strangeparticle.luther.core.session.projection

import com.strangeparticle.luther.core.session.ToolCallState
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

internal fun buildToolCallStates(events: List<ChatHistoryItem>): Map<String, ToolCallState> {
    val states = mutableMapOf<String, ToolCallState>()
    for (event in events) {
        when (event) {
            is ToolCallStartedChatHistoryItem -> states[event.toolCall.id] = ToolCallState.Pending
            is ToolApprovalRequestedChatHistoryItem -> states[event.toolCallId] = ToolCallState.ApprovalRequested
            is ToolApprovalRespondedChatHistoryItem -> states[event.toolCallId] = ToolCallState.ApprovalResponded(event.approved)
            is ToolCallCompletedChatHistoryItem -> states[event.toolCallId] = if (event.endsTurn) {
                ToolCallState.OutputDenied
            } else {
                ToolCallState.OutputAvailable(event.transcriptOutput)
            }
            is ToolCallFailedChatHistoryItem -> states[event.toolCallId] = ToolCallState.OutputError(event.message)
            is ToolCallDeniedChatHistoryItem -> states[event.toolCallId] = ToolCallState.OutputDenied
            is AssistantErroredChatHistoryItem,
            is AssistantRespondedChatHistoryItem,
            is LocalCommandRespondedChatHistoryItem,
            is LocalCommandSubmittedChatHistoryItem,
            is ProviderModelChangedChatHistoryItem,
            is StateSnapshotAddedChatHistoryItem,
            is UserSubmittedChatHistoryItem -> Unit
        }
    }
    return states
}
