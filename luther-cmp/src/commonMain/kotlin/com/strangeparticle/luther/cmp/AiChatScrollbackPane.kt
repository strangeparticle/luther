package com.strangeparticle.luther.cmp

import com.strangeparticle.luther.core.session.ChatHistoryGroup
import com.strangeparticle.luther.core.session.ChatHistoryGroupType
import com.strangeparticle.luther.core.session.ChatMessagePart
import com.strangeparticle.luther.core.session.ToolCallState
import com.strangeparticle.luther.core.session.event.LocalCommandRespondedChatHistoryItem
import com.strangeparticle.luther.core.session.event.LocalCommandResponseKind
import com.strangeparticle.luther.core.session.event.LocalCommandSource
import com.strangeparticle.luther.core.session.event.LocalCommandSubmittedChatHistoryItem
import com.strangeparticle.luther.core.session.event.ProviderModelChangedChatHistoryItem
import com.strangeparticle.luther.core.session.projection.buildProviderHistory
import com.strangeparticle.luther.core.session.projection.buildTranscriptParts
import com.strangeparticle.luther.core.client.provider.ChatMessage
import com.strangeparticle.luther.core.client.provider.ToolCall

internal sealed class AiChatScrollbackPane {
    data class ProviderModelChange(
        val text: String,
    ) : AiChatScrollbackPane()

    data class LocalCommand(
        val commandText: String,
        val commandAttribution: CommandAttribution,
        val responseText: String,
        val style: LocalCommandResponseStyle,
    ) : AiChatScrollbackPane()

    data class Interaction(
        val requestText: String,
        val responseParts: List<ChatMessagePart>,
        val transcriptStartIndex: Int? = null,
    ) : AiChatScrollbackPane()

    // Debug-only panes used when SHOW_FULL_CHAT_TRANSCRIPT is on. Each represents
    // exactly one AiConversationMessage from AiSessionManager.history so the developer
    // can see every payload exchanged with the model — including the state
    // snapshots and raw tool-result payloads the normal Interaction view hides.

    data class DebugUserMessage(
        val text: String,
        val historyIndex: Int,
    ) : AiChatScrollbackPane()

    data class DebugStateSnapshot(
        val snapshotJson: String,
        val historyIndex: Int,
    ) : AiChatScrollbackPane()

    data class DebugAssistantMessage(
        val text: String?,
        val toolCalls: List<ToolCall>,
        val historyIndex: Int,
    ) : AiChatScrollbackPane()

    data class DebugToolResult(
        val toolCallId: String,
        val content: String,
        val historyIndex: Int,
    ) : AiChatScrollbackPane()

    data class DebugProviderModelChange(
        val text: String,
        val historyIndex: Int,
    ) : AiChatScrollbackPane()
}

internal enum class CommandAttribution {
    System,
    User,
}

internal enum class LocalCommandResponseStyle {
    Help,
    Error,
}

internal fun initialTerseHelpScrollbackPane(terseHelpText: String): AiChatScrollbackPane.LocalCommand = AiChatScrollbackPane.LocalCommand(
    commandText = "/help_terse",
    commandAttribution = CommandAttribution.System,
    responseText = terseHelpText,
    style = LocalCommandResponseStyle.Help,
)

internal fun buildSlimScrollbackPanes(groups: List<ChatHistoryGroup>, terseHelpText: String = ""): List<AiChatScrollbackPane> {
    val panes = mutableListOf<AiChatScrollbackPane>()
    for (group in groups) {
        when (group.type) {
            ChatHistoryGroupType.PROVIDER_MODEL_CHANGE -> {
                group.items.filterIsInstance<ProviderModelChangedChatHistoryItem>().forEach { item ->
                    panes += AiChatScrollbackPane.ProviderModelChange(item.displayText)
                }
            }
            ChatHistoryGroupType.LOCAL_COMMAND -> {
                val submitted = group.items.filterIsInstance<LocalCommandSubmittedChatHistoryItem>().firstOrNull()
                val responded = group.items.filterIsInstance<LocalCommandRespondedChatHistoryItem>().firstOrNull()
                if (responded != null) {
                    panes += AiChatScrollbackPane.LocalCommand(
                        commandText = responded.commandText,
                        commandAttribution = submitted?.source.toCommandAttribution(),
                        responseText = responded.responseText,
                        style = responded.kind.toLocalCommandResponseStyle(),
                    )
                }
            }
            ChatHistoryGroupType.AI_INTERACTION -> {
                val transcriptParts = buildTranscriptParts(group.items)
                val userText = transcriptParts.filterIsInstance<ChatMessagePart.UserText>().firstOrNull() ?: continue
                val responseParts = transcriptParts.filter { it !is ChatMessagePart.UserText }
                panes += AiChatScrollbackPane.Interaction(
                    requestText = userText.text,
                    responseParts = responseParts,
                )
            }
        }
    }
    return panes.ifEmpty { listOf(initialTerseHelpScrollbackPane(terseHelpText)) }
}

internal fun buildDebugScrollbackPanes(groups: List<ChatHistoryGroup>): List<AiChatScrollbackPane> {
    val panes = mutableListOf<AiChatScrollbackPane>()
    var paneIndex = 0
    groups.flatMap { it.items }.forEach { item ->
        if (item is ProviderModelChangedChatHistoryItem) {
            panes += AiChatScrollbackPane.DebugProviderModelChange(item.displayText, paneIndex)
            paneIndex += 1
            return@forEach
        }
        for (message in buildProviderHistory(listOf(item))) {
            panes += when (message) {
                is ChatMessage.User -> AiChatScrollbackPane.DebugUserMessage(message.text, paneIndex)
                is ChatMessage.SystemState -> AiChatScrollbackPane.DebugStateSnapshot(message.snapshotJson, paneIndex)
                is ChatMessage.Assistant -> AiChatScrollbackPane.DebugAssistantMessage(message.text, message.toolCalls, paneIndex)
                is ChatMessage.ToolResult -> AiChatScrollbackPane.DebugToolResult(message.toolCallId, message.content, paneIndex)
            }
            paneIndex += 1
        }
    }
    return panes
}

private fun LocalCommandSource?.toCommandAttribution(): CommandAttribution = when (this) {
    LocalCommandSource.System -> CommandAttribution.System
    LocalCommandSource.User,
    null -> CommandAttribution.User
}

private fun LocalCommandResponseKind.toLocalCommandResponseStyle(): LocalCommandResponseStyle = when (this) {
    LocalCommandResponseKind.Help -> LocalCommandResponseStyle.Help
    LocalCommandResponseKind.Error -> LocalCommandResponseStyle.Error
}

/**
 * Plain-English title shown both at the top of each debug pane (UI) and as the
 * first line of its copy-to-clipboard payload. Kept in one place so the two
 * stay in sync.
 */
/**
 * Plain-English title shown both at the top of each debug pane (UI) and as the
 * first line of its copy-to-clipboard payload. Kept in one place so the two
 * stay in sync. Non-debug pane variants don't have a title bar — they pre-date
 * this view and label themselves inline.
 */
internal fun debugPaneTitle(pane: AiChatScrollbackPane): String = when (pane) {
    is AiChatScrollbackPane.DebugProviderModelChange -> "Provider/model"
    is AiChatScrollbackPane.DebugUserMessage -> "Your message"
    is AiChatScrollbackPane.DebugStateSnapshot -> "Application state sent to model"
    is AiChatScrollbackPane.DebugAssistantMessage -> "Assistant reply"
    is AiChatScrollbackPane.DebugToolResult -> "Tool result returned to model — id=${pane.toolCallId}"
    is AiChatScrollbackPane.ProviderModelChange,
    is AiChatScrollbackPane.LocalCommand,
    is AiChatScrollbackPane.Interaction -> ""
}

internal fun getScrollbackPaneTextForCopyToClipboard(pane: AiChatScrollbackPane): String = when (pane) {
    is AiChatScrollbackPane.ProviderModelChange -> pane.text
    is AiChatScrollbackPane.LocalCommand -> listOf(
        formatCommandForCopyToClipboard(pane),
        pane.responseText,
    ).joinToString("\n\n")
    is AiChatScrollbackPane.Interaction -> listOf(
        "You: ${pane.requestText}",
        pane.responseParts.flatMap(::formatAiResponsePartForCopyToClipboard).joinToString("\n"),
    ).filter { it.isNotBlank() }.joinToString("\n\n")
    is AiChatScrollbackPane.DebugUserMessage -> "${debugPaneTitle(pane)}:\n${pane.text}"
    is AiChatScrollbackPane.DebugStateSnapshot -> "${debugPaneTitle(pane)}:\n${pane.snapshotJson}"
    is AiChatScrollbackPane.DebugAssistantMessage -> buildString {
        append(debugPaneTitle(pane))
        append(":")
        if (!pane.text.isNullOrEmpty()) {
            append("\n")
            append(pane.text)
        }
        for (toolCall in pane.toolCalls) {
            append("\n\nTool call: ${toolCall.name}\n")
            append(toolCall.argumentsJson)
        }
    }
    is AiChatScrollbackPane.DebugToolResult -> "${debugPaneTitle(pane)}:\n${pane.content}"
    is AiChatScrollbackPane.DebugProviderModelChange -> "${debugPaneTitle(pane)}:\n${pane.text}"
}

internal fun getAllScrollbackTextForCopyToClipboard(panes: List<AiChatScrollbackPane>): String = panes
    .map(::getScrollbackPaneTextForCopyToClipboard)
    .filter { it.isNotBlank() }
    .joinToString("\n\n")

private fun formatCommandForCopyToClipboard(pane: AiChatScrollbackPane.LocalCommand): String = when (pane.commandAttribution) {
    CommandAttribution.System -> pane.commandText
    CommandAttribution.User -> "You: ${pane.commandText}"
}

internal fun formatAiResponsePartForCopyToClipboard(part: ChatMessagePart): List<String> = when (part) {
    is ChatMessagePart.UserText -> listOf("You: ${part.text}")
    is ChatMessagePart.AssistantText -> listOf("Assistant: ${part.text}")
    is ChatMessagePart.ChatError -> listOf("Error: ${part.message}")
    is ChatMessagePart.ToolCall -> formatToolCallForCopyToClipboard(part)
}

internal fun formatToolCallForCopyToClipboard(part: ChatMessagePart.ToolCall): List<String> = when (val state = part.state) {
    ToolCallState.Pending -> listOf("Working")
    ToolCallState.ApprovalRequested -> listOf("Approval requested")
    is ToolCallState.ApprovalResponded -> listOf(if (state.approved) "Approval granted" else "Approval denied")
    is ToolCallState.OutputAvailable -> emptyList()
    is ToolCallState.OutputError -> listOf("Error: ${state.message}")
    ToolCallState.OutputDenied -> listOf("Denied")
}
