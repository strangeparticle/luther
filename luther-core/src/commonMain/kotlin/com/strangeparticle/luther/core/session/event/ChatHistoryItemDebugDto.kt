package com.strangeparticle.luther.core.session.event

import kotlinx.serialization.Serializable

@Serializable
sealed interface ChatHistoryItemDebugDto {
    val itemIndex: Int
}

fun ChatHistoryItem.toDebugDto(itemIndex: Int): ChatHistoryItemDebugDto = when (this) {
    is AssistantErroredChatHistoryItem -> AssistantErroredChatHistoryItemDebugDto.from(itemIndex, this)
    is AssistantRespondedChatHistoryItem -> AssistantRespondedChatHistoryItemDebugDto.from(itemIndex, this)
    is LocalCommandRespondedChatHistoryItem -> LocalCommandRespondedChatHistoryItemDebugDto.from(itemIndex, this)
    is LocalCommandSubmittedChatHistoryItem -> LocalCommandSubmittedChatHistoryItemDebugDto.from(itemIndex, this)
    is ProviderModelChangedChatHistoryItem -> ProviderModelChangedChatHistoryItemDebugDto.from(itemIndex, this)
    is StateSnapshotAddedChatHistoryItem -> StateSnapshotAddedChatHistoryItemDebugDto.from(itemIndex, this)
    is ToolApprovalRequestedChatHistoryItem -> ToolApprovalRequestedChatHistoryItemDebugDto.from(itemIndex, this)
    is ToolApprovalRespondedChatHistoryItem -> ToolApprovalRespondedChatHistoryItemDebugDto.from(itemIndex, this)
    is ToolCallCompletedChatHistoryItem -> ToolCallCompletedChatHistoryItemDebugDto.from(itemIndex, this)
    is ToolCallDeniedChatHistoryItem -> ToolCallDeniedChatHistoryItemDebugDto.from(itemIndex, this)
    is ToolCallFailedChatHistoryItem -> ToolCallFailedChatHistoryItemDebugDto.from(itemIndex, this)
    is ToolCallStartedChatHistoryItem -> ToolCallStartedChatHistoryItemDebugDto.from(itemIndex, this)
    is UserSubmittedChatHistoryItem -> UserSubmittedChatHistoryItemDebugDto.from(itemIndex, this)
}
