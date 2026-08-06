package com.strangeparticle.luther.core.session

import com.strangeparticle.luther.core.client.provider.ChatResponse
import com.strangeparticle.luther.core.client.provider.ProviderErrorType
import com.strangeparticle.luther.core.client.provider.ProviderException
import com.strangeparticle.luther.core.client.provider.StopReason
import com.strangeparticle.luther.core.session.event.AssistantErroredChatHistoryItem
import com.strangeparticle.luther.core.session.event.AssistantRespondedChatHistoryItem
import com.strangeparticle.luther.core.toolcall.ToolCallExecutionContext
import com.strangeparticle.luther.core.toolcall.ToolCallRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test
    fun `provider error during streaming leaves no assistant item and only the error item`() = runTest {
        val fake = AiProviderClientInMemoryFake().apply {
            sendChatException = ProviderException(ProviderErrorType.Network, "network unavailable")
        }
        val manager = createStreamingManager(fake)

        manager.submit("hi").join()

        // The streaming placeholder published before the stream threw must be retracted — only
        // the error item should remain, exactly like the old blocking sendChat(...) throwing
        // before any assistant item was ever appended.
        assertEquals(emptyList(), manager.items.filterIsInstance<AssistantRespondedChatHistoryItem>())
        assertEquals(1, manager.items.filterIsInstance<AssistantErroredChatHistoryItem>().size)

        // The turn is not wedged: a later submit still succeeds normally.
        fake.sendChatException = null
        fake.responseQueue += fake.textOnly("recovered")
        manager.submit("hi again").join()

        assertEquals(ChatMessagePart.AssistantText("recovered"), manager.transcriptParts.last())
    }

    @Test
    fun `terminal response with no text and no tool calls leaves no assistant item`() = runTest {
        val fake = AiProviderClientInMemoryFake().apply {
            responseQueue += ChatResponse(text = null, toolCalls = emptyList(), stopReason = StopReason.Other)
        }
        val manager = createStreamingManager(fake)

        manager.submit("hi").join()

        // Matches the old blocking code's response.text?.let { ... } guard: a terminal response
        // with neither text nor tool calls must not leave a stray assistant item behind.
        assertTrue(manager.items.filterIsInstance<AssistantRespondedChatHistoryItem>().isEmpty())
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `stop mid stream retracts the placeholder and produces no error item`() = runTest {
        val completionGate = CompletableDeferred<Unit>()
        val fake = AiProviderClientInMemoryFake().apply {
            streamDeltas = listOf("Hel", "lo")
            streamCompletionGate = completionGate
            responseQueue += textOnly("Hello")
        }
        val manager = createStreamingManager(fake)

        val job = manager.submit("hi")
        runCurrent()
        // Deltas have landed and the partial assistant message is showing; the stream is now
        // suspended on completionGate, waiting to resolve/emit its terminal Completed event.
        val partial = manager.items.filterIsInstance<AssistantRespondedChatHistoryItem>().last()
        assertEquals("Hello", partial.text)

        manager.stop()
        runCurrent()

        assertTrue(job.isCancelled)
        // The retracted placeholder must leave no assistant item behind...
        assertTrue(manager.items.filterIsInstance<AssistantRespondedChatHistoryItem>().isEmpty())
        // ...and cancellation must propagate as cancellation, not be turned into an error item.
        assertTrue(manager.items.filterIsInstance<AssistantErroredChatHistoryItem>().isEmpty())
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
