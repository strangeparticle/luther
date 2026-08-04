package com.strangeparticle.luther.core.session

import com.strangeparticle.luther.core.session.event.ProviderModelChangedChatHistoryItem
import kotlin.test.Test
import kotlin.test.assertEquals

private const val SAMPLE_HELP = "terse help text"

internal class ProviderModelHistoryTest {

    @Test
    fun `initial chat history records active provider model before terse help`() {
        val history = initialChatHistory(
            providerLabel = "OpenAI",
            modelLabel = "gpt-4o-mini",
            terseHelpText = SAMPLE_HELP,
        )

        assertEquals(
            ChatHistoryGroup(
                ChatHistoryGroupType.PROVIDER_MODEL_CHANGE,
                listOf(ProviderModelChangedChatHistoryItem("OpenAI", "gpt-4o-mini")),
            ),
            history.first(),
        )
        assertEquals(terseHelpHistory(SAMPLE_HELP), history.drop(1))
    }

    @Test
    fun `appending a provider model change adds a new provider model change entry`() {
        val initial = initialChatHistory(
            providerLabel = "OpenAI",
            modelLabel = "gpt-4o-mini",
            terseHelpText = SAMPLE_HELP,
        )
        val changed = appendProviderModelState(
            groups = initial,
            providerLabel = "Anthropic",
            modelLabel = "claude-3-5-sonnet-latest",
        )

        assertEquals(
            ChatHistoryGroup(
                ChatHistoryGroupType.PROVIDER_MODEL_CHANGE,
                listOf(ProviderModelChangedChatHistoryItem("Anthropic", "claude-3-5-sonnet-latest")),
            ),
            changed.last(),
        )
        assertEquals(2, changed.count { it.type == ChatHistoryGroupType.PROVIDER_MODEL_CHANGE })
        assertEquals(initial, changed.dropLast(1))
    }
}
