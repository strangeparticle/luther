package com.strangeparticle.luther.cmp

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag

private const val CHAT_PANE_RESIZE_DRAG_HANDLE_CONTENT_DESCRIPTION = "Chat pane resize drag handle"

/**
 * Horizontal boundary rendered above the AI chat pane. The decorative line spans the full
 * width; only the centered grip thumb accepts pointer input. Vertical drag motion is
 * reported via [onDragDelta] in pixels (caller converts to dp using its own density).
 *
 * Styling — including the [AiChatIcons.DragHandle] glyph, thumb dimensions (see
 * [AiChatResizeMetrics]), divider thickness, and vertical-resize hover cursor — intentionally
 * mirrors springboard's grid header resize boundary (GridNavHeaderResizeBoundary) so resize
 * affordances look the same throughout a host that uses both. luther-cmp keeps its own copy of
 * these metrics so it does not depend on the host's gridnav layer.
 */
@Composable
internal fun ChatPaneResizeHandle(
    onDragDelta: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.outline,
            thickness = AiChatResizeMetrics.BoundaryThickness,
        )
        Box(
            modifier = Modifier
                .size(
                    width = AiChatResizeMetrics.ThumbWidth,
                    height = AiChatResizeMetrics.ThumbHeight,
                )
                .testTag(AiChatTestTags.AI_CHAT_RESIZE_HANDLE)
                .pointerHoverIcon(aiChatVerticalResizePointerIcon)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDragDelta(dragAmount.y)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = AiChatIcons.DragHandle,
                contentDescription = CHAT_PANE_RESIZE_DRAG_HANDLE_CONTENT_DESCRIPTION,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(
                    width = AiChatResizeMetrics.GripWidth,
                    height = AiChatResizeMetrics.GripHeight,
                ),
            )
        }
    }
}
