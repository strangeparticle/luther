package com.strangeparticle.luther.cmp

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import com.strangeparticle.luther.core.session.ChatMessagePart
import kotlin.test.Test

// Hover tooltips are a desktop-only interaction (touch platforms have no hover), so this stays on
// the JVM desktop runner. It asserts the tooltip *text* node — not pixels.
@OptIn(ExperimentalTestApi::class)
internal class AiChatPaneHoverTest {

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
