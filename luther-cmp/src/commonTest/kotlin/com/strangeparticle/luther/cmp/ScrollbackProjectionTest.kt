// KNOWN TEST GAP: three cases from springboard's original ChatHistoryProjectionTest were
// intentionally NOT relocated here. They assert on cmp-internal scrollback projections that are
// built from core-internal `*ChatHistoryItem` event classes — no single module can see both the
// cmp-internal projections and the core-internal event classes at once, and we chose not to grow
// luther's public API just to make these tests compile. The un-relocated cases are:
//   - "ai interaction group projects to one interaction pane and provider history"
//   - "snapshot items project to provider history and debug panes but not slim panes"
//   - "assistant error projects to chat error without provider history entry"
// This is a documented gap in the cutover plan.
package com.strangeparticle.luther.cmp

import com.strangeparticle.luther.core.session.ChatHistoryGroup
import com.strangeparticle.luther.core.session.ChatHistoryGroupType
import com.strangeparticle.luther.core.session.buildChatHistoryDebugDumpJson
import com.strangeparticle.luther.core.session.event.LocalCommandRespondedChatHistoryItem
import com.strangeparticle.luther.core.session.event.LocalCommandResponseKind
import com.strangeparticle.luther.core.session.event.LocalCommandSource
import com.strangeparticle.luther.core.session.event.LocalCommandSubmittedChatHistoryItem
import com.strangeparticle.luther.core.session.event.ProviderModelChangedChatHistoryItem
import com.strangeparticle.luther.core.session.projection.buildProviderHistory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Scrollback-projection cases split out of springboard's former ChatHistoryProjectionTest. These
 * assert on luther-cmp's scrollback projections (buildSlimScrollbackPanes,
 * getAllScrollbackTextForCopyToClipboard, AiChatScrollbackPane), so they live where cmp internals
 * are visible. They only feed PUBLIC luther-core event classes into those projections; the cases
 * that required core-internal event classes stayed on the springboard/core side (see report).
 */
internal class ScrollbackProjectionTest {

    @Test
    fun `local command group projects to local command scrollback pane only`() {
        val groups = listOf(
            ChatHistoryGroup(ChatHistoryGroupType.LOCAL_COMMAND, listOf(
                LocalCommandSubmittedChatHistoryItem("/help", LocalCommandSource.User),
                LocalCommandRespondedChatHistoryItem("/help", "Help text", LocalCommandResponseKind.Help),
            )),
        )

        assertEquals(
            listOf(AiChatScrollbackPane.LocalCommand(
                commandText = "/help",
                commandAttribution = CommandAttribution.User,
                responseText = "Help text",
                style = LocalCommandResponseStyle.Help,
            )),
            buildSlimScrollbackPanes(groups),
        )
        assertEquals(emptyList(), buildProviderHistory(listOf(
            LocalCommandSubmittedChatHistoryItem("/help", LocalCommandSource.User),
            LocalCommandRespondedChatHistoryItem("/help", "Help text", LocalCommandResponseKind.Help),
        )))
    }

    @Test
    fun `provider model change group projects to provider model scrollback and debug dump only`() {
        val groups = listOf(
            ChatHistoryGroup(ChatHistoryGroupType.PROVIDER_MODEL_CHANGE, listOf(
                ProviderModelChangedChatHistoryItem("OpenAI", "gpt-4o-mini"),
            )),
        )

        assertEquals(
            listOf(AiChatScrollbackPane.ProviderModelChange("Active AI provider/model: OpenAI:gpt-4o-mini")),
            buildSlimScrollbackPanes(groups),
        )
        assertEquals(emptyList(), buildProviderHistory(groups.flatMap { it.items }))
        assertEquals(
            "Active AI provider/model: OpenAI:gpt-4o-mini",
            getAllScrollbackTextForCopyToClipboard(buildSlimScrollbackPanes(groups)),
        )

        val dumpJson = buildChatHistoryDebugDumpJson(
            groups = groups,
            providerLabel = "OpenAI",
            modelLabel = "gpt-4o-mini",
            systemPrompt = "system prompt",
        )
        val item = (((Json.parseToJsonElement(dumpJson).jsonObject["groups"] as JsonArray)[0] as JsonObject)["items"] as JsonArray)[0] as JsonObject
        assertEquals(JsonPrimitive("ProviderModelChangedChatHistoryItem"), item["kind"])
        assertEquals(JsonPrimitive("OpenAI"), item["providerLabel"])
        assertEquals(JsonPrimitive("gpt-4o-mini"), item["modelLabel"])
    }
}
