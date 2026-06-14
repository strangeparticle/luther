package com.strangeparticle.luther.core.session

import com.strangeparticle.luther.core.client.provider.ChatMessage
import com.strangeparticle.luther.core.client.provider.ChatRequest
import com.strangeparticle.luther.core.client.provider.ChatResponse
import com.strangeparticle.luther.core.session.event.ChatHistoryItem
import com.strangeparticle.luther.core.session.event.AssistantErroredChatHistoryItem
import com.strangeparticle.luther.core.session.event.AssistantRespondedChatHistoryItem
import com.strangeparticle.luther.core.session.event.StateSnapshotAddedChatHistoryItem
import com.strangeparticle.luther.core.session.event.ToolApprovalRequestedChatHistoryItem
import com.strangeparticle.luther.core.session.event.ToolApprovalRespondedChatHistoryItem
import com.strangeparticle.luther.core.session.event.ToolCallCompletedChatHistoryItem
import com.strangeparticle.luther.core.session.event.ToolCallDeniedChatHistoryItem
import com.strangeparticle.luther.core.session.event.ToolCallFailedChatHistoryItem
import com.strangeparticle.luther.core.session.event.ToolCallStartedChatHistoryItem
import com.strangeparticle.luther.core.session.event.UserSubmittedChatHistoryItem
import com.strangeparticle.luther.core.session.projection.buildProviderHistory
import com.strangeparticle.luther.core.session.projection.buildTranscriptParts
import com.strangeparticle.luther.core.toolcall.ToolCallDispatcher
import com.strangeparticle.luther.core.toolcall.ToolCallExecutionResult
import com.strangeparticle.luther.core.toolcall.ToolCallHandlerResponse
import com.strangeparticle.luther.core.toolcall.ToolCallRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal class AiSessionManager(
    private val sendChat: suspend (ChatRequest) -> ChatResponse,
    private val toolCallRegistry: ToolCallRegistry,
    private val snapshotProvider: AiSessionSnapshotProvider,
    private val toolCallExecutionContextFactory: AiSessionToolCallExecutionContextFactory,
    private val systemPromptProvider: () -> String,
    private val modelIdProvider: () -> String,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    /**
     * Approximate token budget for outgoing request history. When the estimated
     * token count exceeds this, the oldest complete turn group is evicted
     * (repeating until under the budget). Estimation uses `text.length / 4`.
     */
    private val maxHistoryTokens: Int = DEFAULT_MAX_HISTORY_TOKENS,
    groupsProvider: (() -> List<ChatHistoryGroup>)? = null,
    updateGroups: ((List<ChatHistoryGroup>) -> Unit)? = null,
    private val onTranscriptChanged: () -> Unit = {},
    private val onTurnStart: () -> Unit = {},
    private val onTurnEnd: () -> Unit = {},
) {
    private val mutableGroups = mutableListOf<ChatHistoryGroup>()
    private val resolvedGroupsProvider: () -> List<ChatHistoryGroup> = groupsProvider ?: { mutableGroups.toList() }
    private val resolvedUpdateGroups: (List<ChatHistoryGroup>) -> Unit = updateGroups ?: { newGroups ->
        mutableGroups.clear()
        mutableGroups.addAll(newGroups)
    }
    private val pendingApprovals = mutableMapOf<String, CompletableDeferred<Boolean>>()
    private val approvalDecisions = mutableMapOf<String, Boolean>()
    private val toolCallDispatcher = ToolCallDispatcher(toolCallRegistry)

    val groups: List<ChatHistoryGroup> get() = resolvedGroupsProvider()
    val items: List<ChatHistoryItem> get() = groups.flatMap { it.items }
    val transcriptParts: List<ChatMessagePart> get() = buildTranscriptParts(items)
    val history: List<ChatMessage> get() = buildProviderHistory(items)

    private var currentRequestJob: Job? = null
    var stateChangedSinceLastSnapshotSent = true
        private set

    fun markExternalStateChange() {
        stateChangedSinceLastSnapshotSent = true
    }

    /**
     * Dispatches a registered tool-call by name from a local chat command (e.g. `/undo`, `/redo`)
     * rather than from a model turn. Builds a tool-call execution context that mirrors the one in
     * [runRequestLoop] (state changes flip the snapshot flag; approval is auto-granted since the
     * user is the one issuing the command) and returns the handler's response so the caller can
     * surface its human-readable message. Refuses to run while a request turn is active.
     */
    suspend fun executeLocalToolCall(toolName: String): ToolCallHandlerResponse {
        check(currentRequestJob?.isActive != true) { "An AI request is already in progress." }
        val context = toolCallExecutionContextFactory.createToolCallExecutionContext(
            onStateChanged = { stateChangedSinceLastSnapshotSent = true },
            awaitUserApproval = { true },
        )
        return toolCallDispatcher.execute(
            toolCallId = "local-$toolName",
            providerToolId = toolName,
            argumentsAsJsonString = "{}",
            context = context,
        )
    }

    fun submit(userText: String): Job {
        check(currentRequestJob?.isActive != true) { "An AI request is already in progress." }

        val job = coroutineScope.launch {
            onTurnStart()
            try {
                startNewAiInteractionGroup()
                appendSnapshotIfChanged()
                appendItemToCurrentGroup(UserSubmittedChatHistoryItem(userText))

                runRequestLoop()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                appendItemToCurrentGroup(AssistantErroredChatHistoryItem(e.message ?: "AI request failed"))
            } finally {
                onTurnEnd()
            }
        }
        currentRequestJob = job
        return job
    }

    fun onApprovalDecision(toolCallId: String, approved: Boolean) {
        val deferred = pendingApprovals[toolCallId] ?: return
        approvalDecisions[toolCallId] = approved
        appendItemToCurrentGroup(ToolApprovalRespondedChatHistoryItem(toolCallId, approved))
        deferred.complete(approved)
    }

    fun stop() {
        currentRequestJob?.cancel()
        for (toolCallId in pendingApprovals.keys) {
            appendItemToCurrentGroup(ToolCallDeniedChatHistoryItem(toolCallId))
        }
        pendingApprovals.clear()
        approvalDecisions.clear()
    }

    private fun startNewAiInteractionGroup() {
        val currentGroups = resolvedGroupsProvider().toMutableList()
        currentGroups += ChatHistoryGroup(ChatHistoryGroupType.AI_INTERACTION, emptyList())
        resolvedUpdateGroups(currentGroups)
    }

    private fun appendItemToCurrentGroup(item: ChatHistoryItem) {
        val currentGroups = resolvedGroupsProvider().toMutableList()
        if (currentGroups.isEmpty()) {
            currentGroups += ChatHistoryGroup(ChatHistoryGroupType.AI_INTERACTION, listOf(item))
        } else {
            val lastGroup = currentGroups.last()
            currentGroups[currentGroups.lastIndex] = lastGroup.copy(items = lastGroup.items + item)
        }
        resolvedUpdateGroups(currentGroups)
        onTranscriptChanged()
    }

    private suspend fun runRequestLoop() {
        while (true) {
            appendSnapshotIfChanged()
            val requestHistory = evictHistoryIfNeeded(history)
            val response = sendChat(
                ChatRequest(
                    modelId = modelIdProvider(),
                    systemPrompt = systemPromptProvider(),
                    messages = requestHistory,
                    tools = toolCallRegistry.getDefinitions(),
                )
            )

            if (response.toolCalls.isEmpty()) {
                response.text?.let { text ->
                    appendItemToCurrentGroup(AssistantRespondedChatHistoryItem(text = text, toolCalls = emptyList()))
                }
                return
            }

            appendItemToCurrentGroup(AssistantRespondedChatHistoryItem(
                text = response.text,
                toolCalls = response.toolCalls,
            ))

            val context = toolCallExecutionContextFactory.createToolCallExecutionContext(
                onStateChanged = { stateChangedSinceLastSnapshotSent = true },
                awaitUserApproval = { toolCallId ->
                    appendItemToCurrentGroup(ToolApprovalRequestedChatHistoryItem(toolCallId))
                    val deferred = pendingApprovals.getOrPut(toolCallId) { CompletableDeferred() }
                    deferred.await()
                },
            )
            for (toolCall in response.toolCalls) {
                appendItemToCurrentGroup(ToolCallStartedChatHistoryItem(toolCall))

                val result = toolCallDispatcher.execute(
                    toolCallId = toolCall.id,
                    providerToolId = toolCall.name,
                    argumentsAsJsonString = toolCall.argumentsJson,
                    context = context,
                )
                val content = result.toProviderMessageContent()
                pendingApprovals.remove(toolCall.id)
                val approvalDecision = approvalDecisions.remove(toolCall.id)
                if (result.endsTurn) {
                    appendItemToCurrentGroup(ToolCallCompletedChatHistoryItem(
                        toolCallId = toolCall.id,
                        providerContent = content,
                        transcriptOutput = result.toTranscriptOutput(content),
                        endsTurn = true,
                    ))
                    return
                }
                appendToolResultItem(toolCall.id, result, content, approvalDecision)
            }
        }
    }

    private fun appendToolResultItem(
        toolCallId: String,
        result: ToolCallHandlerResponse,
        content: String,
        approvalDecision: Boolean?,
    ) {
        appendItemToCurrentGroup(when {
            approvalDecision == false -> ToolCallDeniedChatHistoryItem(toolCallId)
            result is ToolCallExecutionResult && !result.success -> ToolCallFailedChatHistoryItem(
                toolCallId,
                providerContent = content,
                message = result.message ?: result.toTranscriptOutput(content),
            )
            else -> ToolCallCompletedChatHistoryItem(
                toolCallId = toolCallId,
                providerContent = content,
                transcriptOutput = result.toTranscriptOutput(content),
                endsTurn = false,
            )
        })
    }

    private fun appendSnapshotIfChanged() {
        if (!stateChangedSinceLastSnapshotSent) {
            return
        }
        appendItemToCurrentGroup(StateSnapshotAddedChatHistoryItem(snapshotProvider.getSnapshotJson()))
        stateChangedSinceLastSnapshotSent = false
    }

    private fun evictHistoryIfNeeded(history: List<ChatMessage>): List<ChatMessage> {
        val requestHistory = history.toMutableList()
        while (estimateHistoryTokens(requestHistory) > maxHistoryTokens) {
            val boundaries = turnStartIndices(requestHistory)
            if (boundaries.size <= 1) return requestHistory
            requestHistory.subList(0, boundaries[1]).clear()
        }
        return requestHistory
    }

    private fun estimateHistoryTokens(history: List<ChatMessage>): Int = history.sumOf(::estimateMessageTokens)

    private fun estimateMessageTokens(message: ChatMessage): Int = when (message) {
        is ChatMessage.User -> estimateTokens(message.text)
        is ChatMessage.Assistant -> {
            estimateTokens(message.text ?: "") +
                message.toolCalls.sumOf { estimateTokens(it.name) + estimateTokens(it.argumentsJson) }
        }
        is ChatMessage.SystemState -> estimateTokens(message.snapshotJson)
        is ChatMessage.ToolResult -> estimateTokens(message.content)
    }

    private fun estimateTokens(text: String): Int = (text.length + 3) / 4

    private fun turnStartIndices(history: List<ChatMessage>): List<Int> {
        val starts = mutableListOf<Int>()
        for (i in history.indices) {
            if (history[i] is ChatMessage.User) {
                val candidate = if (i > 0 && history[i - 1] is ChatMessage.SystemState) i - 1 else i
                starts += candidate
            }
        }
        return starts
    }

    companion object {
        const val DEFAULT_MAX_HISTORY_TOKENS: Int = 75_000
    }
}
