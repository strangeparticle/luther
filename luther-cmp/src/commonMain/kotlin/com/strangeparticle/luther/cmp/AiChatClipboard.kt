package com.strangeparticle.luther.cmp

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

// Default copy-to-clipboard action for the chat component, backed by Compose's multiplatform
// clipboard. A host may still override the copy behavior by passing its own lambda; this keeps the
// component turnkey without depending on a host platform clipboard function.
@Composable
internal fun rememberCopyToClipboard(): (String) -> Unit {
    val clipboardManager = LocalClipboardManager.current
    return { text -> clipboardManager.setText(AnnotatedString(text)) }
}
