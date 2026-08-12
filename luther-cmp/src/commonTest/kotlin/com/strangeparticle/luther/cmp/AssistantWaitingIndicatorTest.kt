package com.strangeparticle.luther.cmp

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import com.strangeparticle.luther.core.session.ChatMessagePart
import kotlin.test.Test

/**
 * The session publishes an assistant item with empty text the moment a turn starts, so the UI has
 * something to grow in place while the response streams. An empty bubble gives the user no signal
 * that anything is happening, so that placeholder renders a waiting indicator until the first
 * token arrives.
 */
@OptIn(ExperimentalTestApi::class)
internal class AssistantWaitingIndicatorTest {

    @Test
    fun `empty assistant message shows the waiting indicator`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                AiChatPane(
                    state = configuredState(
                        transcriptParts = listOf(
                            ChatMessagePart.UserText("Ask something"),
                            ChatMessagePart.AssistantText(""),
                        ),
                    ),
                    onClose = {},
                    onOpenSettings = {},
                    initialHeight = 420.dp,
                )
            }
        }

        onNodeWithTag(AiChatTestTags.AI_CHAT_ASSISTANT_WAITING, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `assistant message with text shows no waiting indicator`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                AiChatPane(
                    state = configuredState(
                        transcriptParts = listOf(
                            ChatMessagePart.UserText("Ask something"),
                            ChatMessagePart.AssistantText("Hi"),
                        ),
                    ),
                    onClose = {},
                    onOpenSettings = {},
                    initialHeight = 420.dp,
                )
            }
        }

        onNodeWithTag(AiChatTestTags.AI_CHAT_ASSISTANT_WAITING, useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag(AiChatTestTags.AI_CHAT_ASSISTANT_MESSAGE, useUnmergedTree = true).assertExists()
    }
}
