package com.strangeparticle.luther.cmp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Exercises the resize-handle drag math in isolation (the same clamping/density logic the host
// wires up) via the multiplatform touch-input API.
@OptIn(ExperimentalTestApi::class)
internal class ChatPaneResizeHandleTest {

    @Test
    fun `dragging handle upward grows the chat pane height`() = runComposeUiTest {
        val tracker = HeightTracker(startDp = AiChatPaneDefaults.DefaultHeight.value)
        setContent { MaterialTheme { ResizableHarness(tracker) } }

        // Drag up = negative Y. Pane grows because its bottom edge is pinned.
        onNodeWithTag(AiChatTestTags.AI_CHAT_RESIZE_HANDLE).performTouchInput {
            down(center)
            moveBy(Offset(0f, -60f))
            up()
        }

        assertTrue(
            tracker.currentHeightDp > AiChatPaneDefaults.DefaultHeight.value,
            "Dragging up should increase height; was ${AiChatPaneDefaults.DefaultHeight.value}, now ${tracker.currentHeightDp}",
        )
    }

    @Test
    fun `dragging handle downward shrinks the chat pane height`() = runComposeUiTest {
        val tracker = HeightTracker(startDp = AiChatPaneDefaults.DefaultHeight.value)
        setContent { MaterialTheme { ResizableHarness(tracker) } }

        onNodeWithTag(AiChatTestTags.AI_CHAT_RESIZE_HANDLE).performTouchInput {
            down(center)
            moveBy(Offset(0f, 60f))
            up()
        }

        assertTrue(
            tracker.currentHeightDp < AiChatPaneDefaults.DefaultHeight.value,
            "Dragging down should decrease height; was ${AiChatPaneDefaults.DefaultHeight.value}, now ${tracker.currentHeightDp}",
        )
    }

    @Test
    fun `dragging handle aggressively upward clamps to max height`() = runComposeUiTest {
        val tracker = HeightTracker(startDp = AiChatPaneDefaults.DefaultHeight.value)
        setContent { MaterialTheme { ResizableHarness(tracker) } }

        onNodeWithTag(AiChatTestTags.AI_CHAT_RESIZE_HANDLE).performTouchInput {
            down(center)
            moveBy(Offset(0f, -5000f))
            up()
        }

        assertEquals(AiChatPaneDefaults.MaxHeight.value, tracker.currentHeightDp)
    }

    @Test
    fun `dragging handle aggressively downward clamps to min height`() = runComposeUiTest {
        val tracker = HeightTracker(startDp = AiChatPaneDefaults.DefaultHeight.value)
        setContent { MaterialTheme { ResizableHarness(tracker) } }

        onNodeWithTag(AiChatTestTags.AI_CHAT_RESIZE_HANDLE).performTouchInput {
            down(center)
            moveBy(Offset(0f, 5000f))
            up()
        }

        assertEquals(AiChatPaneDefaults.MinHeight.value, tracker.currentHeightDp)
    }

    private class HeightTracker(startDp: Float) {
        var currentHeightDp: Float = startDp
    }

    @Composable
    private fun ResizableHarness(tracker: HeightTracker) {
        var heightDp by remember { mutableStateOf(tracker.currentHeightDp) }
        val density = LocalDensity.current
        Column(modifier = Modifier.fillMaxWidth()) {
            ChatPaneResizeHandle(
                onDragDelta = { deltaPx ->
                    val deltaDp = with(density) { deltaPx.toDp() }
                    val proposed = (heightDp.dp - deltaDp).coerceIn(
                        AiChatPaneDefaults.MinHeight,
                        AiChatPaneDefaults.MaxHeight,
                    )
                    heightDp = proposed.value
                    tracker.currentHeightDp = heightDp
                },
            )
        }
    }
}
