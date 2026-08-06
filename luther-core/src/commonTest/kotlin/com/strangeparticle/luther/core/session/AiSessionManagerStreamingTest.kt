package com.strangeparticle.luther.core.session

import com.strangeparticle.luther.core.session.event.AssistantRespondedChatHistoryItem
import com.strangeparticle.luther.core.toolcall.ToolCallExecutionContext
import com.strangeparticle.luther.core.toolcall.ToolCallRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers `AiSessionManager.runRequestLoop` consuming `responseStream` instead of the blocking
 * `sendChat`: the assistant message must grow in place (one item, replaced on each delta) rather
 * than being appended once at the end, and the terminal `Completed.response` must feed the
 * existing tool-execution loop exactly as the old blocking response did.
 */
internal class AiSessionManagerStreamingTest {

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `streaming grows the assistant message in place and finalizes it`() = runTest {
        val fake = AiProviderClientInMemoryFake().apply {
            streamDeltas = listOf("Hel", "lo")
            responseQueue += textOnly("Hello")
        }
        val manager = createStreamingManager(fake)
        val job = manager.submit("hi")

        runCurrent()
        // After the deltas emit, the last item is a growing assistant message (single item, not duplicated).
        val partial = manager.items.filterIsInstance<AssistantRespondedChatHistoryItem>().last()
        assertEquals("Hello", partial.text)

        job.join()
        assertEquals(ChatMessagePart.AssistantText("Hello"), manager.transcriptParts.last())
        assertEquals(1, manager.items.filterIsInstance<AssistantRespondedChatHistoryItem>().size)
    }

    private fun TestScope.createStreamingManager(fake: AiProviderClientInMemoryFake): AiSessionManager =
        AiSessionManager(
            sendChat = fake::sendChat,
            responseStream = fake::responseStream,
            toolCallRegistry = ToolCallRegistry(),
            snapshotProvider = object : AiSessionSnapshotProvider {
                override fun getSnapshotJson(): String = "{}"
            },
            toolCallExecutionContextFactory = object : AiSessionToolCallExecutionContextFactory {
                override fun createToolCallExecutionContext(
                    onStateChanged: () -> Unit,
                    awaitUserApproval: suspend (toolCallId: String) -> Boolean,
                ): ToolCallExecutionContext = object : ToolCallExecutionContext {}
            },
            systemPromptProvider = { "system prompt" },
            modelIdProvider = { "test-model" },
            coroutineScope = this,
        )
}
