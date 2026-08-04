package com.strangeparticle.luther.cmp

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.strangeparticle.luther.core.client.provider.ToolCall
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
internal class AiChatDebugPaneTest {

    // ── Copy-to-clipboard formatters ──────────────────────────────────

    @Test
    fun `copy text for DebugUserMessage includes title and body`() {
        val pane = AiChatScrollbackPane.DebugUserMessage(text = "rename staging to qa", historyIndex = 0)
        assertEquals("Your message:\nrename staging to qa", getScrollbackPaneTextForCopyToClipboard(pane))
    }

    @Test
    fun `copy text for DebugStateSnapshot includes title and json body`() {
        val pane = AiChatScrollbackPane.DebugStateSnapshot(snapshotJson = "{\"a\":1}", historyIndex = 0)
        assertEquals("Application state sent to model:\n{\"a\":1}", getScrollbackPaneTextForCopyToClipboard(pane))
    }

    @Test
    fun `copy text for DebugAssistantMessage with text and tool calls`() {
        val pane = AiChatScrollbackPane.DebugAssistantMessage(
            text = "Renaming the environment.",
            toolCalls = listOf(
                ToolCall(id = "call-1", name = "update_environment", argumentsJson = "{\"id\":\"e1\"}"),
            ),
            historyIndex = 0,
        )
        val expected = "Assistant reply:\nRenaming the environment.\n\nTool call: update_environment\n{\"id\":\"e1\"}"
        assertEquals(expected, getScrollbackPaneTextForCopyToClipboard(pane))
    }

    @Test
    fun `copy text for DebugAssistantMessage with tool calls only omits text line`() {
        val pane = AiChatScrollbackPane.DebugAssistantMessage(
            text = null,
            toolCalls = listOf(
                ToolCall(id = "call-1", name = "save_springboard", argumentsJson = "{}"),
            ),
            historyIndex = 0,
        )
        val expected = "Assistant reply:\n\nTool call: save_springboard\n{}"
        assertEquals(expected, getScrollbackPaneTextForCopyToClipboard(pane))
    }

    @Test
    fun `copy text for DebugToolResult labels the id and includes content`() {
        val pane = AiChatScrollbackPane.DebugToolResult(
            toolCallId = "call-1",
            content = "{\"status\":\"ok\"}",
            historyIndex = 0,
        )
        val text = getScrollbackPaneTextForCopyToClipboard(pane)
        assertTrue(text.startsWith("Tool result returned to model — id=call-1:"), "got: $text")
        assertTrue(text.endsWith("{\"status\":\"ok\"}"), "got: $text")
    }

    // ── Pane rendering ────────────────────────────────────────────────

    @Test
    fun `debug panes render their plain-English titles and per-pane copy buttons`() = runComposeUiTest {
        val panes = listOf(
            AiChatScrollbackPane.DebugStateSnapshot(snapshotJson = "{}", historyIndex = 0),
            AiChatScrollbackPane.DebugUserMessage(text = "do the thing", historyIndex = 1),
            AiChatScrollbackPane.DebugAssistantMessage(
                text = "Ok",
                toolCalls = listOf(ToolCall("call-1", "save_springboard", "{}")),
                historyIndex = 2,
            ),
            AiChatScrollbackPane.DebugToolResult(toolCallId = "call-1", content = "{\"ok\":true}", historyIndex = 3),
        )

        setContent {
            MaterialTheme {
                AiChatPane(
                    state = AiChatPaneState.configured(
                        providerLabel = "OpenAI",
                        modelLabel = "gpt-5",
                        transcriptParts = emptyList(),
                        scrollbackPanes = panes,
                        isRunning = false,
                        onSubmit = {},
                        onStop = {},
                        onApprovalDecision = { _, _ -> },
                    ),
                    onClose = {},
                    onOpenSettings = {},
                    initialHeight = 800.dp,
                )
            }
        }

        onNodeWithText("Your message").assertExists()
        onNodeWithText("Application state sent to model").assertExists()
        onNodeWithText("Assistant reply").assertExists()
        onNodeWithText("Tool call: save_springboard").assertExists()
        onNodeWithText("Tool result returned to model — id=call-1").assertExists()

        // One copy button per pane.
        onAllNodesWithContentDescription("Copy scrollback pane").assertCountEquals(panes.size)
    }
}
