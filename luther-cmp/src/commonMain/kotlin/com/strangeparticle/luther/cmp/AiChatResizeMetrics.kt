package com.strangeparticle.luther.cmp

import androidx.compose.ui.unit.dp

// Sizing for the chat-pane resize handle. These values mirror springboard's
// GridNavHeaderResizeBoundary so the chat pane's resize affordance looks the same as the grid's
// elsewhere in the host. They are duplicated here (rather than imported from the host's gridnav
// constants) so luther-cmp stays self-contained; keep them in sync if the grid affordance changes.
internal object AiChatResizeMetrics {
    val BoundaryThickness = 2.dp
    val ThumbWidth = 28.dp
    val ThumbHeight = 20.dp
    val GripWidth = 20.dp
    val GripHeight = 20.dp
}
