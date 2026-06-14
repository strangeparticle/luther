package com.strangeparticle.luther.cmp

import com.strangeparticle.luther.core.session.ChatMessagePart

// luther-cmp does not own help content — the chat pane takes it as a parameter, so the suite
// supplies a small fixture where springboard would inject its own help text.
internal object AiChatTestHelpText {
    val terse = "Terse help line one.\nTerse help line two."
    const val fullTitle = "Full Help"
    val full = "$fullTitle\nFull help body."
}

// Builds a configured AiChatPaneState with sensible OpenAI/gpt-5 defaults so each test overrides
// only the slice it exercises. Mirrors the production "configured" shape.
internal fun configuredState(
    isRunning: Boolean = false,
    transcriptParts: List<ChatMessagePart> = emptyList(),
    scrollbackPanes: List<AiChatScrollbackPane>? = null,
    modelLabel: String = "gpt-5",
    modelPicker: AiChatPaneModelPickerState? = null,
    debugChatHistoryText: String = "",
    onSubmit: (String) -> Unit = {},
    onStop: () -> Unit = {},
    onProcessingFocusFallback: () -> Unit = {},
): AiChatPaneState = if (scrollbackPanes == null) {
    AiChatPaneState.configured(
        providerLabel = "OpenAI",
        modelLabel = modelLabel,
        modelPicker = modelPicker,
        transcriptParts = transcriptParts,
        terseHelpText = AiChatTestHelpText.terse,
        debugChatHistoryText = debugChatHistoryText,
        isRunning = isRunning,
        onSubmit = onSubmit,
        onStop = onStop,
        onApprovalDecision = { _, _ -> },
        onProcessingFocusFallback = onProcessingFocusFallback,
    )
} else {
    AiChatPaneState.configured(
        providerLabel = "OpenAI",
        modelLabel = modelLabel,
        modelPicker = modelPicker,
        transcriptParts = transcriptParts,
        scrollbackPanes = scrollbackPanes,
        debugChatHistoryText = debugChatHistoryText,
        isRunning = isRunning,
        onSubmit = onSubmit,
        onStop = onStop,
        onApprovalDecision = { _, _ -> },
        onProcessingFocusFallback = onProcessingFocusFallback,
    )
}

internal fun numberedScrollbackPanes(count: Int): List<AiChatScrollbackPane> = (0 until count).map { index ->
    AiChatScrollbackPane.Interaction(
        requestText = "Request $index",
        responseParts = listOf(ChatMessagePart.AssistantText("Assistant response $index")),
    )
}
