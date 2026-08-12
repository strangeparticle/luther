package com.strangeparticle.luther.cmp

// Test tags for the drop-in chat component. These are component-owned (rather than living in a host
// TestTags registry) so luther-cmp is self-contained. Values match the original springboard tags so
// existing UI tests keep matching.
internal object AiChatTestTags {
    const val AI_CHAT_PANE = "aiChatPane"
    const val AI_CHAT_TITLE_BAR = "aiChatTitleBar"
    const val AI_CHAT_USER_HELP = "aiChatUserHelp"
    const val AI_CHAT_RESIZE_HANDLE = "aiChatResizeHandle"
    const val AI_CHAT_COPY_DEBUG_HISTORY_BUTTON = "aiChatCopyDebugHistoryButton"
    const val AI_CHAT_COPY_TRANSCRIPT_BUTTON = "aiChatCopyTranscriptButton"
    const val AI_CHAT_MODEL_DROPDOWN = "aiChatModelDropdown"
    const val AI_CHAT_MODEL_REFRESH_BUTTON = "aiChatModelRefreshButton"
    const val AI_CHAT_CLOSE_BUTTON = "aiChatCloseButton"
    const val AI_CHAT_SETTINGS_BUTTON = "aiChatSettingsButton"
    const val AI_CHAT_INPUT_SECTION = "aiChatInputSection"
    const val AI_CHAT_INPUT = "aiChatInput"
    const val AI_CHAT_HISTORY = "aiChatHistory"
    const val AI_CHAT_SEND_BUTTON = "aiChatSendButton"
    const val AI_CHAT_STOP_BUTTON = "aiChatStopButton"
    const val AI_CHAT_WORKING_INDICATOR = "aiChatWorkingIndicator"
    const val AI_CHAT_SCROLLBACK_PANE = "aiChatScrollbackPane"
    const val AI_CHAT_USER_MESSAGE = "aiChatUserMessage"
    const val AI_CHAT_ASSISTANT_MESSAGE = "aiChatAssistantMessage"
    const val AI_CHAT_ASSISTANT_WAITING = "aiChatAssistantWaiting"
    const val AI_CHAT_COMMAND_MESSAGE = "aiChatCommandMessage"
    const val AI_CHAT_TOOL_ACTIVITY = "aiChatToolActivity"
    const val AI_CHAT_ERROR_MESSAGE = "aiChatErrorMessage"
    const val AI_APPROVAL_APPLY_BUTTON = "aiApprovalApplyButton"
    const val AI_APPROVAL_CANCEL_BUTTON = "aiApprovalCancelButton"

    fun aiChatScrollbackPane(index: Int) = "aiChatScrollbackPane_$index"
    fun aiChatScrollbackPaneCopyButton(index: Int) = "aiChatScrollbackPaneCopyButton_$index"
    fun aiChatModelDropdownOption(optionId: String) = "aiChatModelDropdownOption_$optionId"
}
