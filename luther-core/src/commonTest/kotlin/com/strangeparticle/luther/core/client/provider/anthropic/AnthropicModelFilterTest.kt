package com.strangeparticle.luther.core.client.provider.anthropic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class AnthropicModelFilterTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun modelListJson(vararg models: Pair<String, String>) = json.parseToJsonElement(
        buildString {
            append("""{"data":[""")
            models.forEachIndexed { i, (id, displayName) ->
                if (i > 0) append(",")
                append("""{"type":"model","id":"$id","display_name":"$displayName"}""")
            }
            append("]}")
        }
    ).jsonObject

    @Test
    fun `keep_claudeOpus`() {
        val result = AnthropicModelFilter.filterAndMap(modelListJson("claude-opus-4-7" to "Claude Opus 4.7"))
        assertEquals(1, result.size)
        assertEquals("claude-opus-4-7", result[0].id)
    }

    @Test
    fun `keep_claudeSonnet`() {
        val result = AnthropicModelFilter.filterAndMap(modelListJson("claude-sonnet-4-6" to "Claude Sonnet 4.6"))
        assertEquals(1, result.size)
    }

    @Test
    fun `keep_claudeHaiku`() {
        val result = AnthropicModelFilter.filterAndMap(modelListJson("claude-haiku-4-5" to "Claude Haiku 4.5"))
        assertEquals(1, result.size)
    }

    @Test
    fun `drop_nonClaudeModel`() {
        val result = AnthropicModelFilter.filterAndMap(modelListJson("gpt-5" to "GPT-5"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `drop entry where type is not model`() {
        val body = json.parseToJsonElement(
            """{"data":[{"type":"snapshot","id":"claude-opus-4-7","display_name":"Snapshot"}]}"""
        ).jsonObject
        assertTrue(AnthropicModelFilter.filterAndMap(body).isEmpty())
    }

    @Test
    fun `filterAndMap_returnsSortedDescending`() {
        val result = AnthropicModelFilter.filterAndMap(modelListJson(
            "claude-haiku-4-5" to "Haiku",
            "claude-opus-4-7" to "Opus",
            "claude-sonnet-4-6" to "Sonnet",
        ))
        assertEquals(listOf("claude-sonnet-4-6", "claude-opus-4-7", "claude-haiku-4-5"), result.map { it.id })
    }

    @Test
    fun `filterAndMap_setsSupportsToolCallingTrue`() {
        val result = AnthropicModelFilter.filterAndMap(modelListJson("claude-sonnet-4-6" to "Sonnet"))
        assertTrue(result.all { it.supportsToolCalling })
    }

    @Test
    fun `filterAndMap_usesDisplayNameFromResponse`() {
        val result = AnthropicModelFilter.filterAndMap(modelListJson("claude-sonnet-4-6" to "Claude Sonnet 4.6"))
        assertEquals("Claude Sonnet 4.6", result[0].displayName)
    }

    @Test
    fun `filterAndMap_emptyDataReturnsEmpty`() {
        val body = json.parseToJsonElement("""{"data":[]}""").jsonObject
        assertTrue(AnthropicModelFilter.filterAndMap(body).isEmpty())
    }
}
