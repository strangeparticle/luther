package com.strangeparticle.luther.cmp

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.strangeparticle.luther.core.session.ChatMessagePart
import com.strangeparticle.luther.core.session.ToolCallState
import com.strangeparticle.luther.core.client.provider.ToolCall
import com.strangeparticle.luther.core.client.provider.Choice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
internal class AiChatPaneTest {

    @Test
    fun `renders not configured state with settings action`() = runComposeUiTest {
        var settingsClickCount = 0
        setContent {
            MaterialTheme {
                AiChatPane(
                    state = AiChatPaneState.notConfigured(),
                    onClose = {},
                    onOpenSettings = { settingsClickCount += 1 },
                )
            }
        }

        onNodeWithText("AI is not configured").assertExists()
        onNodeWithTag(AiChatTestTags.AI_CHAT_SETTINGS_BUTTON).performClick()

        assertEquals(1, settingsClickCount)
    }

    @Test
    fun `renders transcript parts and approval callbacks`() = runComposeUiTest {
        val decisions = mutableListOf<Boolean>()
        val toolCall = ToolCall("call-save", "save_springboard", "{}")

        setContent {
            MaterialTheme {
                ChatMessagePartRenderer(
                    part = ChatMessagePart.ToolCall(toolCall, ToolCallState.ApprovalRequested),
                    onApprovalDecision = { _, approved -> decisions += approved },
                )
            }
        }

        onNodeWithText("Approval requested").assertExists()
        onNodeWithTag(AiChatTestTags.AI_APPROVAL_APPLY_BUTTON).performClick()

        assertEquals(listOf(true), decisions)
    }

    @Test
    fun `successful tool calls are not shown in chat transcript`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                ChatMessagePartRenderer(
                    part = ChatMessagePart.ToolCall(
                        ToolCall("call-group", "add_app_group", "{}"),
                        ToolCallState.OutputAvailable("Applied."),
                    ),
                    onApprovalDecision = { _, _ -> },
                )
            }
        }

        onNodeWithText("Tool: add_app_group").assertDoesNotExist()
        onNodeWithText("Applied.").assertDoesNotExist()
    }

    @Test
    fun `provider error after tool call does not show tool success`() = runComposeUiTest {
        val state = configuredState(
            transcriptParts = listOf(
                ChatMessagePart.UserText("Group these apps"),
                ChatMessagePart.ToolCall(
                    ToolCall("call-group", "add_app_group", "{}"),
                    ToolCallState.OutputAvailable("Applied."),
                ),
                ChatMessagePart.ChatError("OpenAI request failed with HTTP 429: Request too large"),
            ),
        )

        setContent {
            MaterialTheme {
                AiChatPane(state = state, onClose = {}, onOpenSettings = {})
            }
        }

        onNodeWithText("Group these apps").assertExists()
        onNodeWithText("Error: OpenAI request failed with HTTP 429: Request too large").assertExists()
        onNodeWithText("Tool: add_app_group").assertDoesNotExist()
        onNodeWithText("Applied.").assertDoesNotExist()
    }

    @Test
    fun `interaction messages expose distinct visual roles`() = runComposeUiTest {
        val state = configuredState(
            transcriptParts = listOf(
                ChatMessagePart.UserText("Add Chrome"),
                ChatMessagePart.AssistantText("I can do that."),
                ChatMessagePart.ToolCall(
                    ToolCall("call-save", "save_springboard", "{}"),
                    ToolCallState.ApprovalRequested,
                ),
                ChatMessagePart.ChatError("network unavailable"),
            ),
        )

        setContent {
            MaterialTheme {
                AiChatPane(state = state, onClose = {}, onOpenSettings = {}, initialHeight = 420.dp)
            }
        }

        val pane = onNodeWithTag(AiChatTestTags.aiChatScrollbackPane(0)).getUnclippedBoundsInRoot()
        val user = onNodeWithTag(AiChatTestTags.AI_CHAT_USER_MESSAGE, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val assistant = onNodeWithTag(AiChatTestTags.AI_CHAT_ASSISTANT_MESSAGE, useUnmergedTree = true).getUnclippedBoundsInRoot()

        val error = onNodeWithTag(AiChatTestTags.AI_CHAT_ERROR_MESSAGE, useUnmergedTree = true).getUnclippedBoundsInRoot()

        onNodeWithTag(AiChatTestTags.AI_CHAT_TOOL_ACTIVITY, useUnmergedTree = true).assertExists()
        onNodeWithText("You").assertDoesNotExist()
        onNodeWithText("Assistant").assertDoesNotExist()
        onNodeWithText("Tool").assertDoesNotExist()
        onNodeWithText("Error").assertDoesNotExist()
        onNodeWithText("You: Add Chrome").assertDoesNotExist()
        onNodeWithText("Add Chrome").assertExists()
        onNodeWithText("Error: network unavailable").assertExists()

        assertTrue(user.left < pane.left + 72.dp, "user message should sit on the left side of its pane")
        assertTrue(assistant.left < pane.left + 72.dp, "assistant message should sit on the left side of its pane")
        assertTrue(error.top > user.bottom + 8.dp, "response/error should have breathing room below the user message")
    }

    @Test
    fun `user message bubble uses the primary role`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                val colors = AiChatPaneDefaults.colors()
                assertEquals(MaterialTheme.colorScheme.primary, colors.userMessageBubble)
                assertEquals(MaterialTheme.colorScheme.onPrimary, colors.userMessageText)
            }
        }
    }

    @Test
    fun `local commands expose command visual role`() = runComposeUiTest {
        val state = configuredState(
            scrollbackPanes = listOf(
                AiChatScrollbackPane.LocalCommand(
                    commandText = "/help",
                    commandAttribution = CommandAttribution.User,
                    responseText = AiChatTestHelpText.full,
                    style = LocalCommandResponseStyle.Help,
                ),
            ),
        )

        setContent {
            MaterialTheme {
                AiChatPane(state = state, onClose = {}, onOpenSettings = {})
            }
        }

        onNodeWithTag(AiChatTestTags.AI_CHAT_COMMAND_MESSAGE, useUnmergedTree = true).assertExists()
        onNodeWithText("Command").assertDoesNotExist()
        onNodeWithText("You: /help").assertExists()
    }

    @Test
    fun `input row sends text through pane state`() = runComposeUiTest {
        val sentMessages = mutableListOf<String>()
        val state = AiChatPaneState.configured(
            providerLabel = "OpenAI",
            modelLabel = "gpt-5",
            transcriptParts = emptyList(),
            onSubmit = { sentMessages += it },
            onStop = {},
            onApprovalDecision = { _, _ -> },
        )

        setContent {
            MaterialTheme {
                AiChatPane(state = state, onClose = {}, onOpenSettings = {})
            }
        }

        onNodeWithTag(AiChatTestTags.AI_CHAT_INPUT).performTextInput("Add Chrome")
        onNodeWithTag(AiChatTestTags.AI_CHAT_SEND_BUTTON).performClick()

        assertEquals(listOf("Add Chrome"), sentMessages)
    }

    @Test
    fun `title bar renders provider with model dropdown and selection callback`() = runComposeUiTest {
        val selectedModels = mutableListOf<String>()
        val state = configuredState(
            modelLabel = "gpt-5",
            modelPicker = AiChatPaneModelPickerState(
                selectedModelId = "gpt-5",
                selectedModelLabel = "GPT-5",
                options = listOf(
                    Choice("gpt-5", "GPT-5"),
                    Choice("gpt-4.1", "GPT-4.1"),
                ),
                isLoading = false,
                errorMessage = null,
                onRefresh = {},
                onSelectModel = { selectedModels += it },
            ),
        )

        setContent {
            MaterialTheme {
                AiChatPane(state = state, onClose = {}, onOpenSettings = {})
            }
        }

        onNodeWithText("OpenAI:").assertExists()
        onNodeWithTag(AiChatTestTags.AI_CHAT_MODEL_DROPDOWN).assertTextContains("GPT-5").performClick()
        onNodeWithTag(AiChatTestTags.aiChatModelDropdownOption("gpt-4.1")).performClick()

        assertEquals(listOf("gpt-4.1"), selectedModels)
    }

    @Test
    fun `title bar model dropdown exposes loading error and empty option states`() = runComposeUiTest {
        val refreshes = mutableListOf<Unit>()
        val state = configuredState(
            modelPicker = AiChatPaneModelPickerState(
                selectedModelId = "",
                selectedModelLabel = "",
                options = emptyList(),
                isLoading = true,
                errorMessage = "network unavailable",
                onRefresh = { refreshes += Unit },
                onSelectModel = {},
            ),
        )

        setContent {
            MaterialTheme {
                AiChatPane(state = state, onClose = {}, onOpenSettings = {})
            }
        }

        onNodeWithTag(AiChatTestTags.AI_CHAT_MODEL_DROPDOWN).assertTextContains("Loading")
        onNodeWithText("Model list error: network unavailable").assertExists()
        onNodeWithTag(AiChatTestTags.AI_CHAT_MODEL_REFRESH_BUTTON).performClick()

        assertEquals(listOf(Unit), refreshes)
    }

    @Test
    fun `enter sends while shift enter inserts a newline`() = runComposeUiTest {
        val sentMessages = mutableListOf<String>()
        val state = configuredState(onSubmit = { sentMessages += it })

        setContent {
            MaterialTheme {
                AiChatPane(state = state, onClose = {}, onOpenSettings = {})
            }
        }

        val input = onNodeWithTag(AiChatTestTags.AI_CHAT_INPUT)
        input.performTextInput("line one")
        input.performKeyInput {
            keyDown(Key.ShiftLeft)
            pressKey(Key.Enter)
            keyUp(Key.ShiftLeft)
        }
        input.performTextInput("line two")
        input.assertTextEquals("line one\nline two")
        assertEquals(emptyList(), sentMessages)

        input.performKeyInput { pressKey(Key.Enter) }

        assertEquals(listOf("line one\nline two"), sentMessages)
    }

    @Test
    fun `pasting tabs preserves tab characters`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                AiChatPane(state = configuredState(), onClose = {}, onOpenSettings = {})
            }
        }

        val input = onNodeWithTag(AiChatTestTags.AI_CHAT_INPUT)
        input.performTextInput("before\tafter")

        input.assertTextEquals("before\tafter")
    }

    @Test
    fun `stop button stops when running`() = runComposeUiTest {
        var stopCount = 0
        val state = configuredState(isRunning = true, onStop = { stopCount += 1 })

        setContent {
            MaterialTheme {
                AiChatPane(state = state, onClose = {}, onOpenSettings = {})
            }
        }

        onNodeWithTag(AiChatTestTags.AI_CHAT_STOP_BUTTON).performClick()

        assertEquals(1, stopCount)
    }

    @Test
    fun `running state disables input editing and submit`() = runComposeUiTest {
        val sentMessages = mutableListOf<String>()
        val state = configuredState(
            isRunning = true,
            onSubmit = { sentMessages += it },
        )

        setContent {
            MaterialTheme {
                AiChatPane(state = state, onClose = {}, onOpenSettings = {})
            }
        }

        onNodeWithTag(AiChatTestTags.AI_CHAT_SEND_BUTTON).assertIsNotEnabled()
        onNodeWithTag(AiChatTestTags.AI_CHAT_STOP_BUTTON).assertIsEnabled()
        onNodeWithTag(AiChatTestTags.AI_CHAT_WORKING_INDICATOR).assertExists()

        val input = onNodeWithTag(AiChatTestTags.AI_CHAT_INPUT)
        input.assertIsNotEnabled()
        input.assertTextEquals("")
        assertEquals(emptyList(), sentMessages)
    }

    @Test
    fun `idle state hides processing indicator and allows submit`() = runComposeUiTest {
        val sentMessages = mutableListOf<String>()
        val state = configuredState(
            isRunning = false,
            onSubmit = { sentMessages += it },
        )

        setContent {
            MaterialTheme {
                AiChatPane(state = state, onClose = {}, onOpenSettings = {})
            }
        }

        onNodeWithTag(AiChatTestTags.AI_CHAT_WORKING_INDICATOR).assertDoesNotExist()
        onNodeWithTag(AiChatTestTags.AI_CHAT_SEND_BUTTON).assertIsEnabled()
        onNodeWithTag(AiChatTestTags.AI_CHAT_STOP_BUTTON).assertIsNotEnabled()

        onNodeWithTag(AiChatTestTags.AI_CHAT_INPUT).performTextInput("Add Chrome")
        onNodeWithTag(AiChatTestTags.AI_CHAT_SEND_BUTTON).performClick()

        assertEquals(listOf("Add Chrome"), sentMessages)
    }

    @Test
    fun `running transition requests processing focus fallback`() = runComposeUiTest {
        var fallbackCount = 0
        val isRunning = mutableStateOf(false)

        setContent {
            MaterialTheme {
                AiChatPane(
                    state = configuredState(
                        isRunning = isRunning.value,
                        onProcessingFocusFallback = { fallbackCount += 1 },
                    ),
                    onClose = {},
                    onOpenSettings = {},
                )
            }
        }

        waitForIdle()
        assertEquals(0, fallbackCount)

        isRunning.value = true
        waitForIdle()

        assertEquals(1, fallbackCount)
    }

    @Test
    fun `focus returns to input after running completes`() = runComposeUiTest {
        val isRunning = mutableStateOf(true)

        setContent {
            MaterialTheme {
                AiChatPane(
                    state = configuredState(isRunning = isRunning.value),
                    onClose = {},
                    onOpenSettings = {},
                )
            }
        }

        waitForIdle()
        isRunning.value = false
        waitForIdle()

        onNodeWithTag(AiChatTestTags.AI_CHAT_INPUT).assertIsFocused()
    }

    @Test
    fun `chat pane defaults to default height and fixed three line input`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                AiChatPane(state = configuredState(), onClose = {}, onOpenSettings = {})
            }
        }

        onNodeWithTag(AiChatTestTags.AI_CHAT_PANE).assertHeightIsEqualTo(270.dp)
        onNodeWithTag(AiChatTestTags.AI_CHAT_HISTORY).assertExists()
        onNodeWithTag(AiChatTestTags.AI_CHAT_INPUT).assertHeightIsEqualTo(64.dp)
    }

    @Test
    fun `chat pane renders at the explicit height passed in`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                AiChatPane(
                    state = configuredState(),
                    onClose = {},
                    onOpenSettings = {},
                    initialHeight = 360.dp,
                )
            }
        }

        onNodeWithTag(AiChatTestTags.AI_CHAT_PANE).assertHeightIsEqualTo(360.dp)
    }

    @Test
    fun `input field left edge aligns with scrollback panes`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                AiChatPane(state = configuredState(), onClose = {}, onOpenSettings = {})
            }
        }

        val scrollbackLeft = onNodeWithTag(AiChatTestTags.aiChatScrollbackPane(0)).getUnclippedBoundsInRoot().left
        val inputLeft = onNodeWithTag(AiChatTestTags.AI_CHAT_INPUT_SECTION).getUnclippedBoundsInRoot().left

        assertEquals(scrollbackLeft.value, inputLeft.value, absoluteTolerance = 0.5f)
    }

    @Test
    fun `input section uses lighter background than pane body`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                val colors = AiChatPaneDefaults.colors()
                assertEquals(MaterialTheme.colorScheme.surfaceContainerLow, colors.pane)
                assertEquals(MaterialTheme.colorScheme.surfaceContainer, colors.inputSection)
                assertNotEquals(colors.pane, colors.inputSection)
            }
        }
    }

    @Test
    fun `scrollback panes use distinct surfaces and outline`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                val colors = AiChatPaneDefaults.colors()
                assertEquals(MaterialTheme.colorScheme.surfaceContainerLow, colors.pane)
                assertEquals(MaterialTheme.colorScheme.surfaceContainerHigh, colors.interactionScrollbackPane)
                assertEquals(MaterialTheme.colorScheme.surfaceContainer, colors.helpScrollbackPane)
                assertEquals(MaterialTheme.colorScheme.outlineVariant, colors.scrollbackPaneOutline)
                assertNotEquals(colors.pane, colors.interactionScrollbackPane)
                assertNotEquals(colors.pane, colors.helpScrollbackPane)
            }
        }
    }

    @Test
    fun `scrollback text uses compact line height`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                val textStyle = AiChatPaneDefaults.scrollbackTextStyle()
                assertEquals(13.sp, textStyle.fontSize)
                assertEquals(17.sp, textStyle.lineHeight)
            }
        }
    }

    @Test
    fun `startup terse help renders as a scrollback pane with command text`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                AiChatPane(state = configuredState(), onClose = {}, onOpenSettings = {})
            }
        }

        onNodeWithTag(AiChatTestTags.aiChatScrollbackPane(0)).assertExists()
        onNodeWithText("/help_terse").assertExists()
        onNodeWithText("You: /help_terse").assertDoesNotExist()
        onNodeWithText(AiChatTestHelpText.terse.lines().first(), substring = true).assertExists()
        onNodeWithTag(AiChatTestTags.aiChatScrollbackPaneCopyButton(0)).assertExists()
    }

    @Test
    fun `chat header uses AI assistant title and secondary provider text`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                AiChatPane(state = configuredState(), onClose = {}, onOpenSettings = {})
            }
        }

        onNodeWithText("AI Assistant").assertExists()
        onNodeWithText("OpenAI:").assertExists()
        onNodeWithText("gpt-5").assertExists()
        onNodeWithText("OpenAI · gpt-5").assertDoesNotExist()
        onNodeWithText("Assistant · OpenAI gpt-5").assertDoesNotExist()
    }

    @Test
    fun `send and stop buttons are compact`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                AiChatPane(state = configuredState(), onClose = {}, onOpenSettings = {})
            }
        }

        onNodeWithTag(AiChatTestTags.AI_CHAT_SEND_BUTTON).assertHeightIsEqualTo(32.dp)
        onNodeWithTag(AiChatTestTags.AI_CHAT_STOP_BUTTON).assertHeightIsEqualTo(32.dp)
    }

    @Test
    fun `copy transcript button copies full transcript text`() = runComposeUiTest {
        var copiedText = ""
        val state = configuredState(
            transcriptParts = listOf(
                ChatMessagePart.UserText("Add Chrome"),
                ChatMessagePart.AssistantText("I can do that."),
                ChatMessagePart.ChatError("network unavailable"),
                ChatMessagePart.ToolCall(
                    ToolCall("call-save", "save_springboard", "{}"),
                    ToolCallState.OutputAvailable("saved"),
                ),
            ),
        )

        setContent {
            MaterialTheme {
                AiChatPane(
                    state = state,
                    onClose = {},
                    onOpenSettings = {},
                    onCopyTranscript = { copiedText = it },
                )
            }
        }

        onNodeWithTag(AiChatTestTags.AI_CHAT_COPY_TRANSCRIPT_BUTTON).performClick()

        assertEquals(
            "You: Add Chrome\n\nAssistant: I can do that.\nError: network unavailable",
            copiedText,
        )
    }

    @Test
    fun `copy debug chat history button copies debug dump text and sits left of transcript copy`() = runComposeUiTest {
        var copiedText = ""
        val state = configuredState(
            debugChatHistoryText = """{"kind":"SpringboardAiDebugDump"}""",
        )

        setContent {
            MaterialTheme {
                AiChatPane(
                    state = state,
                    onClose = {},
                    onOpenSettings = {},
                    onCopyTranscript = { copiedText = it },
                )
            }
        }

        onNodeWithTag(AiChatTestTags.AI_CHAT_COPY_DEBUG_HISTORY_BUTTON).performClick()

        val debugButton = onNodeWithTag(AiChatTestTags.AI_CHAT_COPY_DEBUG_HISTORY_BUTTON).getUnclippedBoundsInRoot()
        val transcriptButton = onNodeWithTag(AiChatTestTags.AI_CHAT_COPY_TRANSCRIPT_BUTTON).getUnclippedBoundsInRoot()
        assertTrue(debugButton.right <= transcriptButton.left)
        assertEquals("""{"kind":"SpringboardAiDebugDump"}""", copiedText)
    }

    @Test
    fun `user-facing help is rendered when transcript is empty`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                AiChatPane(state = configuredState(), onClose = {}, onOpenSettings = {})
            }
        }

        onNodeWithTag(AiChatTestTags.AI_CHAT_USER_HELP).assertExists()
        onNodeWithText(AiChatTestHelpText.terse.lines().first(), substring = true).assertExists()
    }

    @Test
    fun `help command renders below empty-chat summary`() = runComposeUiTest {
        val state = configuredState(
            scrollbackPanes = listOf(
                AiChatScrollbackPane.LocalCommand(
                    commandText = "/help_terse",
                    commandAttribution = CommandAttribution.System,
                    responseText = AiChatTestHelpText.terse,
                    style = LocalCommandResponseStyle.Help,
                ),
                AiChatScrollbackPane.LocalCommand(
                    commandText = "/help",
                    commandAttribution = CommandAttribution.User,
                    responseText = AiChatTestHelpText.full,
                    style = LocalCommandResponseStyle.Help,
                ),
            ),
        )
        setContent {
            MaterialTheme {
                AiChatPane(state = state, onClose = {}, onOpenSettings = {})
            }
        }

        onNodeWithTag(AiChatTestTags.aiChatScrollbackPane(1)).assertExists()
        onNodeWithText("You: /help").assertExists()
        onNodeWithText(AiChatTestHelpText.fullTitle, substring = true).assertExists()
        onNodeWithTag(AiChatTestTags.AI_CHAT_HISTORY).performScrollToIndex(0)
        onNodeWithTag(AiChatTestTags.aiChatScrollbackPane(0)).assertExists()
    }

    @Test
    fun `copy transcript includes local help command output`() = runComposeUiTest {
        var copiedText = ""
        val state = configuredState(
            scrollbackPanes = listOf(
                AiChatScrollbackPane.LocalCommand(
                    commandText = "/help_terse",
                    commandAttribution = CommandAttribution.System,
                    responseText = AiChatTestHelpText.terse,
                    style = LocalCommandResponseStyle.Help,
                ),
                AiChatScrollbackPane.LocalCommand(
                    commandText = "/help",
                    commandAttribution = CommandAttribution.User,
                    responseText = AiChatTestHelpText.full,
                    style = LocalCommandResponseStyle.Help,
                ),
            ),
        )
        setContent {
            MaterialTheme {
                AiChatPane(
                    state = state,
                    onClose = {},
                    onOpenSettings = {},
                    onCopyTranscript = { copiedText = it },
                )
            }
        }

        onNodeWithTag(AiChatTestTags.AI_CHAT_COPY_TRANSCRIPT_BUTTON).performClick()

        assertTrue(copiedText.contains("/help_terse"))
        assertTrue(copiedText.contains("You: /help"))
        assertTrue(copiedText.contains(AiChatTestHelpText.fullTitle))
    }

    @Test
    fun `pane copy button copies only that scrollback pane`() = runComposeUiTest {
        var copiedText = ""
        val state = configuredState(
            scrollbackPanes = listOf(
                AiChatScrollbackPane.LocalCommand(
                    commandText = "/help_terse",
                    commandAttribution = CommandAttribution.System,
                    responseText = AiChatTestHelpText.terse,
                    style = LocalCommandResponseStyle.Help,
                ),
                AiChatScrollbackPane.Interaction(
                    requestText = "Add Chrome",
                    responseParts = listOf(ChatMessagePart.AssistantText("Added Chrome.")),
                ),
            ),
        )
        setContent {
            MaterialTheme {
                AiChatPane(
                    state = state,
                    onClose = {},
                    onOpenSettings = {},
                    onCopyTranscript = { copiedText = it },
                )
            }
        }

        onNodeWithTag(AiChatTestTags.aiChatScrollbackPaneCopyButton(1), useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        waitForIdle()

        assertEquals("You: Add Chrome\n\nAssistant: Added Chrome.", copiedText)
    }

    @Test
    fun `interaction pane renders user request once transcript has a user part`() = runComposeUiTest {
        val state = configuredState(
            transcriptParts = listOf(ChatMessagePart.UserText("Add a logs URL for fretnaut in prod")),
        )
        setContent {
            MaterialTheme {
                AiChatPane(state = state, onClose = {}, onOpenSettings = {})
            }
        }

        onNodeWithText("Add a logs URL for fretnaut in prod").assertExists()
    }

    @Test
    fun `close button is compact`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                AiChatPane(state = configuredState(), onClose = {}, onOpenSettings = {})
            }
        }

        onNodeWithTag(AiChatTestTags.AI_CHAT_CLOSE_BUTTON).assertHeightIsEqualTo(28.dp).assertWidthIsEqualTo(28.dp)
    }

    @Test
    fun `chat input gains focus on click and focus changes the border token`() = runComposeUiTest {
        var focused = Color.Unspecified
        var unfocused = Color.Unspecified
        setContent {
            MaterialTheme {
                val colors = AiChatPaneDefaults.colors()
                focused = colors.inputBorderFocused
                unfocused = colors.inputBorderUnfocused
                AiChatPane(state = configuredState(), onClose = {}, onOpenSettings = {})
            }
        }

        assertNotEquals(unfocused, focused)
        onNodeWithTag(AiChatTestTags.AI_CHAT_INPUT).performClick().assertIsFocused()
    }

    @Test
    fun `chat history scrolls to bottom when a new pane is appended`() = runComposeUiTest {
        val scrollbackPanes = mutableStateOf(numberedScrollbackPanes(12))

        setContent {
            MaterialTheme {
                AiChatPane(
                    state = configuredState(scrollbackPanes = scrollbackPanes.value),
                    onClose = {},
                    onOpenSettings = {},
                    initialHeight = 220.dp,
                )
            }
        }

        onNodeWithText("Request 11").assertIsDisplayed()

        scrollbackPanes.value = numberedScrollbackPanes(13)
        waitForIdle()

        onNodeWithText("Request 12").assertIsDisplayed()
    }

    @Test
    fun `chat history scrolls to bottom on initial render`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                AiChatPane(
                    state = configuredState(scrollbackPanes = numberedScrollbackPanes(12)),
                    onClose = {},
                    onOpenSettings = {},
                    initialHeight = 220.dp,
                )
            }
        }

        onNodeWithText("Request 11").assertIsDisplayed()
    }

    @Test
    fun `chat history scrolls to bottom when latest pane content grows after user scrolled up`() = runComposeUiTest {
        val initialPanes = numberedScrollbackPanes(12)
        val scrollbackPanes = mutableStateOf(initialPanes)

        setContent {
            MaterialTheme {
                AiChatPane(
                    state = configuredState(scrollbackPanes = scrollbackPanes.value),
                    onClose = {},
                    onOpenSettings = {},
                    initialHeight = 220.dp,
                )
            }
        }

        onNodeWithTag(AiChatTestTags.AI_CHAT_HISTORY).performScrollToIndex(0)
        onNodeWithText("Request 0").assertIsDisplayed()

        scrollbackPanes.value = initialPanes.dropLast(1) + AiChatScrollbackPane.Interaction(
            requestText = "Request 11",
            responseParts = listOf(
                ChatMessagePart.AssistantText(
                    (1..20).joinToString("\n") { line -> "Expanded assistant response line $line" },
                ),
            ),
        )
        waitForIdle()

        onNodeWithText("Expanded assistant response line 20", substring = true).assertIsDisplayed()
    }

}
