package com.strangeparticle.luther.core.session

import com.strangeparticle.luther.core.client.provider.ChatMessage
import com.strangeparticle.luther.core.client.provider.ProviderErrorType
import com.strangeparticle.luther.core.client.provider.ProviderException
import com.strangeparticle.luther.core.session.event.AssistantRespondedChatHistoryItem
import com.strangeparticle.luther.core.session.event.StateSnapshotAddedChatHistoryItem
import com.strangeparticle.luther.core.session.event.ToolCallCompletedChatHistoryItem
import com.strangeparticle.luther.core.session.event.ToolCallStartedChatHistoryItem
import com.strangeparticle.luther.core.session.event.UserSubmittedChatHistoryItem
import com.strangeparticle.luther.core.client.provider.ToolCall
import com.strangeparticle.luther.core.toolcall.ToolCallExecutionResult
import com.strangeparticle.luther.core.toolcall.ToolCallExecutionContext
import com.strangeparticle.luther.core.toolcall.ToolCallHandler
import com.strangeparticle.luther.core.toolcall.ToolCallHandlerResponse
import com.strangeparticle.luther.core.toolcall.ToolCallRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class AiSessionManagerTest {

    @Test
    fun `submit records canonical user snapshot and assistant events`() = runTest {
        val aiClient = AiProviderClientInMemoryFake().apply {
            responseQueue += textOnly("done")
        }
        val manager = createManager(aiClient, snapshotJson = "{\"tabs\":[]}")

        manager.submit("Add Chrome").join()

        assertEquals(
            listOf(
                StateSnapshotAddedChatHistoryItem("{\"tabs\":[]}"),
                UserSubmittedChatHistoryItem("Add Chrome"),
                AssistantRespondedChatHistoryItem(text = "done", toolCalls = emptyList()),
            ),
            manager.items,
        )
    }

    @Test
    fun `tool call flow records canonical lifecycle events`() = runTest {
        val registry = ToolCallRegistry().apply { register(RecordingToolCallHandler()) }
        val toolCall = ToolCall("call-1", "record_tool", "{}")
        val aiClient = AiProviderClientInMemoryFake().apply {
            responseQueue += multipleToolCalls(listOf(toolCall))
            responseQueue += textOnly("finished")
        }
        val manager = createManager(aiClient, toolCallRegistry = registry)

        manager.submit("Run tool").join()

        assertTrue(manager.items.contains(AssistantRespondedChatHistoryItem(text = null, toolCalls = listOf(toolCall))))
        assertTrue(manager.items.contains(ToolCallStartedChatHistoryItem(toolCall)))
        assertTrue(manager.items.any { it is ToolCallCompletedChatHistoryItem && it.toolCallId == "call-1" })
    }

    @Test
    fun `history token eviction does not delete canonical events`() = runTest {
        val aiClient = AiProviderClientInMemoryFake().apply {
            responseQueue += textOnly("first")
            responseQueue += textOnly("second")
        }
        val manager = createManager(aiClient, maxHistoryTokens = 50)

        manager.submit("a".repeat(200)).join()
        manager.submit("b".repeat(200)).join()

        assertTrue(manager.items.filterIsInstance<UserSubmittedChatHistoryItem>().any { it.text.startsWith("a") })
        assertTrue(manager.items.filterIsInstance<AssistantRespondedChatHistoryItem>().any { it.text == "first" })
    }

    @Test
    fun `submit appends user transcript part`() = runTest {
        val aiClient = AiProviderClientInMemoryFake().apply {
            responseQueue += textOnly("done")
        }
        val manager = createManager(aiClient)

        manager.submit("Add Chrome").join()

        assertEquals(ChatMessagePart.UserText("Add Chrome"), manager.transcriptParts.first())
    }

    @Test
    fun `first request includes current snapshot state`() = runTest {
        val aiClient = AiProviderClientInMemoryFake().apply {
            responseQueue += textOnly("done")
        }
        val manager = createManager(aiClient, snapshotJson = "{\"tabs\":[]}")

        manager.submit("What is open?").join()

        val systemState = assertIs<ChatMessage.SystemState>(aiClient.recordedRequests.single().messages.first())
        assertEquals("{\"tabs\":[]}", systemState.snapshotJson)
    }

    @Test
    fun `text-only response appends assistant transcript and history message`() = runTest {
        val aiClient = AiProviderClientInMemoryFake().apply {
            responseQueue += textOnly("I updated it.")
        }
        val manager = createManager(aiClient)

        manager.submit("Update it").join()

        assertEquals(ChatMessagePart.AssistantText("I updated it."), manager.transcriptParts.last())
        val assistantMessage = assertIs<ChatMessage.Assistant>(manager.history.last())
        assertEquals("I updated it.", assistantMessage.text)
        assertEquals(emptyList(), assistantMessage.toolCalls)
    }

    @Test
    fun `snapshot flag clears after being included`() = runTest {
        val aiClient = AiProviderClientInMemoryFake().apply {
            responseQueue += textOnly("done")
        }
        val manager = createManager(aiClient)

        manager.submit("No-op").join()

        assertFalse(manager.stateChangedSinceLastSnapshotSent)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `tool call response creates pending transcript part before execution completes`() = runTest {
        val releaseTool = CompletableDeferred<Unit>()
        val tool = RecordingToolCallHandler(releaseTool = releaseTool)
        val registry = ToolCallRegistry().apply { register(tool) }
        val aiClient = AiProviderClientInMemoryFake().apply {
            responseQueue += multipleToolCalls(listOf(ToolCall("call-1", "record_tool", "{}")))
            responseQueue += textOnly("finished")
        }
        val manager = createManager(aiClient, toolCallRegistry = registry)

        val job = manager.submit("Run tool")
        runCurrent()

        val toolPart = assertIs<ChatMessagePart.ToolCall>(manager.transcriptParts.last())
        assertEquals(ToolCallState.Pending, toolPart.state)

        releaseTool.complete(Unit)
        job.join()
    }

    @Test
    fun `tool calls execute sequentially through dispatcher`() = runTest {
        val executionOrder = mutableListOf<String>()
        val registry = ToolCallRegistry().apply {
            register(RecordingToolCallHandler(executionOrder = executionOrder))
        }
        val aiClient = AiProviderClientInMemoryFake().apply {
            responseQueue += multipleToolCalls(
                listOf(
                    ToolCall("call-1", "record_tool", "{}"),
                    ToolCall("call-2", "record_tool", "{}"),
                )
            )
            responseQueue += textOnly("finished")
        }
        val manager = createManager(aiClient, toolCallRegistry = registry)

        manager.submit("Run tools").join()

        assertEquals(listOf("call-1", "call-2"), executionOrder)
    }

    @Test
    fun `tool result appends provider tool message to history`() = runTest {
        val registry = ToolCallRegistry().apply { register(RecordingToolCallHandler()) }
        val aiClient = AiProviderClientInMemoryFake().apply {
            responseQueue += multipleToolCalls(listOf(ToolCall("call-1", "record_tool", "{}")))
            responseQueue += textOnly("finished")
        }
        val manager = createManager(aiClient, toolCallRegistry = registry)

        manager.submit("Run tool").join()

        val toolResult = manager.history.filterIsInstance<ChatMessage.ToolResult>().single()
        assertEquals("call-1", toolResult.toolCallId)
        assertEquals("{\"success\":true,\"message\":\"handled call-1\"}", toolResult.content)
    }

    @Test
    fun `tool result uses transcript output for visible chat state`() = runTest {
        val registry = ToolCallRegistry().apply { register(TranscriptOutputToolCallHandler()) }
        val aiClient = AiProviderClientInMemoryFake().apply {
            responseQueue += multipleToolCalls(listOf(ToolCall("call-1", "transcript_output_tool", "{}")))
            responseQueue += textOnly("finished")
        }
        val manager = createManager(aiClient, toolCallRegistry = registry)

        manager.submit("Run tool").join()

        val toolPart = manager.transcriptParts.filterIsInstance<ChatMessagePart.ToolCall>().single()
        val state = assertIs<ToolCallState.OutputAvailable>(toolPart.state)
        assertEquals("Applied.", state.output)

        val toolResult = manager.history.filterIsInstance<ChatMessage.ToolResult>().single()
        assertEquals("{\"success\":true}", toolResult.content)
    }

    @Test
    fun `tool call response triggers follow-up provider request and stops after text response`() = runTest {
        val registry = ToolCallRegistry().apply { register(RecordingToolCallHandler()) }
        val aiClient = AiProviderClientInMemoryFake().apply {
            responseQueue += multipleToolCalls(listOf(ToolCall("call-1", "record_tool", "{}")))
            responseQueue += textOnly("finished")
        }
        val manager = createManager(aiClient, toolCallRegistry = registry)

        manager.submit("Run tool").join()

        assertEquals(2, aiClient.recordedRequests.size)
        assertEquals(ChatMessagePart.AssistantText("finished"), manager.transcriptParts.last())
    }

    @Test
    fun `terminal message tool response does not trigger follow-up provider request`() = runTest {
        val registry = ToolCallRegistry().apply { register(TerminalMessageToolCallHandler()) }
        var requestCount = 0
        val aiClient = AiProviderClientInMemoryFake().apply {
            sendChatHandler = {
                requestCount += 1
                if (requestCount == 1) {
                    multipleToolCalls(listOf(ToolCall("call-1", "terminal_message_tool", "{}")))
                } else {
                    throw ProviderException(ProviderErrorType.RateLimit, "rate limited")
                }
            }
        }
        val manager = createManager(aiClient, toolCallRegistry = registry)

        manager.submit("Explain").join()

        assertEquals(1, aiClient.recordedRequests.size)
        assertEquals(
            listOf(
                ChatMessagePart.UserText("Explain"),
                ChatMessagePart.AssistantText("Here is the answer."),
            ),
            manager.transcriptParts,
        )
    }

    @Test
    fun `second request without mutation does not inject another snapshot`() = runTest {
        val aiClient = AiProviderClientInMemoryFake().apply {
            responseQueue += textOnly("first")
            responseQueue += textOnly("second")
        }
        val manager = createManager(aiClient)

        manager.submit("First").join()
        manager.submit("Second").join()

        val systemStates = aiClient.recordedRequests.last().messages.filterIsInstance<ChatMessage.SystemState>()
        assertEquals(1, systemStates.size)
    }

    @Test
    fun `mutating tool marks state changed and follow-up request includes fresh snapshot`() = runTest {
        var snapshotCount = 0
        val snapshotProvider = object : AiSessionSnapshotProvider {
            override fun getSnapshotJson(): String {
                snapshotCount += 1
                return "{\"snapshot\":$snapshotCount}"
            }
        }
        val registry = ToolCallRegistry().apply { register(StateChangingToolCallHandler()) }
        val aiClient = AiProviderClientInMemoryFake().apply {
            responseQueue += multipleToolCalls(listOf(ToolCall("call-1", "state_changing_tool", "{}")))
            responseQueue += textOnly("finished")
        }
        val manager = createManager(aiClient, snapshotProvider = snapshotProvider, toolCallRegistry = registry)

        manager.submit("Mutate").join()

        val systemStates = aiClient.recordedRequests.last().messages.filterIsInstance<ChatMessage.SystemState>()
        assertEquals(listOf("{\"snapshot\":1}", "{\"snapshot\":2}"), systemStates.map { it.snapshotJson })
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `confirmation gated tool transitions to ApprovalRequested before user decides`() = runTest {
        val registry = ToolCallRegistry().apply { register(ApprovalGatedToolCallHandler()) }
        val aiClient = AiProviderClientInMemoryFake().apply {
            responseQueue += multipleToolCalls(listOf(ToolCall("call-save", "approval_gated_tool", "{}")))
            responseQueue += textOnly("finished")
        }
        var transcriptChangeCount = 0
        val manager = createManager(
            aiClient,
            toolCallRegistry = registry,
            onTranscriptChanged = { transcriptChangeCount += 1 },
        )

        val job = manager.submit("Save it")
        runCurrent()

        val part = assertIs<ChatMessagePart.ToolCall>(manager.transcriptParts.last())
        assertEquals(ToolCallState.ApprovalRequested, part.state)
        assertTrue(transcriptChangeCount > 0, "approval request must notify the UI to recompose")

        manager.onApprovalDecision("call-save", approved = true)
        job.join()
    }

    @Test
    fun `onApprovalDecision true resumes the tool and produces OutputAvailable`() = runTest {
        val registry = ToolCallRegistry().apply { register(ApprovalGatedToolCallHandler()) }
        val aiClient = AiProviderClientInMemoryFake().apply {
            responseQueue += multipleToolCalls(listOf(ToolCall("call-save", "approval_gated_tool", "{}")))
            responseQueue += textOnly("finished")
        }
        val manager = createManager(aiClient, toolCallRegistry = registry)

        val job = manager.submit("Save it")
        @OptIn(ExperimentalCoroutinesApi::class)
        runCurrent()
        manager.onApprovalDecision("call-save", approved = true)
        job.join()

        val toolPart = manager.transcriptParts.filterIsInstance<ChatMessagePart.ToolCall>().single()
        val state = assertIs<ToolCallState.OutputAvailable>(toolPart.state)
        assertEquals("{\"success\":true,\"message\":\"saved\"}", state.output)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `onApprovalDecision transitions approval card to ApprovalResponded before tool completes`() = runTest {
        val releaseAfterApproval = CompletableDeferred<Unit>()
        val registry = ToolCallRegistry().apply {
            register(SlowApprovalGatedToolCallHandler(releaseAfterApproval))
        }
        val aiClient = AiProviderClientInMemoryFake().apply {
            responseQueue += multipleToolCalls(listOf(ToolCall("call-save", "slow_approval_gated_tool", "{}")))
            responseQueue += textOnly("finished")
        }
        val manager = createManager(aiClient, toolCallRegistry = registry)

        val job = manager.submit("Save it")
        runCurrent()
        manager.onApprovalDecision("call-save", approved = true)
        runCurrent()

        val respondedPart = manager.transcriptParts.filterIsInstance<ChatMessagePart.ToolCall>().single()
        assertEquals(ToolCallState.ApprovalResponded(approved = true), respondedPart.state)

        releaseAfterApproval.complete(Unit)
        job.join()
    }

    @Test
    fun `onApprovalDecision false resumes the tool and produces OutputDenied`() = runTest {
        val registry = ToolCallRegistry().apply { register(ApprovalGatedToolCallHandler()) }
        val aiClient = AiProviderClientInMemoryFake().apply {
            responseQueue += multipleToolCalls(listOf(ToolCall("call-save", "approval_gated_tool", "{}")))
            responseQueue += textOnly("finished")
        }
        val manager = createManager(aiClient, toolCallRegistry = registry)

        val job = manager.submit("Save it")
        @OptIn(ExperimentalCoroutinesApi::class)
        runCurrent()
        manager.onApprovalDecision("call-save", approved = false)
        job.join()

        val toolPart = manager.transcriptParts.filterIsInstance<ChatMessagePart.ToolCall>().single()
        assertIs<ToolCallState.OutputDenied>(toolPart.state)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `unresolved approval keeps the current request job active`() = runTest {
        val registry = ToolCallRegistry().apply { register(ApprovalGatedToolCallHandler()) }
        val aiClient = AiProviderClientInMemoryFake().apply {
            responseQueue += multipleToolCalls(listOf(ToolCall("call-save", "approval_gated_tool", "{}")))
            responseQueue += textOnly("finished")
        }
        val manager = createManager(aiClient, toolCallRegistry = registry)

        val job = manager.submit("Save it")
        runCurrent()

        assertFalse(job.isCompleted, "job must stay active while approval is unresolved")
        // Verify the second provider request hasn't happened yet either.
        assertEquals(1, aiClient.recordedRequests.size)

        manager.onApprovalDecision("call-save", approved = true)
        job.join()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `submit rejects a new turn while a request is active`() = runTest {
        val releaseTool = CompletableDeferred<Unit>()
        val registry = ToolCallRegistry().apply { register(RecordingToolCallHandler(releaseTool = releaseTool)) }
        val aiClient = AiProviderClientInMemoryFake().apply {
            responseQueue += multipleToolCalls(listOf(ToolCall("call-1", "record_tool", "{}")))
            responseQueue += textOnly("finished")
        }
        val manager = createManager(aiClient, toolCallRegistry = registry)

        val firstJob = manager.submit("First")
        runCurrent()

        assertFailsWith<IllegalStateException> { manager.submit("Second") }
        assertEquals(1, manager.transcriptParts.filterIsInstance<ChatMessagePart.UserText>().size)

        releaseTool.complete(Unit)
        firstJob.join()
    }

    @Test
    fun `provider error appends chat error and allows next submit`() = runTest {
        val aiClient = AiProviderClientInMemoryFake().apply {
            sendChatException = ProviderException(ProviderErrorType.Network, "network unavailable")
        }
        val manager = createManager(aiClient)

        manager.submit("First").join()

        assertEquals(ChatMessagePart.ChatError("network unavailable"), manager.transcriptParts.last())

        aiClient.sendChatException = null
        aiClient.responseQueue += aiClient.textOnly("recovered")
        manager.submit("Second").join()

        assertEquals(ChatMessagePart.AssistantText("recovered"), manager.transcriptParts.last())
    }

    @Test
    fun `non-mutating respond style tool does not mark state changed`() = runTest {
        val registry = ToolCallRegistry().apply { register(RecordingToolCallHandler()) }
        val aiClient = AiProviderClientInMemoryFake().apply {
            responseQueue += multipleToolCalls(listOf(ToolCall("call-1", "record_tool", "{}")))
            responseQueue += textOnly("finished")
        }
        val manager = createManager(aiClient, toolCallRegistry = registry)

        manager.submit("Explain").join()

        val systemStates = aiClient.recordedRequests.last().messages.filterIsInstance<ChatMessage.SystemState>()
        assertEquals(1, systemStates.size)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `stop cancels the active request job`() = runTest {
        val releaseTool = CompletableDeferred<Unit>()
        val registry = ToolCallRegistry().apply { register(RecordingToolCallHandler(releaseTool = releaseTool)) }
        val aiClient = AiProviderClientInMemoryFake().apply {
            responseQueue += multipleToolCalls(listOf(ToolCall("call-1", "record_tool", "{}")))
            responseQueue += textOnly("finished")
        }
        val manager = createManager(aiClient, toolCallRegistry = registry)

        val job = manager.submit("Run tool")
        runCurrent()
        assertFalse(job.isCompleted, "job should be active while the tool is suspended")

        manager.stop()
        runCurrent()
        assertTrue(job.isCancelled)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `stop marks unresolved approval request as denied and ignores later decisions`() = runTest {
        val registry = ToolCallRegistry().apply { register(ApprovalGatedToolCallHandler()) }
        val aiClient = AiProviderClientInMemoryFake().apply {
            responseQueue += multipleToolCalls(listOf(ToolCall("call-save", "approval_gated_tool", "{}")))
            responseQueue += textOnly("finished")
        }
        val manager = createManager(aiClient, toolCallRegistry = registry)

        val job = manager.submit("Save it")
        runCurrent()
        manager.stop()
        runCurrent()
        manager.onApprovalDecision("call-save", approved = true)

        assertTrue(job.isCancelled)
        val toolPart = manager.transcriptParts.filterIsInstance<ChatMessagePart.ToolCall>().single()
        assertIs<ToolCallState.OutputDenied>(toolPart.state)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `cancellation does not roll back transcript parts from already-executed tools`() = runTest {
        // Tool runs to the suspension point (records its execution), then we cancel.
        // The transcript ToolCall part it produced must remain — cancellation is not a rollback.
        val releaseTool = CompletableDeferred<Unit>()
        val registry = ToolCallRegistry().apply { register(RecordingToolCallHandler(releaseTool = releaseTool)) }
        val aiClient = AiProviderClientInMemoryFake().apply {
            responseQueue += multipleToolCalls(listOf(ToolCall("call-1", "record_tool", "{}")))
        }
        val manager = createManager(aiClient, toolCallRegistry = registry)

        val job = manager.submit("Run tool")
        @OptIn(ExperimentalCoroutinesApi::class)
        runCurrent()
        val toolPartsBeforeStop = manager.transcriptParts.filterIsInstance<ChatMessagePart.ToolCall>()
        assertEquals(1, toolPartsBeforeStop.size, "tool's Pending transcript part should be posted before suspension")

        manager.stop()
        @OptIn(ExperimentalCoroutinesApi::class)
        runCurrent()
        assertTrue(job.isCancelled)
        // Transcript part is still there — no rollback.
        val toolPartsAfter = manager.transcriptParts.filterIsInstance<ChatMessagePart.ToolCall>()
        assertEquals(1, toolPartsAfter.size)
        assertEquals("call-1", toolPartsAfter.single().toolCall.id)
    }

    @Test
    fun `submit after cancellation starts a fresh turn`() = runTest {
        val releaseTool = CompletableDeferred<Unit>()
        val registry = ToolCallRegistry().apply { register(RecordingToolCallHandler(releaseTool = releaseTool)) }
        val aiClient = AiProviderClientInMemoryFake().apply {
            // First submit: tool call response, then suspends in the tool (cancelled before the next request).
            responseQueue += multipleToolCalls(listOf(ToolCall("call-1", "record_tool", "{}")))
            // Second submit consumes this response.
            responseQueue += textOnly("second submit done")
        }
        val manager = createManager(aiClient, toolCallRegistry = registry)

        val firstJob = manager.submit("First")
        @OptIn(ExperimentalCoroutinesApi::class)
        runCurrent()
        manager.stop()
        @OptIn(ExperimentalCoroutinesApi::class)
        runCurrent()
        assertTrue(firstJob.isCancelled)

        val secondJob = manager.submit("Second")
        secondJob.join()
        assertEquals(ChatMessagePart.AssistantText("second submit done"), manager.transcriptParts.last())
    }

    // ── Task 34: history eviction ─────────────────────────────────────────

    @Test
    fun `oldest user-assistant turn is evicted when history exceeds token budget`() = runTest {
        val aiClient = AiProviderClientInMemoryFake().apply {
            responseQueue += textOnly("first reply")
            responseQueue += textOnly("second reply")
            responseQueue += textOnly("third reply")
        }
        // 50 tokens ≈ 200 chars total. Two turns will already be over budget.
        val manager = createManager(aiClient, maxHistoryTokens = 50)

        manager.submit("a".repeat(200)).join()
        manager.submit("b".repeat(200)).join()
        manager.submit("c".repeat(200)).join()

        // The request sent for the third submit should NOT include the first user message.
        val historySentOnThirdRequest = aiClient.recordedRequests.last().messages
        val userTextsSent = historySentOnThirdRequest
            .filterIsInstance<ChatMessage.User>()
            .map { it.text }
        assertTrue(userTextsSent.none { it.startsWith("a") }, "first user message must be evicted")
        assertTrue(userTextsSent.any { it.startsWith("c") }, "current user message must remain")
    }

    @Test
    fun `tool result messages are evicted with the assistant turn that requested them`() = runTest {
        val registry = ToolCallRegistry().apply { register(RecordingToolCallHandler()) }
        val aiClient = AiProviderClientInMemoryFake().apply {
            // Turn 1: user message → tool call → tool result → assistant text
            responseQueue += multipleToolCalls(listOf(ToolCall("call-1", "record_tool", "{}")))
            responseQueue += textOnly("first done")
            // Turn 2: simple text reply
            responseQueue += textOnly("second done")
            // Turn 3: simple text reply
            responseQueue += textOnly("third done")
        }
        val manager = createManager(aiClient, toolCallRegistry = registry, maxHistoryTokens = 50)

        manager.submit("a".repeat(200)).join()  // Turn 1: heavy, has tool result
        manager.submit("b".repeat(200)).join()  // Turn 2: forces eviction of turn 1
        manager.submit("c".repeat(50)).join()

        val historySent = aiClient.recordedRequests.last().messages
        // Turn 1's tool result must be gone — it was bundled with turn 1's user message.
        val toolResults = historySent.filterIsInstance<ChatMessage.ToolResult>()
        assertTrue(toolResults.none { it.toolCallId == "call-1" }, "tool result must be evicted with its turn")
    }

    @Test
    fun `old snapshot injections are evicted with the logical turn they preceded`() = runTest {
        var snapshotCount = 0
        val snapshotProvider = object : AiSessionSnapshotProvider {
            override fun getSnapshotJson(): String {
                snapshotCount += 1
                return "{\"snap\":$snapshotCount}"
            }
        }
        val registry = ToolCallRegistry().apply { register(StateChangingToolCallHandler()) }
        val aiClient = AiProviderClientInMemoryFake().apply {
            // Turn 1: mutating tool, triggers snapshot before turn 2 starts.
            responseQueue += multipleToolCalls(listOf(ToolCall("call-1", "state_changing_tool", "{}")))
            responseQueue += textOnly("first done")
            // Turn 2: another mutation so a third snapshot is injected before turn 3.
            responseQueue += multipleToolCalls(listOf(ToolCall("call-2", "state_changing_tool", "{}")))
            responseQueue += textOnly("second done")
            responseQueue += textOnly("third done")
        }
        val manager = createManager(
            aiClient,
            snapshotProvider = snapshotProvider,
            toolCallRegistry = registry,
            maxHistoryTokens = 50,
        )

        manager.submit("a".repeat(200)).join()
        manager.submit("b".repeat(200)).join()
        manager.submit("c".repeat(50)).join()

        val historySent = aiClient.recordedRequests.last().messages
        val snapshotsSent = historySent.filterIsInstance<ChatMessage.SystemState>().map { it.snapshotJson }
        // The first snapshot ({"snap":1}) belonged to turn 1; it must be gone.
        assertTrue(snapshotsSent.none { it == "{\"snap\":1}" }, "snapshot from evicted turn 1 must also be evicted")
    }

    private fun TestScope.createManager(
        aiClient: AiProviderClientInMemoryFake,
        snapshotJson: String = "{\"springboards\":[]}",
        snapshotProvider: AiSessionSnapshotProvider = object : AiSessionSnapshotProvider {
            override fun getSnapshotJson(): String = snapshotJson
        },
        toolCallRegistry: ToolCallRegistry = ToolCallRegistry(),
        maxHistoryTokens: Int = AiSessionManager.DEFAULT_MAX_HISTORY_TOKENS,
        onTranscriptChanged: () -> Unit = {},
    ): AiSessionManager = AiSessionManager(
        sendChat = aiClient::sendChat,
        toolCallRegistry = toolCallRegistry,
        snapshotProvider = snapshotProvider,
        toolCallExecutionContextFactory = object : AiSessionToolCallExecutionContextFactory {
            override fun createToolCallExecutionContext(
                onStateChanged: () -> Unit,
                awaitUserApproval: suspend (toolCallId: String) -> Boolean,
            ): ToolCallExecutionContext =
                TestToolCallExecutionContext(onStateChanged, awaitUserApproval)
        },
        systemPromptProvider = { "system prompt" },
        modelIdProvider = { "test-model" },
        coroutineScope = this,
        maxHistoryTokens = maxHistoryTokens,
        onTranscriptChanged = onTranscriptChanged,
    )

    private class TestToolCallExecutionContext(
        val markStateChanged: () -> Unit,
        val awaitUserApproval: suspend (toolCallId: String) -> Boolean,
    ) : ToolCallExecutionContext

    private class RecordingToolCallHandler(
        private val executionOrder: MutableList<String> = mutableListOf(),
        private val releaseTool: CompletableDeferred<Unit>? = null,
    ) : ToolCallHandler {
        override val providerToolId = "record_tool"
        override val description = "Record tool execution."
        override val schema: JsonObject = buildJsonObject {}

        override suspend fun executeToolCallHandler(
            toolCallId: String,
            argumentsAsJsonString: String,
            context: ToolCallExecutionContext,
        ): ToolCallHandlerResponse {
            executionOrder += toolCallId
            releaseTool?.await()
            return ToolCallExecutionResult(success = true, message = "handled $toolCallId")
        }
    }

    private class TerminalMessageToolCallHandler : ToolCallHandler {
        override val providerToolId = "terminal_message_tool"
        override val description = "Return a final user-facing message."
        override val schema: JsonObject = buildJsonObject {}

        override suspend fun executeToolCallHandler(
            toolCallId: String,
            argumentsAsJsonString: String,
            context: ToolCallExecutionContext,
        ): ToolCallHandlerResponse = object : ToolCallHandlerResponse {
            override val endsTurn: Boolean = true
            override fun toProviderMessageContent(json: kotlinx.serialization.json.Json): String =
                "{\"success\":true,\"message\":\"Here is the answer.\"}"
            override fun toTranscriptOutput(providerMessageContent: String): String = "Here is the answer."
        }
    }

    private class TranscriptOutputToolCallHandler : ToolCallHandler {
        override val providerToolId = "transcript_output_tool"
        override val description = "Return different provider and transcript output."
        override val schema: JsonObject = buildJsonObject {}

        override suspend fun executeToolCallHandler(
            toolCallId: String,
            argumentsAsJsonString: String,
            context: ToolCallExecutionContext,
        ): ToolCallHandlerResponse = object : ToolCallHandlerResponse {
            override fun toProviderMessageContent(json: kotlinx.serialization.json.Json): String = "{\"success\":true}"
            override fun toTranscriptOutput(providerMessageContent: String): String = "Applied."
        }
    }

    private class StateChangingToolCallHandler : ToolCallHandler {
        override val providerToolId = "state_changing_tool"
        override val description = "Record state-changing tool execution."
        override val schema: JsonObject = buildJsonObject {}

        override suspend fun executeToolCallHandler(
            toolCallId: String,
            argumentsAsJsonString: String,
            context: ToolCallExecutionContext,
        ): ToolCallHandlerResponse {
            assertIs<TestToolCallExecutionContext>(context).markStateChanged()
            return ToolCallExecutionResult(success = true, message = "changed")
        }
    }

    /**
     * Mirrors SaveSpringboardToolCallHandler's approval flow: requires confirmation,
     * suspends on `awaitUserApproval`, and on denial returns a structured error rather
     * than throwing. The session manager translates denial into [ToolCallState.OutputDenied].
     */
    private class ApprovalGatedToolCallHandler : ToolCallHandler {
        override val providerToolId = "approval_gated_tool"
        override val description = "Confirmation-gated tool used for approval-flow tests."
        override val requiresUserConfirmation: Boolean = true
        override val schema: JsonObject = buildJsonObject {}

        override suspend fun executeToolCallHandler(
            toolCallId: String,
            argumentsAsJsonString: String,
            context: ToolCallExecutionContext,
        ): ToolCallHandlerResponse {
            val approved = assertIs<TestToolCallExecutionContext>(context).awaitUserApproval(toolCallId)
            return if (approved) {
                ToolCallExecutionResult(success = true, message = "saved")
            } else {
                ToolCallExecutionResult(success = false, message = "User declined", code = "user_declined")
            }
        }
    }

    private class SlowApprovalGatedToolCallHandler(
        private val releaseAfterApproval: CompletableDeferred<Unit>,
    ) : ToolCallHandler {
        override val providerToolId = "slow_approval_gated_tool"
        override val description = "Confirmation-gated tool that suspends after approval."
        override val requiresUserConfirmation: Boolean = true
        override val schema: JsonObject = buildJsonObject {}

        override suspend fun executeToolCallHandler(
            toolCallId: String,
            argumentsAsJsonString: String,
            context: ToolCallExecutionContext,
        ): ToolCallHandlerResponse {
            val approved = assertIs<TestToolCallExecutionContext>(context).awaitUserApproval(toolCallId)
            if (!approved) {
                return ToolCallExecutionResult(success = false, message = "User declined", code = "user_declined")
            }
            releaseAfterApproval.await()
            return ToolCallExecutionResult(success = true, message = "saved")
        }
    }
}
