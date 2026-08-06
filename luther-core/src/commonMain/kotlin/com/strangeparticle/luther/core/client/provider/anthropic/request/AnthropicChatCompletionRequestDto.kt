package com.strangeparticle.luther.core.client.provider.anthropic.request

import com.strangeparticle.luther.core.client.provider.ChatMessage
import com.strangeparticle.luther.core.client.provider.ChatRequest
import com.strangeparticle.luther.core.client.provider.ToolCall
import com.strangeparticle.luther.core.client.provider.ToolDefinition
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

/**
 * Top-level Anthropic Messages API request DTO.
 *
 * AnthropicChatCompletionRequestTest documents the full serialized JSON shapes produced by [from].
 */
@Serializable
internal data class AnthropicChatCompletionRequestDto(
    val model: String,
    val messages: List<AnthropicMessageDto>,
    val system: String,
    @SerialName("max_tokens") val maxTokens: Int,
    val tools: List<AnthropicToolDto>? = null,
    @SerialName("tool_choice") val toolChoice: AnthropicToolChoiceDto? = null,
    val stream: Boolean = false,
) {
    companion object {

        private const val DEFAULT_MAX_TOKENS = 8192

        fun from(request: ChatRequest, stream: Boolean = false): AnthropicChatCompletionRequestDto =
            AnthropicChatCompletionRequestDto(
                model = request.modelId,
                messages = buildMessages(request.messages),
                system = request.systemPrompt,
                maxTokens = request.maxTokens ?: DEFAULT_MAX_TOKENS,
                tools = request.tools.takeIf { it.isNotEmpty() }?.map(::toAnthropicTool),
                toolChoice = request.tools.takeIf { it.isNotEmpty() }?.let { AnthropicToolChoiceDto("auto") },
                stream = stream,
            )

        /**
         * Map history to Anthropic messages, merging consecutive user-role turns into one.
         *
         * Anthropic requires strictly alternating user/assistant roles. Two cases produce
         * back-to-back user turns that must be merged:
         *  1. ChatMessage.SystemState followed by ChatMessage.User
         *  2. Multiple consecutive ChatMessage.ToolResult instances (all → user role)
         *
         * Consecutive user-role inputs accumulate into a pending list and are flushed as
         * a single AnthropicMessageDto with a JsonArray content when an assistant turn arrives
         * or the history ends.
         */
        private fun buildMessages(history: List<ChatMessage>): List<AnthropicMessageDto> {
            val result = mutableListOf<AnthropicMessageDto>()
            val pendingUserBlocks = mutableListOf<JsonElement>()

            fun flushPendingUser() {
                if (pendingUserBlocks.isEmpty()) return
                val content: JsonElement = when {
                    pendingUserBlocks.size == 1 && (pendingUserBlocks[0] as? JsonObject)
                        ?.get("type")?.let { (it as? JsonPrimitive)?.content } == "text" -> {
                        // Single text block — Anthropic accepts a plain string, which is simpler
                        JsonPrimitive((pendingUserBlocks[0] as JsonObject)["text"]!!.let { (it as JsonPrimitive).content })
                    }
                    else -> JsonArray(pendingUserBlocks.toList())
                }
                result.add(AnthropicMessageDto(role = "user", content = content))
                pendingUserBlocks.clear()
            }

            for (message in history) {
                when (message) {
                    is ChatMessage.User -> {
                        pendingUserBlocks.add(textBlock(message.text))
                    }
                    is ChatMessage.SystemState -> {
                        pendingUserBlocks.add(textBlock("<current_state>${message.snapshotJson}</current_state>"))
                    }
                    is ChatMessage.ToolResult -> {
                        pendingUserBlocks.add(toolResultBlock(message.toolCallId, message.content))
                    }
                    is ChatMessage.Assistant -> {
                        flushPendingUser()
                        result.add(toAssistantMessage(message))
                    }
                }
            }
            flushPendingUser()
            return result
        }

        private fun toAssistantMessage(message: ChatMessage.Assistant): AnthropicMessageDto {
            val blocks = buildList {
                message.text?.let { add(textBlock(it)) }
                message.toolCalls.forEach { add(toolUseBlock(it)) }
            }
            val content: JsonElement = if (blocks.size == 1 && message.toolCalls.isEmpty()) {
                // Text-only assistant turn — plain string is accepted and preferred
                JsonPrimitive(message.text!!)
            } else {
                JsonArray(blocks)
            }
            return AnthropicMessageDto(role = "assistant", content = content)
        }

        private fun textBlock(text: String): JsonObject = buildJsonObject {
            put("type", "text")
            put("text", text)
        }

        private fun toolUseBlock(toolCall: ToolCall): JsonObject = buildJsonObject {
            put("type", "tool_use")
            put("id", toolCall.id)
            put("name", toolCall.name)
            put("input", Json.parseToJsonElement(toolCall.argumentsJson))
        }

        private fun toolResultBlock(toolCallId: String, content: String): JsonObject = buildJsonObject {
            put("type", "tool_result")
            put("tool_use_id", toolCallId)
            put("content", content)
        }

        private fun toAnthropicTool(tool: ToolDefinition): AnthropicToolDto =
            AnthropicToolDto(
                name = tool.name,
                description = tool.description,
                inputSchema = tool.schema,
            )
    }
}
