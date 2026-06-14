package com.strangeparticle.luther.cmp

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import com.strangeparticle.luther.core.session.ChatMessagePart
import kotlin.test.Test
import kotlin.test.assertEquals

// Pixel-capture and mouse-hover assertions are reliable on the JVM desktop runner, so these stay
// JVM-pinned rather than in commonTest. They still use the same runComposeUiTest framework.
@OptIn(ExperimentalTestApi::class)
internal class AiChatPanePixelTest {

    @Test
    fun `user message bubble uses material primary color`() = runComposeUiTest {
        var expectedBubbleColor = Color.Unspecified
        setContent {
            MaterialTheme {
                expectedBubbleColor = MaterialTheme.colorScheme.primary
                ChatMessagePartRenderer(
                    part = ChatMessagePart.UserText("      "),
                    onApprovalDecision = { _, _ -> },
                )
            }
        }

        val pixels = onNodeWithTag(AiChatTestTags.AI_CHAT_USER_MESSAGE, useUnmergedTree = true).captureToImage().toPixelMap()

        assertEquals(expectedBubbleColor, pixels[pixels.width / 2, pixels.height / 2])
    }

    @Test
    fun `copy controls show hover tooltip text`() = runComposeUiTest {
        val state = configuredState(
            scrollbackPanes = listOf(
                AiChatScrollbackPane.Interaction(
                    requestText = "Add Chrome",
                    responseParts = listOf(ChatMessagePart.AssistantText("Added Chrome.")),
                ),
            ),
        )
        setContent {
            MaterialTheme {
                AiChatPane(state = state, onClose = {}, onOpenSettings = {})
            }
        }

        mainClock.autoAdvance = false
        onNodeWithTag(AiChatTestTags.AI_CHAT_COPY_TRANSCRIPT_BUTTON).performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(1_000)
        onNodeWithText("Copy conversation").assertExists()
    }
}
