package com.strangeparticle.luther.core.session

import com.strangeparticle.luther.core.session.event.LocalCommandRespondedChatHistoryItem
import com.strangeparticle.luther.core.session.event.LocalCommandResponseKind
import com.strangeparticle.luther.core.session.event.LocalCommandSource
import com.strangeparticle.luther.core.session.event.LocalCommandSubmittedChatHistoryItem
import com.strangeparticle.luther.core.session.event.ProviderModelChangedChatHistoryItem

// Chat-history construction lives in luther-core because the LutherSession facade owns the chat
// history and is responsible for seeding it and appending provider/model and local-command entries.
// These build plain ChatHistoryGroup values from core history-item types; the terse help text is a
// host-supplied string (luther-core carries no domain copy of its own).

internal fun providerModelStateGroup(
    providerLabel: String,
    modelLabel: String,
): ChatHistoryGroup = ChatHistoryGroup(
    type = ChatHistoryGroupType.PROVIDER_MODEL_CHANGE,
    items = listOf(ProviderModelChangedChatHistoryItem(providerLabel, modelLabel)),
)

internal fun terseHelpHistory(terseHelpText: String): List<ChatHistoryGroup> = listOf(
    ChatHistoryGroup(
        type = ChatHistoryGroupType.LOCAL_COMMAND,
        items = listOf(
            LocalCommandSubmittedChatHistoryItem("/help_terse", LocalCommandSource.System),
            LocalCommandRespondedChatHistoryItem("/help_terse", terseHelpText, LocalCommandResponseKind.Help),
        ),
    ),
)

// The starting chat history for a session records the active provider/model first, then the terse
// help. Callers record the active provider/model exactly once when the session starts and append a
// change entry only when the effective provider/model actually changes.
internal fun initialChatHistory(
    providerLabel: String,
    modelLabel: String,
    terseHelpText: String,
): List<ChatHistoryGroup> = listOf(providerModelStateGroup(providerLabel, modelLabel)) + terseHelpHistory(terseHelpText)

internal fun appendProviderModelState(
    groups: List<ChatHistoryGroup>,
    providerLabel: String,
    modelLabel: String,
): List<ChatHistoryGroup> = groups + providerModelStateGroup(providerLabel, modelLabel)

internal fun localCommandGroup(
    commandText: String,
    source: LocalCommandSource,
    responseText: String,
    responseKind: LocalCommandResponseKind,
): ChatHistoryGroup = ChatHistoryGroup(
    type = ChatHistoryGroupType.LOCAL_COMMAND,
    items = listOf(
        LocalCommandSubmittedChatHistoryItem(commandText, source),
        LocalCommandRespondedChatHistoryItem(commandText, responseText, responseKind),
    ),
)
