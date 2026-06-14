package com.strangeparticle.luther.cmp

import com.strangeparticle.luther.core.session.ChatMessagePart

internal data class AiChatPaneState(
    val isConfigured: Boolean,
    val providerLabel: String,
    val modelLabel: String,
    val modelPicker: AiChatPaneModelPickerState?,
    val transcriptParts: List<ChatMessagePart>,
    val scrollbackPanes: List<AiChatScrollbackPane>,
    val debugChatHistoryText: String,
    val isRunning: Boolean,
    val focusInputOnShow: Boolean,
    val onSubmit: (String) -> Unit,
    val onStop: () -> Unit,
    val onApprovalDecision: (toolCallId: String, approved: Boolean) -> Unit,
    val onProcessingFocusFallback: () -> Unit,
) {
    companion object {
        fun notConfigured(): AiChatPaneState = AiChatPaneState(
            isConfigured = false,
            providerLabel = "Not configured",
            modelLabel = "",
            modelPicker = null,
            transcriptParts = emptyList(),
            scrollbackPanes = emptyList(),
            debugChatHistoryText = "",
            isRunning = false,
            focusInputOnShow = false,
            onSubmit = {},
            onStop = {},
            onApprovalDecision = { _, _ -> },
            onProcessingFocusFallback = {},
        )

        fun configured(
            providerLabel: String,
            modelLabel: String,
            modelPicker: AiChatPaneModelPickerState? = null,
            transcriptParts: List<ChatMessagePart>,
            terseHelpText: String = "",
            scrollbackPanes: List<AiChatScrollbackPane> = buildScrollbackPanesFromTranscript(transcriptParts, terseHelpText),
            debugChatHistoryText: String = "",
            isRunning: Boolean = false,
            focusInputOnShow: Boolean = false,
            onSubmit: (String) -> Unit,
            onStop: () -> Unit,
            onApprovalDecision: (toolCallId: String, approved: Boolean) -> Unit,
            onProcessingFocusFallback: () -> Unit = {},
        ): AiChatPaneState = AiChatPaneState(
            isConfigured = true,
            providerLabel = providerLabel,
            modelLabel = modelLabel,
            modelPicker = modelPicker,
            transcriptParts = transcriptParts,
            scrollbackPanes = scrollbackPanes,
            debugChatHistoryText = debugChatHistoryText,
            isRunning = isRunning,
            focusInputOnShow = focusInputOnShow,
            onSubmit = onSubmit,
            onStop = onStop,
            onApprovalDecision = onApprovalDecision,
            onProcessingFocusFallback = onProcessingFocusFallback,
        )

        private fun buildScrollbackPanesFromTranscript(transcriptParts: List<ChatMessagePart>, terseHelpText: String = ""): List<AiChatScrollbackPane> {
            val panes = mutableListOf<AiChatScrollbackPane>()
            var currentStartIndex: Int? = null
            var currentRequestText: String? = null
            val currentResponseParts = mutableListOf<ChatMessagePart>()

            fun flush() {
                val requestText = currentRequestText ?: return
                panes += AiChatScrollbackPane.Interaction(
                    requestText = requestText,
                    responseParts = currentResponseParts.toList(),
                    transcriptStartIndex = currentStartIndex,
                )
                currentStartIndex = null
                currentRequestText = null
                currentResponseParts.clear()
            }

            transcriptParts.forEachIndexed { index, part ->
                if (part is ChatMessagePart.UserText) {
                    flush()
                    currentStartIndex = index
                    currentRequestText = part.text
                } else if (currentRequestText != null) {
                    currentResponseParts += part
                }
            }
            flush()
            return panes.ifEmpty {
                if (transcriptParts.isEmpty()) listOf(initialTerseHelpScrollbackPane(terseHelpText)) else emptyList()
            }
        }
    }
}
