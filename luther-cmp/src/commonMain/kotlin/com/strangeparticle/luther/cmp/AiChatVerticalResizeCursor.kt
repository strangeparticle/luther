package com.strangeparticle.luther.cmp

import androidx.compose.ui.input.pointer.PointerIcon

// Platform pointer icon shown while hovering the chat-pane resize thumb. Desktop returns a vertical
// (north/south) resize cursor; other platforms fall back to the default crosshair-style icon.
internal expect val aiChatVerticalResizePointerIcon: PointerIcon
