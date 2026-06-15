package com.strangeparticle.luther.cmp

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.strangeparticle.luther.core.session.ChatMessagePart
import com.strangeparticle.luther.core.session.ToolCallState

@Composable
internal fun ChatMessagePartRenderer(
    part: ChatMessagePart,
    onApprovalDecision: (toolCallId: String, approved: Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        when (part) {
            is ChatMessagePart.UserText -> UserTextRenderer(part.text)
            is ChatMessagePart.AssistantText -> AssistantTextRenderer(part.text)
            is ChatMessagePart.ChatError -> ErrorMessageRenderer(part.message)
            is ChatMessagePart.ToolCall -> ToolCallRenderer(part, onApprovalDecision)
        }
    }
}

@Composable
private fun UserTextRenderer(text: String) {
    val colors = AiChatPaneDefaults.colors()
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            color = colors.userMessageBubble,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.widthIn(max = 560.dp).testTag(AiChatTestTags.AI_CHAT_USER_MESSAGE),
        ) {
            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) {
                Text(text, color = colors.userMessageText, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun AssistantTextRenderer(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.widthIn(max = 620.dp).testTag(AiChatTestTags.AI_CHAT_ASSISTANT_MESSAGE),
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) {
            Text(text, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ErrorMessageRenderer(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().testTag(AiChatTestTags.AI_CHAT_ERROR_MESSAGE),
    ) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) {
            Text("Error: $message", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ToolCallRenderer(
    part: ChatMessagePart.ToolCall,
    onApprovalDecision: (toolCallId: String, approved: Boolean) -> Unit,
) {
    val toolCall = part.toolCall
    when (val state = part.state) {
        ToolCallState.Pending -> ToolActivitySurface {
            AssistChip(onClick = {}, label = { Text("Working") })
        }
        ToolCallState.ApprovalRequested -> {
            ToolActivitySurface {
                Text("Approval requested", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Row {
                    Button(
                        onClick = { onApprovalDecision(toolCall.id, true) },
                        modifier = Modifier.testTag(AiChatTestTags.AI_APPROVAL_APPLY_BUTTON),
                    ) { Text("Apply") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { onApprovalDecision(toolCall.id, false) },
                        modifier = Modifier.testTag(AiChatTestTags.AI_APPROVAL_CANCEL_BUTTON),
                    ) { Text("Cancel") }
                }
            }
        }
        is ToolCallState.ApprovalResponded -> ToolActivitySurface {
            Text(if (state.approved) "Approval granted" else "Approval denied", fontSize = 13.sp)
        }
        is ToolCallState.OutputAvailable -> Unit
        is ToolCallState.OutputError -> ToolActivitySurface {
            Text(state.message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
        ToolCallState.OutputDenied -> ToolActivitySurface {
            Text("Denied", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ToolActivitySurface(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.widthIn(max = 620.dp).testTag(AiChatTestTags.AI_CHAT_TOOL_ACTIVITY),
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), content = content)
    }
}
