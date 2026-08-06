package com.strangeparticle.luther.core.session

import com.strangeparticle.luther.core.client.provider.ChatRequest
import com.strangeparticle.luther.core.client.provider.ChatResponse
import com.strangeparticle.luther.core.client.provider.ChatResponseEvent
import com.strangeparticle.luther.core.client.provider.Model
import com.strangeparticle.luther.core.client.provider.ProviderException
import com.strangeparticle.luther.core.client.provider.StopReason
import com.strangeparticle.luther.core.client.provider.ToolCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonObject

/**
 * Test double for a provider's transport. Exposes [sendChat] / [responseStream] / [listModels]
 * matching the provider shapes (the manager takes both a `suspend (ChatRequest) -> ChatResponse`
 * and a `(ChatRequest) -> Flow<ChatResponseEvent>`, so tests pass `fake::sendChat` and
 * `fake::responseStream`). Tests script the responses they want; the fake records everything
 * that flows through it so assertions can verify request shape without touching Ktor or any
 * HTTP transport.
 *
 * Two ways to script behavior, in priority order:
 *
 * 1. **Per-request handler.** Set [sendChatHandler] to a function that takes the
 *    [ChatRequest] and returns the [ChatResponse]. Useful for tests that need to
 *    inspect the request to decide what to send back (e.g. agent-loop iterations
 *    where the second request looks different from the first).
 * 2. **Response queue.** Push responses to [responseQueue] in order; each
 *    `sendChat()` / `responseStream()` pops the head. Useful for simple linear scenarios.
 *
 * If both are unset and `sendChat()` / `responseStream()` is called, the fake throws to make
 * the missing setup obvious in test output. [responseStream] additionally emits [streamDeltas]
 * (empty by default) as [ChatResponseEvent.TextDelta]s before its terminal
 * [ChatResponseEvent.Completed], letting tests exercise the in-progress streaming path.
 */
internal class AiProviderClientInMemoryFake {

    /** Calls received in order. Inspect after the test to assert request shape. */
    val recordedRequests: MutableList<ChatRequest> = mutableListOf()

    /** Number of times [listModels] has been called. */
    var listModelsCallCount: Int = 0

    /** Optional override that decides what to return based on the actual request. */
    var sendChatHandler: ((ChatRequest) -> ChatResponse)? = null

    /** Linear queue of responses. `sendChat()` pops the head. */
    val responseQueue: ArrayDeque<ChatResponse> = ArrayDeque()

    /** What [listModels] returns. Override per-test. */
    var modelsResponse: List<Model> = emptyList()

    /** When set, [sendChat] throws this instead of returning a response. */
    var sendChatException: ProviderException? = null

    /** When set, [listModels] throws this instead of returning [modelsResponse]. */
    var listModelsException: ProviderException? = null

    suspend fun sendChat(request: ChatRequest): ChatResponse {
        recordedRequests += request
        sendChatException?.let { throw it }
        sendChatHandler?.let { return it(request) }
        if (responseQueue.isEmpty()) {
            throw IllegalStateException(
                "AiProviderClientInMemoryFake.sendChat() called but neither sendChatHandler " +
                    "nor responseQueue was configured. Test setup error."
            )
        }
        return responseQueue.removeFirst()
    }

    /** Deltas [responseStream] emits, in order, before its terminal [ChatResponseEvent.Completed]. Empty by default, i.e. a single Completed emission — matching a non-streaming response. */
    var streamDeltas: List<String> = emptyList()

    /**
     * Streaming counterpart to [sendChat]: records the request, emits [streamDeltas] as
     * [ChatResponseEvent.TextDelta]s, then resolves the terminal response from the same
     * [sendChatHandler] / [responseQueue] source [sendChat] uses and emits it as a
     * [ChatResponseEvent.Completed]. [sendChatException] is honored the same way (thrown before
     * anything is emitted), so tests that script an error see identical behavior whether the
     * session exercises the blocking or the streaming call.
     */
    fun responseStream(request: ChatRequest): Flow<ChatResponseEvent> = flow {
        recordedRequests += request
        sendChatException?.let { throw it }
        for (delta in streamDeltas) {
            emit(ChatResponseEvent.TextDelta(delta))
        }
        val response = sendChatHandler?.invoke(request) ?: if (responseQueue.isNotEmpty()) {
            responseQueue.removeFirst()
        } else {
            throw IllegalStateException(
                "AiProviderClientInMemoryFake.responseStream() called but neither sendChatHandler " +
                    "nor responseQueue was configured. Test setup error."
            )
        }
        emit(ChatResponseEvent.Completed(response))
    }

    suspend fun listModels(): List<Model> {
        listModelsCallCount++
        listModelsException?.let { throw it }
        return modelsResponse
    }

    // -- Convenience builders ----------------------------------------------------

    /** Build a text-only response (model returned prose, no tool calls, finished naturally). */
    fun textOnly(text: String): ChatResponse =
        ChatResponse(
            text = text,
            toolCalls = emptyList(),
            stopReason = StopReason.Stop,
        )

    /** Build a single-tool-call response (model wants the runtime to invoke one tool). */
    fun toolCall(
        toolCallId: String,
        toolName: String,
        arguments: JsonObject,
        text: String? = null,
    ): ChatResponse = ChatResponse(
        text = text,
        toolCalls = listOf(ToolCall(toolCallId, toolName, arguments.toString())),
        stopReason = StopReason.ToolUse,
    )

    /** Build a multi-tool-call response (model proposes several tool invocations in one turn). */
    fun multipleToolCalls(
        calls: List<ToolCall>,
        text: String? = null,
    ): ChatResponse = ChatResponse(
        text = text,
        toolCalls = calls,
        stopReason = StopReason.ToolUse,
    )
}
