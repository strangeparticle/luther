package com.strangeparticle.luther.core.session.projection

import com.strangeparticle.luther.core.session.ChatMessagePart
import com.strangeparticle.luther.core.session.event.AssistantRespondedChatHistoryItem
import kotlin.test.Test
import kotlin.test.assertEquals

internal class StreamingTranscriptProjectionTest {
    @Test
    fun `empty and partial assistant text project to AssistantText parts`() {
        assertEquals(
            listOf(ChatMessagePart.AssistantText("")),
            buildTranscriptParts(listOf(AssistantRespondedChatHistoryItem(text = "", toolCalls = emptyList()))),
        )
        assertEquals(
            listOf(ChatMessagePart.AssistantText("Hel")),
            buildTranscriptParts(listOf(AssistantRespondedChatHistoryItem(text = "Hel", toolCalls = emptyList()))),
        )
    }
}
