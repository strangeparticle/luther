package com.strangeparticle.luther.core.session.projection

import com.strangeparticle.luther.core.session.ChatMessagePart
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

fun buildTranscriptParts(events: List<ChatHistoryItem>): List<ChatMessagePart> {
    val parts = mutableListOf<ChatMessagePart>()
    val toolPartIndices = mutableMapOf<String, Int>()

    fun updateToolPart(toolCallId: String, state: ToolCallState) {
        val index = toolPartIndices[toolCallId] ?: return
        val existing = parts[index] as? ChatMessagePart.ToolCall ?: return
        parts[index] = existing.copy(state = state)
    }

    for (event in events) {
        when (event) {
            is UserSubmittedChatHistoryItem -> parts += ChatMessagePart.UserText(event.text)
            is AssistantRespondedChatHistoryItem -> event.text?.let { parts += ChatMessagePart.AssistantText(it) }
            is AssistantErroredChatHistoryItem -> parts += ChatMessagePart.ChatError(event.message)
            is ToolCallStartedChatHistoryItem -> {
                toolPartIndices[event.toolCall.id] = parts.size
                parts += ChatMessagePart.ToolCall(event.toolCall, ToolCallState.Pending)
            }
            is ToolApprovalRequestedChatHistoryItem -> updateToolPart(event.toolCallId, ToolCallState.ApprovalRequested)
            is ToolApprovalRespondedChatHistoryItem -> updateToolPart(event.toolCallId, ToolCallState.ApprovalResponded(event.approved))
            is ToolCallCompletedChatHistoryItem -> {
                if (event.endsTurn) {
                    val index = toolPartIndices[event.toolCallId]
                    if (index != null) {
                        parts[index] = ChatMessagePart.AssistantText(event.transcriptOutput)
                    }
                } else {
                    updateToolPart(event.toolCallId, ToolCallState.OutputAvailable(event.transcriptOutput))
                }
            }
            is ToolCallFailedChatHistoryItem -> updateToolPart(event.toolCallId, ToolCallState.OutputError(event.message))
            is ToolCallDeniedChatHistoryItem -> updateToolPart(event.toolCallId, ToolCallState.OutputDenied)
            is LocalCommandRespondedChatHistoryItem,
            is LocalCommandSubmittedChatHistoryItem,
            is ProviderModelChangedChatHistoryItem,
            is StateSnapshotAddedChatHistoryItem -> Unit
        }
    }
    return parts
}
