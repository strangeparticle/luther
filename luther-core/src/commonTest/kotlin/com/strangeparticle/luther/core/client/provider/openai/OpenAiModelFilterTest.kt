package com.strangeparticle.luther.core.client.provider.openai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [com.strangeparticle.luther.core.client.provider.openai.OpenAiModelFilter] — the heuristic that picks chat-completion-capable
 * models out of OpenAI's `/v1/models` response.
 */
internal class OpenAiModelFilterTest {

    @Test
    fun `gpt-5 is chat-capable`() {
        assertTrue(com.strangeparticle.luther.core.client.provider.openai.OpenAiModelFilter.isChatCompletionCapable("gpt-5"))
    }

    @Test
    fun `gpt-5-mini is chat-capable`() {
        assertTrue(com.strangeparticle.luther.core.client.provider.openai.OpenAiModelFilter.isChatCompletionCapable("gpt-5-mini"))
    }

    @Test
    fun `gpt-4o is chat-capable`() {
        assertTrue(com.strangeparticle.luther.core.client.provider.openai.OpenAiModelFilter.isChatCompletionCapable("gpt-4o"))
    }

    @Test
    fun `text-embedding-3-large is rejected`() {
        assertFalse(com.strangeparticle.luther.core.client.provider.openai.OpenAiModelFilter.isChatCompletionCapable("text-embedding-3-large"))
    }

    @Test
    fun `omni-moderation-latest is rejected`() {
        assertFalse(com.strangeparticle.luther.core.client.provider.openai.OpenAiModelFilter.isChatCompletionCapable("omni-moderation-latest"))
    }

    @Test
    fun `whisper-1 is rejected`() {
        assertFalse(com.strangeparticle.luther.core.client.provider.openai.OpenAiModelFilter.isChatCompletionCapable("whisper-1"))
    }

    @Test
    fun `dall-e-3 is rejected`() {
        assertFalse(com.strangeparticle.luther.core.client.provider.openai.OpenAiModelFilter.isChatCompletionCapable("dall-e-3"))
    }

    @Test
    fun `babbage-002 is rejected`() {
        assertFalse(com.strangeparticle.luther.core.client.provider.openai.OpenAiModelFilter.isChatCompletionCapable("babbage-002"))
    }

    @Test
    fun `gpt-4o-audio-preview is rejected - audio variant`() {
        assertFalse(com.strangeparticle.luther.core.client.provider.openai.OpenAiModelFilter.isChatCompletionCapable("gpt-4o-audio-preview"))
    }

    @Test
    fun `filterAndMap returns sorted descending model infos`() {
        val body = Json.parseToJsonElement(
            """
            {
              "object": "list",
              "data": [
                { "id": "gpt-4o-mini", "object": "model" },
                { "id": "gpt-5", "object": "model" },
                { "id": "text-embedding-3-large", "object": "model" },
                { "id": "gpt-5-mini", "object": "model" },
                { "id": "whisper-1", "object": "model" }
              ]
            }
            """.trimIndent()
        ) as JsonObject

        val result = com.strangeparticle.luther.core.client.provider.openai.OpenAiModelFilter.filterAndMap(body)

        assertEquals(listOf("gpt-5-mini", "gpt-5", "gpt-4o-mini"), result.map { it.id })
        // All surfaced as tool-calling-capable.
        assertTrue(result.all { it.supportsToolCalling })
    }

    @Test
    fun `filterAndMap returns empty list when data field is missing`() {
        val body = Json.parseToJsonElement("""{ "object": "list" }""") as JsonObject
        assertTrue(com.strangeparticle.luther.core.client.provider.openai.OpenAiModelFilter.filterAndMap(body).isEmpty())
    }
}
