package com.strangeparticle.luther.core.client.provider.anthropic.response

import com.strangeparticle.luther.core.client.provider.ChatResponse
import com.strangeparticle.luther.core.client.provider.ChatResponseEvent
import com.strangeparticle.luther.core.client.provider.StopReason
import com.strangeparticle.luther.core.client.provider.ToolCall
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Reduces Anthropic Messages-API SSE `data:` payloads into a stream of [ChatResponseEvent].
 * Stateful; feed each payload to [onData] (returns a [ChatResponseEvent.TextDelta] to emit, or
 * null), then call [completed] once at end of stream.
 */
internal class AnthropicStreamAccumulator(private val json: Json = Json { ignoreUnknownKeys = true }) {
    private val text = StringBuilder()
    private data class ToolBlock(val id: String, val name: String, val args: StringBuilder = StringBuilder())
    private val toolBlocks = LinkedHashMap<Int, ToolBlock>()
    private var stopReason: String? = null

    fun onData(data: String): ChatResponseEvent.TextDelta? {
        val obj = json.parseToJsonElement(data).jsonObject
        when (obj["type"]?.jsonPrimitive?.contentOrNull) {
            "content_block_start" -> {
                val index = obj["index"]?.jsonPrimitive?.intOrNull ?: return null
                val block = obj["content_block"]?.jsonObject ?: return null
                if (block["type"]?.jsonPrimitive?.contentOrNull == "tool_use") {
                    toolBlocks[index] = ToolBlock(
                        id = block["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        name = block["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    )
                }
            }
            "content_block_delta" -> {
                val index = obj["index"]?.jsonPrimitive?.intOrNull ?: return null
                val delta = obj["delta"]?.jsonObject ?: return null
                when (delta["type"]?.jsonPrimitive?.contentOrNull) {
                    "text_delta" -> {
                        val t = delta["text"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        text.append(t)
                        return ChatResponseEvent.TextDelta(t)
                    }
                    "input_json_delta" ->
                        toolBlocks[index]?.args?.append(delta["partial_json"]?.jsonPrimitive?.contentOrNull.orEmpty())
                }
            }
            "message_delta" ->
                stopReason = obj["delta"]?.jsonObject?.get("stop_reason")?.jsonPrimitive?.contentOrNull ?: stopReason
        }
        return null
    }

    fun completed(): ChatResponseEvent.Completed = ChatResponseEvent.Completed(
        ChatResponse(
            text = text.toString().takeIf { it.isNotEmpty() },
            toolCalls = toolBlocks.values.map { ToolCall(it.id, it.name, it.args.toString().ifEmpty { "{}" }) },
            stopReason = mapStopReason(stopReason),
        )
    )

    private fun mapStopReason(raw: String?): StopReason = when (raw) {
        "end_turn", "stop_sequence" -> StopReason.Stop
        "tool_use" -> StopReason.ToolUse
        "max_tokens" -> StopReason.MaxTokens
        else -> StopReason.Other
    }
}
