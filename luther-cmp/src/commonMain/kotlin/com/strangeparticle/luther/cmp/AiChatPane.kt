package com.strangeparticle.luther.cmp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class AiChatPaneColors(
    val pane: Color,
    val inputSection: Color,
    val interactionScrollbackPane: Color,
    val helpScrollbackPane: Color,
    val scrollbackPaneOutline: Color,
)

internal object AiChatPaneDefaults {
    /** Default vertical height the pane opens at the first time it is shown. */
    val DefaultHeight = 270.dp

    /** Lower bound the user can drag the pane to — keeps title bar, body row, and input visible. */
    val MinHeight = 140.dp

    /** Soft upper bound the user can drag the pane to. */
    val MaxHeight = 600.dp

    @Composable
    fun colors(): AiChatPaneColors = AiChatPaneColors(
        pane = MaterialTheme.colorScheme.surfaceContainerLow,
        inputSection = MaterialTheme.colorScheme.surfaceContainer,
        interactionScrollbackPane = MaterialTheme.colorScheme.surfaceContainerHigh,
        helpScrollbackPane = MaterialTheme.colorScheme.surfaceContainer,
        scrollbackPaneOutline = MaterialTheme.colorScheme.outlineVariant,
    )

    fun scrollbackTextStyle(): TextStyle = TextStyle(
        fontSize = 13.sp,
        lineHeight = 17.sp,
    )
}

@Composable
private fun AiChatPaneModelDropdown(
    state: AiChatPaneModelPickerState,
    fallbackModelLabel: String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val hasOptions = state.options.isNotEmpty()
    val selectedDisplayName = (
        state.options.firstOrNull { it.valueId == state.selectedModelId }?.displayLabel
            ?: state.selectedModelLabel
        ).ifBlank { fallbackModelLabel }
    val displayedText = when {
        state.isLoading -> "Loading"
        state.errorMessage != null && selectedDisplayName.isBlank() -> "Error"
        !hasOptions && selectedDisplayName.isBlank() -> "No models"
        else -> selectedDisplayName
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box {
            Row(
                modifier = Modifier
                    .widthIn(min = 92.dp, max = 180.dp)
                    .height(24.dp)
                    .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.extraSmall)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraSmall)
                    .clickable(enabled = hasOptions && !state.isLoading) { expanded = true }
                    .padding(start = 8.dp, end = 4.dp)
                    .semantics(mergeDescendants = true) {}
                    .testTag(AiChatTestTags.AI_CHAT_MODEL_DROPDOWN),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = displayedText.ifBlank { "(select)" },
                    fontSize = 11.sp,
                    color = if (state.errorMessage != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = AiChatIcons.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
            DropdownMenu(
                expanded = expanded && hasOptions,
                onDismissRequest = { expanded = false },
            ) {
                state.options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.displayLabel, fontSize = 13.sp) },
                        onClick = {
                            state.onSelectModel(option.valueId)
                            expanded = false
                        },
                        modifier = Modifier.testTag(AiChatTestTags.aiChatModelDropdownOption(option.valueId)),
                    )
                }
            }
        }
        IconButton(
            onClick = state.onRefresh,
            modifier = Modifier.size(24.dp).testTag(AiChatTestTags.AI_CHAT_MODEL_REFRESH_BUTTON),
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = AiChatIcons.Refresh,
                    contentDescription = "Reload assistant model options",
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

@Composable
internal fun AiChatPane(
    state: AiChatPaneState,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    onCopyTranscript: (String) -> Unit = rememberCopyToClipboard(),
    onTabOut: () -> Unit = {},
    onShiftTabOut: () -> Unit = {},
    inputFocusRequester: FocusRequester? = null,
    height: androidx.compose.ui.unit.Dp = AiChatPaneDefaults.DefaultHeight,
) {
    var inputValue by remember { mutableStateOf(TextFieldValue("")) }
    var hasBeenRunning by remember { mutableStateOf(false) }
    val fallbackInputFocusRequester = remember { FocusRequester() }
    val activeInputFocusRequester = inputFocusRequester ?: fallbackInputFocusRequester
    val inputInteractionSource = remember { MutableInteractionSource() }
    val isInputFocused by inputInteractionSource.collectIsFocusedAsState()
    val inputBorderColor = if (isInputFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val inputBorderWidth = if (isInputFocused) 2.dp else 1.dp
    val colors = AiChatPaneDefaults.colors()
    val historyListState = rememberLazyListState()
    LaunchedEffect(state.scrollbackPanes.size, state.scrollbackPanes.lastOrNull()) {
        if (state.scrollbackPanes.isNotEmpty()) {
            historyListState.scrollToItem(state.scrollbackPanes.size)
        }
    }
    LaunchedEffect(state.focusInputOnShow) {
        if (state.focusInputOnShow && !state.isRunning) {
            try { activeInputFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }
    LaunchedEffect(state.isRunning) {
        if (state.isRunning) {
            hasBeenRunning = true
            state.onProcessingFocusFallback()
        } else if (hasBeenRunning) {
            try { activeInputFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }
    fun sendInput() {
        val text = inputValue.text.trim()
        if (text.isNotEmpty() && !state.isRunning) {
            state.onSubmit(text)
            inputValue = TextFieldValue("")
        }
    }
    fun insertTextAtSelection(text: String) {
        val selectionStart = inputValue.selection.min
        val selectionEnd = inputValue.selection.max
        val newText = inputValue.text.replaceRange(selectionStart, selectionEnd, text)
        inputValue = TextFieldValue(
            text = newText,
            selection = TextRange(selectionStart + text.length),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .requiredHeight(height)
            .testTag(AiChatTestTags.AI_CHAT_PANE),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .requiredHeight(height)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown && event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter -> {
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            if (state.isRunning) return@onPreviewKeyEvent true
                            if (event.isShiftPressed) {
                                insertTextAtSelection("\n")
                            } else {
                                sendInput()
                            }
                            true
                        }
                        Key.Tab -> {
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            if (state.isRunning) return@onPreviewKeyEvent true
                            if (event.isShiftPressed) {
                                onShiftTabOut()
                            } else {
                                onTabOut()
                            }
                            true
                        }
                        Key.Escape -> {
                            if (state.isRunning && event.type == KeyEventType.KeyUp) {
                                state.onStop()
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                },
            color = colors.pane,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // ── Title bar ─────────────────────────────────────────────
                // A visually distinct strip at the top of the pane — slightly higher
                // surface tone than the pane body, with a divider underneath.
                Surface(
                    modifier = Modifier.fillMaxWidth().testTag(AiChatTestTags.AI_CHAT_TITLE_BAR),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .requiredHeight(36.dp)
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "AI Assistant",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.alignBy(FirstBaseline),
                            )
                            if (state.isConfigured) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "${state.providerLabel}:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.alignBy(FirstBaseline),
                                )
                                Spacer(Modifier.width(4.dp))
                                if (state.modelPicker != null) {
                                    AiChatPaneModelDropdown(
                                        state = state.modelPicker,
                                        fallbackModelLabel = state.modelLabel,
                                        modifier = Modifier.alignBy(FirstBaseline),
                                    )
                                    if (state.modelPicker.errorMessage != null) {
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "Model list error: ${state.modelPicker.errorMessage}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.error,
                                            maxLines = 1,
                                            modifier = Modifier.alignBy(FirstBaseline),
                                        )
                                    }
                                } else {
                                    SelectionContainer(modifier = Modifier.alignBy(FirstBaseline)) {
                                        Text(
                                            text = state.modelLabel,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                        if (!state.isConfigured) {
                            Text(
                                text = "Not configured",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TooltipIconButton(
                            tooltipText = "Copy debug history",
                            onClick = { onCopyTranscript(state.debugChatHistoryText) },
                            enabled = state.debugChatHistoryText.isNotBlank(),
                            modifier = Modifier.size(28.dp).testTag(AiChatTestTags.AI_CHAT_COPY_DEBUG_HISTORY_BUTTON),
                        ) {
                            Icon(
                                AiChatIcons.BugReport,
                                contentDescription = "Copy debug chat history",
                                modifier = Modifier.size(15.dp),
                            )
                        }
                        TooltipIconButton(
                            tooltipText = "Copy conversation",
                            onClick = { onCopyTranscript(getAllScrollbackTextForCopyToClipboard(state.scrollbackPanes)) },
                            enabled = state.scrollbackPanes.isNotEmpty(),
                            modifier = Modifier.size(28.dp).testTag(AiChatTestTags.AI_CHAT_COPY_TRANSCRIPT_BUTTON),
                        ) {
                            Icon(
                                AiChatIcons.ContentCopy,
                                contentDescription = "Copy assistant transcript",
                                modifier = Modifier.size(15.dp),
                            )
                        }
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(28.dp).testTag(AiChatTestTags.AI_CHAT_CLOSE_BUTTON),
                        ) {
                            Icon(AiChatIcons.Close, contentDescription = "Close assistant", modifier = Modifier.size(16.dp))
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // ── Pane body ─────────────────────────────────────────────
                Column(modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (!state.isConfigured) {
                        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("AI is not configured", fontSize = 13.sp)
                            Text(
                                text = "Open AI settings",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .testTag(AiChatTestTags.AI_CHAT_SETTINGS_BUTTON)
                                    .clickable(onClick = onOpenSettings)
                                    .padding(4.dp),
                            )
                        }
                        return@Column
                    }

                    LazyColumn(
                        state = historyListState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag(AiChatTestTags.AI_CHAT_HISTORY),
                    ) {
                        itemsIndexed(state.scrollbackPanes) { index, pane ->
                            AiChatScrollbackPaneRenderer(
                                index = index,
                                pane = pane,
                                onApprovalDecision = state.onApprovalDecision,
                                onCopyToClipboard = onCopyTranscript,
                            )
                        }
                        item(key = "chat-bottom-anchor") {
                            Spacer(Modifier.height(1.dp))
                        }
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth().testTag(AiChatTestTags.AI_CHAT_INPUT_SECTION),
                        color = colors.inputSection,
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .requiredHeight(76.dp)
                            ) {
                                BasicTextField(
                                    value = inputValue,
                                    onValueChange = {
                                        if (!state.isRunning) inputValue = it
                                    },
                                    enabled = !state.isRunning,
                                    interactionSource = inputInteractionSource,
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .requiredHeight(76.dp)
                                        .focusRequester(activeInputFocusRequester)
                                        .border(inputBorderWidth, inputBorderColor, MaterialTheme.shapes.small)
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                        .testTag(AiChatTestTags.AI_CHAT_INPUT),
                                    textStyle = TextStyle(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 13.sp,
                                    ),
                                    minLines = 3,
                                    maxLines = 3,
                                )
                                if (state.isRunning) {
                                    ProcessingOverlay(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .testTag(AiChatTestTags.AI_CHAT_WORKING_INDICATOR),
                                    )
                                }
                            }
                            Button(
                                onClick = { sendInput() },
                                enabled = !state.isRunning,
                                modifier = Modifier.height(32.dp).testTag(AiChatTestTags.AI_CHAT_SEND_BUTTON),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            ) { Text("Send", fontSize = 13.sp) }
                            OutlinedButton(
                                onClick = state.onStop,
                                enabled = state.isRunning,
                                modifier = Modifier.height(32.dp).testTag(AiChatTestTags.AI_CHAT_STOP_BUTTON),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            ) { Text("Stop", fontSize = 13.sp) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TooltipIconButton(
    tooltipText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(tooltipText) } },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
        ) {
            content()
        }
    }
}

@Composable
private fun ProcessingOverlay(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f),
        shape = MaterialTheme.shapes.small,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
            )
            Text(
                text = "Processing",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AiChatScrollbackPaneRenderer(
    index: Int,
    pane: AiChatScrollbackPane,
    onApprovalDecision: (toolCallId: String, approved: Boolean) -> Unit,
    onCopyToClipboard: (String) -> Unit,
) {
    val isHelp = pane is AiChatScrollbackPane.LocalCommand && pane.style == LocalCommandResponseStyle.Help
    val colors = AiChatPaneDefaults.colors()
    val textStyle = AiChatPaneDefaults.scrollbackTextStyle()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(1.dp, colors.scrollbackPaneOutline, MaterialTheme.shapes.small)
            .testTag(AiChatTestTags.aiChatScrollbackPane(index)),
        color = if (isHelp) colors.helpScrollbackPane else colors.interactionScrollbackPane,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    Column {
                        when (pane) {
                            is AiChatScrollbackPane.ProviderModelChange -> ProviderModelChangePaneContent(pane, textStyle)
                            is AiChatScrollbackPane.LocalCommand -> LocalCommandPaneContent(pane, textStyle)
                            is AiChatScrollbackPane.Interaction -> InteractionPaneContent(pane, onApprovalDecision, textStyle)
                            is AiChatScrollbackPane.DebugProviderModelChange -> DebugProviderModelChangePaneContent(pane, textStyle)
                            is AiChatScrollbackPane.DebugUserMessage -> DebugUserMessagePaneContent(pane, textStyle)
                            is AiChatScrollbackPane.DebugStateSnapshot -> DebugStateSnapshotPaneContent(pane, textStyle)
                            is AiChatScrollbackPane.DebugAssistantMessage -> DebugAssistantMessagePaneContent(pane, textStyle)
                            is AiChatScrollbackPane.DebugToolResult -> DebugToolResultPaneContent(pane, textStyle)
                        }
                    }
                }
                TooltipIconButton(
                    tooltipText = "Copy message",
                    onClick = { onCopyToClipboard(getScrollbackPaneTextForCopyToClipboard(pane)) },
                    modifier = Modifier.size(28.dp).testTag(AiChatTestTags.aiChatScrollbackPaneCopyButton(index)),
                ) {
                    Icon(
                        AiChatIcons.ContentCopy,
                        contentDescription = "Copy scrollback pane",
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderModelChangePaneContent(
    pane: AiChatScrollbackPane.ProviderModelChange,
    textStyle: TextStyle,
) {
    Text(
        text = pane.text,
        style = textStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
    )
}

@Composable
private fun LocalCommandPaneContent(
    pane: AiChatScrollbackPane.LocalCommand,
    textStyle: TextStyle,
) {
    Text(
        text = when (pane.commandAttribution) {
            CommandAttribution.System -> pane.commandText
            CommandAttribution.User -> "You: ${pane.commandText}"
        },
        style = textStyle.copy(fontFamily = FontFamily.Monospace),
        color = Color(0xFFE07800),
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(AiChatTestTags.AI_CHAT_COMMAND_MESSAGE),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Text(
                text = pane.responseText,
                style = textStyle,
                color = when (pane.style) {
                    LocalCommandResponseStyle.Help -> MaterialTheme.colorScheme.onSurfaceVariant
                    LocalCommandResponseStyle.Error -> MaterialTheme.colorScheme.error
                },
                modifier = if (pane.style == LocalCommandResponseStyle.Help) Modifier.testTag(AiChatTestTags.AI_CHAT_USER_HELP) else Modifier,
            )
        }
    }
}

@Composable
private fun InteractionPaneContent(
    pane: AiChatScrollbackPane.Interaction,
    onApprovalDecision: (toolCallId: String, approved: Boolean) -> Unit,
    textStyle: TextStyle,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            modifier = Modifier.widthIn(max = 560.dp).testTag(AiChatTestTags.AI_CHAT_USER_MESSAGE),
            color = MaterialTheme.colorScheme.primary,
            shape = MaterialTheme.shapes.small,
        ) {
            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text(
                    text = pane.requestText,
                    style = textStyle,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    pane.responseParts.forEach { part ->
        ChatMessagePartRenderer(part = part, onApprovalDecision = onApprovalDecision)
    }
}

@Composable
private fun DebugPaneTitleBar(pane: AiChatScrollbackPane) {
    Text(
        text = debugPaneTitle(pane),
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        fontSize = 12.sp,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun DebugMonospaceBody(text: String, textStyle: TextStyle) {
    Text(
        text = text,
        style = textStyle.copy(fontFamily = FontFamily.Monospace),
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun DebugUserMessagePaneContent(
    pane: AiChatScrollbackPane.DebugUserMessage,
    textStyle: TextStyle,
) {
    DebugPaneTitleBar(pane)
    Text(text = pane.text, style = textStyle, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun DebugProviderModelChangePaneContent(
    pane: AiChatScrollbackPane.DebugProviderModelChange,
    textStyle: TextStyle,
) {
    DebugPaneTitleBar(pane)
    Text(text = pane.text, style = textStyle, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun DebugStateSnapshotPaneContent(
    pane: AiChatScrollbackPane.DebugStateSnapshot,
    textStyle: TextStyle,
) {
    DebugPaneTitleBar(pane)
    DebugMonospaceBody(text = pane.snapshotJson, textStyle = textStyle)
}

@Composable
private fun DebugAssistantMessagePaneContent(
    pane: AiChatScrollbackPane.DebugAssistantMessage,
    textStyle: TextStyle,
) {
    DebugPaneTitleBar(pane)
    if (!pane.text.isNullOrEmpty()) {
        Text(text = pane.text, style = textStyle, color = MaterialTheme.colorScheme.onSurface)
    }
    pane.toolCalls.forEach { toolCall ->
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Tool call: ${toolCall.name}",
            fontWeight = FontWeight.SemiBold,
            style = textStyle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        DebugMonospaceBody(text = toolCall.argumentsJson, textStyle = textStyle)
    }
}

@Composable
private fun DebugToolResultPaneContent(
    pane: AiChatScrollbackPane.DebugToolResult,
    textStyle: TextStyle,
) {
    DebugPaneTitleBar(pane)
    DebugMonospaceBody(text = pane.content, textStyle = textStyle)
}
