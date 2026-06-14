package com.strangeparticle.luther.core.client.provider

/** Provider-neutral conversation message. Pairs with [ProviderConfig] at the call site. */
sealed interface ChatMessage {
    data class User(val text: String) : ChatMessage
    data class Assistant(val text: String?, val toolCalls: List<ToolCall>) : ChatMessage
    data class ToolResult(val toolCallId: String, val content: String) : ChatMessage
    data class SystemState(val snapshotJson: String) : ChatMessage
}
