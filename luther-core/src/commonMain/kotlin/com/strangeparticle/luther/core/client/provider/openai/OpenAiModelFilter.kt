package com.strangeparticle.luther.core.client.provider.openai

import com.strangeparticle.luther.core.client.provider.Model
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Filters and maps OpenAI's `/v1/models` response down to the chat-completion +
 * tool-calling-capable subset we'd want to surface in a model dropdown.
 *
 * The `/v1/models` endpoint returns every model the account has access to, including
 * embeddings (`text-embedding-...`), moderation (`omni-moderation-...`), audio
 * (`whisper-...`, `tts-...`), image (`dall-e-...`), and legacy completions
 * (`davinci`, `babbage`). We keep `gpt-*` and reject anything that visibly belongs
 * to the other categories. The heuristic is intentionally simple — when a new
 * non-chat `gpt-*` model appears it'll need to be added to the deny list, but that
 * feels like the right tradeoff vs. an allow-list that misses new chat models on
 * release day.
 *
 * Per spec §6.3.
 */
internal object OpenAiModelFilter {

    private val rejectKeywords = listOf(
        "embedding", "moderation", "whisper", "tts", "dall-e", "audio",
        "babbage", "davinci", "image",
    )

    fun isChatCompletionCapable(modelId: String): Boolean {
        val lowered = modelId.lowercase()
        if (!lowered.startsWith("gpt-")) return false
        if (rejectKeywords.any { lowered.contains(it) }) return false
        return true
    }

    /**
     * Walk the parsed `/v1/models` response body, filter to chat-capable model ids,
     * and produce a sorted list of [Model]. Sort is descending by id so newer
     * models (e.g. `gpt-5`) surface above older ones (e.g. `gpt-4o`).
     */
    fun filterAndMap(responseBody: JsonObject): List<Model> {
        val data = responseBody["data"] as? JsonArray ?: return emptyList()
        return data
            .mapNotNull { entry ->
                val obj = entry as? JsonObject ?: return@mapNotNull null
                val id = (obj["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                if (!isChatCompletionCapable(id)) return@mapNotNull null
                Model(id = id, displayName = id, supportsToolCalling = true)
            }
            .sortedByDescending { it.id }
    }
}
