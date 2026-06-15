package com.strangeparticle.luther.cmp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import com.strangeparticle.luther.core.client.provider.AiProvider
import com.strangeparticle.luther.core.client.provider.Choice
import com.strangeparticle.luther.core.client.provider.LutherProviderCatalog
import com.strangeparticle.luther.core.session.AiSessionSnapshotProvider
import com.strangeparticle.luther.core.session.AiSessionToolCallExecutionContextFactory
import com.strangeparticle.luther.core.session.LutherSession
import com.strangeparticle.luther.core.session.LutherSettings
import com.strangeparticle.luther.core.session.buildChatHistoryDebugDumpJson
import com.strangeparticle.luther.core.session.createLutherSession
import com.strangeparticle.luther.core.session.event.LocalCommandResponseKind
import com.strangeparticle.luther.core.session.event.LocalCommandSource
import com.strangeparticle.luther.core.toolcall.ToolCallHandler
import com.strangeparticle.luther.core.toolcall.ToolCallHandlerResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Handle for the drop-in [aiAssistant] component. The component owns its own state — the
 * [LutherSession], chat history, model-option loading, input text and focus. The host holds this
 * handle to issue boundary commands (take focus, trigger undo/redo for an Edit menu) and to read
 * status (isRunning, canUndo/canRedo gating). The host does not build or own the chat state.
 */
class AiAssistantState internal constructor(
    val paneState: AiChatPaneState,
    val isRunning: Boolean,
    private val performUndoAction: () -> Unit,
    private val performRedoAction: () -> Unit,
) {
    /** Host → component: run an undo and record it in the chat history (e.g. from an Edit menu). */
    fun performUndo() = performUndoAction()

    fun performRedo() = performRedoAction()
}

/**
 * Builds and remembers a self-contained AI assistant. The component owns the [LutherSession] (built
 * from host-injected collaborators) and all derived state; the host resolves settings and injects
 * collaborators, then reads [AiAssistantState] and renders [aiAssistant].
 *
 * Settings flow one direction in via [settings] (null until the host has a complete configuration);
 * the component pushes them to the session and absorbs provider/model/credential changes live. The
 * in-pane model picker reports a selection out via [onModelSelected] so the host can persist it.
 */
@Composable
fun rememberAiAssistant(
    providers: List<AiProvider>,
    settings: LutherSettings?,
    catalog: LutherProviderCatalog,
    toolHandlers: List<ToolCallHandler>,
    snapshotProvider: AiSessionSnapshotProvider,
    executionContextFactory: AiSessionToolCallExecutionContextFactory,
    systemPromptProvider: () -> String,
    terseHelpText: String,
    fullHelpText: String,
    showFullTranscript: Boolean,
    onTurnStart: () -> Unit,
    onTurnEnd: () -> Unit,
    onModelSelected: (String) -> Unit,
    onProcessingFocusFallback: () -> Unit,
    toolMessageExtractor: (ToolCallHandlerResponse) -> String?,
): AiAssistantState {
    val coroutineScope = rememberCoroutineScope()

    if (settings == null) {
        return AiAssistantState(
            paneState = AiChatPaneState.notConfigured(),
            isRunning = false,
            performUndoAction = {},
            performRedoAction = {},
        )
    }

    val providerLabel = providers.firstOrNull { it.id == settings.providerId }?.displayName ?: settings.providerId
    val modelId = settings.modelId

    // The component owns the session. It is built once when the configuration first becomes complete
    // and kept across provider/model/credential changes via updateConfiguration (which preserves
    // chat history and records the change), so switching models never tears down the conversation.
    val session = remember {
        createLutherSession(
            providers = providers,
            settings = settings,
            toolHandlers = toolHandlers,
            executionContextFactory = executionContextFactory,
            snapshotProvider = snapshotProvider,
            systemPromptProvider = systemPromptProvider,
            coroutineScope = coroutineScope,
            onTurnStart = onTurnStart,
            onTurnEnd = onTurnEnd,
            terseHelpText = terseHelpText,
        )
    }
    val settingsKey = "${settings.providerId}:${settings.modelId}:${settings.providerConfig.hashCode()}"
    LaunchedEffect(settingsKey) {
        session.updateConfiguration(settings)
    }

    val chatHistory by session.chatHistory.collectAsState()

    var modelOptionsResult by remember(settings.providerId) { mutableStateOf<Result<List<Choice>>?>(null) }
    var isModelOptionsLoading by remember(settings.providerId) { mutableStateOf(false) }

    fun loadModelOptions() {
        coroutineScope.launch {
            isModelOptionsLoading = true
            modelOptionsResult = runCatching { catalog.availableModels(settings.providerId, settings.providerConfig) }
            isModelOptionsLoading = false
        }
    }
    LaunchedEffect(settingsKey) {
        loadModelOptions()
    }

    var runningJob by remember { mutableStateOf<Job?>(null) }
    val isRunning = runningJob?.isActive == true

    val performUndo: () -> Unit = {
        if (runningJob?.isActive == true) {
            session.recordLocalCommand("/undo", LocalCommandSource.User, "Cannot undo while the assistant is processing.", LocalCommandResponseKind.Error)
        } else {
            coroutineScope.launch {
                val response = session.executeLocalToolCall("undo")
                session.notifyUndoPerformed(toolMessageExtractor(response) ?: "Undid last change.")
            }
        }
    }
    val performRedo: () -> Unit = {
        if (runningJob?.isActive == true) {
            session.recordLocalCommand("/redo", LocalCommandSource.User, "Cannot redo while the assistant is processing.", LocalCommandResponseKind.Error)
        } else {
            coroutineScope.launch {
                val response = session.executeLocalToolCall("redo")
                session.notifyRedoPerformed(toolMessageExtractor(response) ?: "Redid last change.")
            }
        }
    }

    val effectiveScrollbackPanes = if (showFullTranscript) {
        buildDebugScrollbackPanes(chatHistory)
    } else {
        buildSlimScrollbackPanes(chatHistory, terseHelpText)
    }
    val debugChatHistoryText = buildChatHistoryDebugDumpJson(
        groups = chatHistory,
        providerLabel = providerLabel,
        modelLabel = modelId,
        systemPrompt = systemPromptProvider(),
    )

    val paneState = AiChatPaneState.configured(
        providerLabel = providerLabel,
        modelLabel = modelId,
        modelPicker = AiChatPaneModelPickerState(
            selectedModelId = modelId,
            selectedModelLabel = modelOptionsResult?.getOrNull().orEmpty()
                .firstOrNull { it.valueId == modelId }?.displayLabel ?: modelId,
            options = modelOptionsResult?.getOrNull().orEmpty(),
            isLoading = isModelOptionsLoading,
            errorMessage = modelOptionsResult?.exceptionOrNull()?.message,
            onRefresh = ::loadModelOptions,
            onSelectModel = onModelSelected,
        ),
        transcriptParts = session.transcriptParts,
        terseHelpText = terseHelpText,
        scrollbackPanes = effectiveScrollbackPanes,
        debugChatHistoryText = debugChatHistoryText,
        isRunning = isRunning,
        onSubmit = onSubmit@{ text ->
            when (val command = parseAiChatLocalCommand(text)) {
                is AiChatLocalCommand.HelpTerse -> {
                    session.recordLocalCommand(command.originalText, LocalCommandSource.User, terseHelpText, LocalCommandResponseKind.Help)
                    return@onSubmit
                }
                is AiChatLocalCommand.HelpFull -> {
                    session.recordLocalCommand(command.originalText, LocalCommandSource.User, fullHelpText, LocalCommandResponseKind.Help)
                    return@onSubmit
                }
                is AiChatLocalCommand.Undo -> {
                    performUndo()
                    return@onSubmit
                }
                is AiChatLocalCommand.Redo -> {
                    performRedo()
                    return@onSubmit
                }
                is AiChatLocalCommand.Unknown -> {
                    session.recordLocalCommand(command.originalText, LocalCommandSource.User, "Unknown command: ${command.originalText}. Try /help.", LocalCommandResponseKind.Error)
                    return@onSubmit
                }
                null -> Unit
            }
            val job = session.submit(text)
            runningJob = job
            coroutineScope.launch {
                job.join()
                runningJob = null
            }
        },
        onStop = {
            session.stop()
            runningJob = null
        },
        onApprovalDecision = { toolCallId, approved ->
            session.respondToToolApproval(toolCallId, approved)
        },
        onProcessingFocusFallback = onProcessingFocusFallback,
    )

    return AiAssistantState(
        paneState = paneState,
        isRunning = isRunning,
        performUndoAction = performUndo,
        performRedoAction = performRedo,
    )
}
