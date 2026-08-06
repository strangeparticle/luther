package com.strangeparticle.luther.core.session

import com.strangeparticle.luther.core.client.provider.AiProvider
import com.strangeparticle.luther.core.session.event.LocalCommandResponseKind
import com.strangeparticle.luther.core.session.event.LocalCommandSource
import com.strangeparticle.luther.core.toolcall.ToolCallHandler
import com.strangeparticle.luther.core.toolcall.ToolCallHandlerResponse
import com.strangeparticle.luther.core.toolcall.ToolCallRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

fun createLutherSession(
    providers: List<AiProvider>,
    settings: LutherSettings,
    toolHandlers: List<ToolCallHandler>,
    executionContextFactory: AiSessionToolCallExecutionContextFactory,
    snapshotProvider: AiSessionSnapshotProvider,
    systemPromptProvider: () -> String,
    coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    onTurnStart: () -> Unit = {},
    onTurnEnd: () -> Unit = {},
    terseHelpText: String = "",
): LutherSession {
    val provider = providers.firstOrNull { it.id == settings.providerId }
        ?: throw IllegalArgumentException("Unknown providerId '${settings.providerId}'")
    require(settings.isComplete(provider)) { "Incomplete LutherSettings for provider '${settings.providerId}'" }
    return LutherSession(
        providers, settings, toolHandlers, executionContextFactory,
        snapshotProvider, systemPromptProvider, coroutineScope, onTurnStart, onTurnEnd, terseHelpText,
    )
}

class LutherSession internal constructor(
    private val providers: List<AiProvider>,
    initialSettings: LutherSettings,
    private val toolHandlers: List<ToolCallHandler>,
    private val executionContextFactory: AiSessionToolCallExecutionContextFactory,
    private val snapshotProvider: AiSessionSnapshotProvider,
    private val systemPromptProvider: () -> String,
    private val coroutineScope: CoroutineScope,
    private val onTurnStart: () -> Unit = {},
    private val onTurnEnd: () -> Unit = {},
    terseHelpText: String = "",
) {
    private val historyState = MutableStateFlow<List<ChatHistoryGroup>>(emptyList())
    val chatHistory: StateFlow<List<ChatHistoryGroup>> = historyState.asStateFlow()

    private var settings: LutherSettings = initialSettings
    private val statusState = MutableStateFlow(statusFor(initialSettings))
    val status: StateFlow<LutherStatus> = statusState.asStateFlow()

    private var manager: AiSessionManager = buildManager()

    // The effective provider/model is recorded in the chat history once at construction and again
    // only when it actually changes (issue #81). lastProviderModelKey tracks the value last recorded
    // so updateConfiguration appends a change entry exactly when provider or model changes.
    private var lastProviderModelKey: String = providerModelKey()

    init {
        historyState.value = initialChatHistory(providerLabel(), settings.modelId, terseHelpText)
    }

    private fun provider(): AiProvider = providers.first { it.id == settings.providerId }

    private fun providerLabel(): String =
        providers.firstOrNull { it.id == settings.providerId }?.displayName ?: settings.providerId

    private fun providerModelKey(): String = "${providerLabel()}:${settings.modelId}"

    private fun buildManager(): AiSessionManager {
        val currentProvider = provider()
        val currentConfig = settings.providerConfig
        val registry = ToolCallRegistry().apply { toolHandlers.forEach { register(it) } }
        return AiSessionManager(
            sendChat = { request -> currentProvider.respond(currentConfig, request) },
            toolCallRegistry = registry,
            snapshotProvider = snapshotProvider,
            toolCallExecutionContextFactory = executionContextFactory,
            systemPromptProvider = systemPromptProvider,
            modelIdProvider = { settings.modelId },
            coroutineScope = coroutineScope,
            groupsProvider = { historyState.value },
            updateGroups = { historyState.value = it },
            onTurnStart = onTurnStart,
            onTurnEnd = onTurnEnd,
        )
    }

    fun updateConfiguration(newSettings: LutherSettings) {
        val needsRebuild = newSettings.providerId != settings.providerId ||
            newSettings.providerConfig != settings.providerConfig
        settings = newSettings
        if (needsRebuild) manager = buildManager()
        val newKey = providerModelKey()
        if (newKey != lastProviderModelKey) {
            historyState.value = appendProviderModelState(historyState.value, providerLabel(), newSettings.modelId)
            lastProviderModelKey = newKey
        }
        statusState.value = statusFor(newSettings)
    }

    private fun statusFor(candidateSettings: LutherSettings): LutherStatus {
        val provider = providers.firstOrNull { it.id == candidateSettings.providerId }
        return LutherStatus(
            isReady = provider != null && candidateSettings.isComplete(provider),
            providerId = candidateSettings.providerId,
            modelId = candidateSettings.modelId,
            lastError = null,
        )
    }

    val transcriptParts: List<ChatMessagePart> get() = manager.transcriptParts

    fun submit(userText: String): Job = manager.submit(userText)
    fun stop() = manager.stop()
    fun respondToToolApproval(toolCallId: String, approved: Boolean) =
        manager.onApprovalDecision(toolCallId, approved)
    fun markExternalStateChange() = manager.markExternalStateChange()

    // Execute a registered tool call outside a model turn (e.g. the /undo and /redo chat commands
    // dispatch the host's undo/redo tools). Host-owned undo/redo: luther runs the host's tool and the
    // host reports the user-visible outcome back via notifyUndoPerformed / notifyRedoPerformed.
    suspend fun executeLocalToolCall(toolName: String): ToolCallHandlerResponse =
        manager.executeLocalToolCall(toolName)

    // Append a user-visible local-command entry to the chat history (help output, undo/redo
    // confirmations, unknown-command errors). The host owns execution; this only records the result
    // for display.
    fun recordLocalCommand(
        commandText: String,
        source: LocalCommandSource,
        responseText: String,
        responseKind: LocalCommandResponseKind,
    ) {
        historyState.value = historyState.value + localCommandGroup(commandText, source, responseText, responseKind)
    }

    fun notifyUndoPerformed(message: String) =
        recordLocalCommand("/undo", LocalCommandSource.User, message, LocalCommandResponseKind.Help)

    fun notifyRedoPerformed(message: String) =
        recordLocalCommand("/redo", LocalCommandSource.User, message, LocalCommandResponseKind.Help)

    fun close() {
        coroutineScope.cancel()
    }
}
