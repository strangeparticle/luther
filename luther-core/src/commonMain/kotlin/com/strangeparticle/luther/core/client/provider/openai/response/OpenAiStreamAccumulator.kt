package com.strangeparticle.luther.core.client.provider.openai.response

import com.strangeparticle.luther.core.client.provider.ChatResponse
import com.strangeparticle.luther.core.client.provider.ChatResponseEvent
import com.strangeparticle.luther.core.client.provider.ProviderErrorType
import com.strangeparticle.luther.core.client.provider.ProviderException
import com.strangeparticle.luther.core.client.provider.StopReason
import com.strangeparticle.luther.core.client.provider.ToolCall
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

internal class OpenAiStreamAccumulator(private val json: Json = Json { ignoreUnknownKeys = true }) {
    private val text = StringBuilder()
    private data class ToolCallBuilder(var id: String = "", var name: String = "", val args: StringBuilder = StringBuilder())
    private val tools = LinkedHashMap<Int, ToolCallBuilder>()
    private var finishReason: String? = null

    fun onData(data: String): ChatResponseEvent.TextDelta? {
        if (data == "[DONE]") return null
        val obj = json.parseToJsonElement(data).jsonObject
        // A mid-stream chunk carrying a top-level `error` object means the SSE connection stayed open
        // past a successful HTTP 200 but the provider then reported a failure inline. Without this
        // guard, the accumulator would silently ignore the error chunk and `completed()` would return
        // a truncated response as if the stream had ended normally.
        // Note: `obj["error"]` is Kotlin `null` only when the key is ABSENT — an explicit
        // `"error": null` (sent on every normal chunk by some OpenAI-compatible providers) decodes to
        // a JsonNull element, which is `!= null`. Exclude JsonNull explicitly so those chunks aren't
        // misclassified as errors.
        val errorElement = obj["error"]
        if (errorElement != null && errorElement !is JsonNull) {
            throw classifyStreamError(data)
        }
        val choice = obj["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
        choice["finish_reason"]?.jsonPrimitive?.contentOrNull?.let { finishReason = it }
        val delta = choice["delta"]?.jsonObject ?: return null

        delta["tool_calls"]?.jsonArray?.forEach { element ->
            val tc = element.jsonObject
            val index = tc["index"]?.jsonPrimitive?.intOrNull ?: 0
            val b = tools.getOrPut(index) { ToolCallBuilder() }
            tc["id"]?.jsonPrimitive?.contentOrNull?.let { b.id = it }
            tc["function"]?.jsonObject?.let { fn ->
                fn["name"]?.jsonPrimitive?.contentOrNull?.let { b.name = it }
                fn["arguments"]?.jsonPrimitive?.contentOrNull?.let { b.args.append(it) }
            }
        }

        val content = delta["content"]?.jsonPrimitive?.contentOrNull
        if (!content.isNullOrEmpty()) {
            text.append(content)
            return ChatResponseEvent.TextDelta(content)
        }
        return null
    }

    // Reuses the same body-based classifier the non-streaming error path uses (Task 1), passing the
    // synthetic httpStatus 200 since the SSE connection itself succeeded before the error arrived.
    private fun classifyStreamError(data: String): ProviderException = try {
        OpenAiResponseParser.classifyError(httpStatus = 200, body = data)
    } catch (e: Exception) {
        ProviderException(ProviderErrorType.ProviderUnavailable, "OpenAI stream error", cause = e)
    }

    fun completed(): ChatResponseEvent.Completed = ChatResponseEvent.Completed(
        ChatResponse(
            text = text.toString().takeIf { it.isNotEmpty() },
            toolCalls = tools.values.map { ToolCall(it.id, it.name, it.args.toString().ifEmpty { "{}" }) },
            stopReason = mapStopReason(finishReason),
        )
    )

    private fun mapStopReason(raw: String?): StopReason = when (raw) {
        "stop" -> StopReason.Stop
        "tool_calls" -> StopReason.ToolUse
        "length" -> StopReason.MaxTokens
        else -> StopReason.Other
    }
}
