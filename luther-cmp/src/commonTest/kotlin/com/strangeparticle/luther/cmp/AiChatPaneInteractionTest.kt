package com.strangeparticle.luther.cmp

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.strangeparticle.luther.core.client.provider.ToolCall
import com.strangeparticle.luther.core.session.ChatMessagePart
import com.strangeparticle.luther.core.session.ToolCallState
import kotlin.test.Test
import kotlin.test.assertEquals

// Isolated luther-cmp UI tests: mount AiChatPane / ChatMessagePartRenderer directly under a plain
// MaterialTheme (no springboard app, theme, or settings), driving them via the multiplatform
// runComposeUiTest framework. These structural tests run on any supported platform; today they
// execute on the JVM desktop runner.
@OptIn(ExperimentalTestApi::class)
internal class AiChatPaneInteractionTest {

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
    fun `tool approval button fires approval decision callback`() = runComposeUiTest {
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
    fun `input row sends typed text through pane state`() = runComposeUiTest {
        val sentMessages = mutableListOf<String>()
        setContent {
            MaterialTheme {
                AiChatPane(state = configuredState(onSubmit = { sentMessages += it }), onClose = {}, onOpenSettings = {})
            }
        }

        onNodeWithTag(AiChatTestTags.AI_CHAT_INPUT).performTextInput("Add Chrome")
        onNodeWithTag(AiChatTestTags.AI_CHAT_SEND_BUTTON).performClick()

        assertEquals(listOf("Add Chrome"), sentMessages)
    }

    @Test
    fun `enter sends while shift enter inserts a newline`() = runComposeUiTest {
        val sentMessages = mutableListOf<String>()
        setContent {
            MaterialTheme {
                AiChatPane(state = configuredState(onSubmit = { sentMessages += it }), onClose = {}, onOpenSettings = {})
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
    fun `running state disables input editing and submit`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                AiChatPane(state = configuredState(isRunning = true), onClose = {}, onOpenSettings = {})
            }
        }

        onNodeWithTag(AiChatTestTags.AI_CHAT_SEND_BUTTON).assertIsNotEnabled()
        onNodeWithTag(AiChatTestTags.AI_CHAT_STOP_BUTTON).assertIsEnabled()
        onNodeWithTag(AiChatTestTags.AI_CHAT_WORKING_INDICATOR).assertExists()
        onNodeWithTag(AiChatTestTags.AI_CHAT_INPUT).assertIsNotEnabled()
    }

    @Test
    fun `focus returns to input after running completes`() = runComposeUiTest {
        val isRunning = mutableStateOf(true)
        setContent {
            MaterialTheme {
                AiChatPane(state = configuredState(isRunning = isRunning.value), onClose = {}, onOpenSettings = {})
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
    fun `copy transcript button copies full transcript text`() = runComposeUiTest {
        var copiedText = ""
        val state = configuredState(
            transcriptParts = listOf(
                ChatMessagePart.UserText("Add Chrome"),
                ChatMessagePart.AssistantText("I can do that."),
                ChatMessagePart.ChatError("network unavailable"),
                ChatMessagePart.ToolCall(ToolCall("call-save", "save_springboard", "{}"), ToolCallState.OutputAvailable("saved")),
            ),
        )
        setContent {
            MaterialTheme {
                AiChatPane(state = state, onClose = {}, onOpenSettings = {}, onCopyTranscript = { copiedText = it })
            }
        }

        onNodeWithTag(AiChatTestTags.AI_CHAT_COPY_TRANSCRIPT_BUTTON).performClick()

        assertEquals("You: Add Chrome\n\nAssistant: I can do that.\nError: network unavailable", copiedText)
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
        onNodeWithText(AiChatTestHelpText.terse.lines().first(), substring = true).assertExists()
        onNodeWithTag(AiChatTestTags.aiChatScrollbackPaneCopyButton(0)).assertExists()
    }

    @Test
    fun `chat history scrolls to bottom on initial render`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                AiChatPane(
                    state = configuredState(scrollbackPanes = numberedScrollbackPanes(12)),
                    onClose = {},
                    onOpenSettings = {},
                    height = 220.dp,
                )
            }
        }

        onNodeWithText("Request 11").assertIsDisplayed()
    }
}
