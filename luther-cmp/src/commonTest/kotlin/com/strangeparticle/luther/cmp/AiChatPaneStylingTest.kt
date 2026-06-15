package com.strangeparticle.luther.cmp

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

// Styling is verified by asserting the centralized design tokens map to the intended MaterialTheme
// roles — no pixel capture. These run on every platform and survive layout/theme changes.
@OptIn(ExperimentalTestApi::class)
internal class AiChatPaneStylingTest {

    @Test
    fun `pane and input-section colors map to intended material roles`() = runComposeUiTest {
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
    fun `input border differs between focused and unfocused`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                val colors = AiChatPaneDefaults.colors()
                assertEquals(MaterialTheme.colorScheme.primary, colors.inputBorderFocused)
                assertEquals(MaterialTheme.colorScheme.outline, colors.inputBorderUnfocused)
                assertNotEquals(colors.inputBorderFocused, colors.inputBorderUnfocused)
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
}
