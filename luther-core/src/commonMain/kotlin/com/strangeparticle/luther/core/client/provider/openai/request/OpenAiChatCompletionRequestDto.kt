package com.strangeparticle.luther.core.client.provider.openai.request

import com.strangeparticle.luther.core.client.provider.ChatMessage
import com.strangeparticle.luther.core.client.provider.ChatRequest
import com.strangeparticle.luther.core.client.provider.ToolCall
import com.strangeparticle.luther.core.client.provider.ToolDefinition
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

@Serializable
internal data class OpenAiChatCompletionRequestDto(
    val model: String,
    val messages: List<com.strangeparticle.luther.core.client.provider.openai.request.OpenAiMessageDto>,
    val tools: List<com.strangeparticle.luther.core.client.provider.openai.request.OpenAiToolDto>? = null,
    @SerialName("tool_choice")
    val toolChoice: String? = null,
    @SerialName("max_tokens")
    val maxTokens: Int? = null,
    val stream: Boolean = false,
) {
    companion object {
        fun from(request: ChatRequest, stream: Boolean = false): OpenAiChatCompletionRequestDto = OpenAiChatCompletionRequestDto(
            model = request.modelId,
            messages = buildMessages(request),
            tools = request.tools.takeIf { it.isNotEmpty() }?.map(::toOpenAiTool),
            toolChoice = request.tools.takeIf { it.isNotEmpty() }?.let { "auto" },
            maxTokens = request.maxTokens,
            stream = stream,
        )

        private fun buildMessages(request: ChatRequest): List<com.strangeparticle.luther.core.client.provider.openai.request.OpenAiMessageDto> = buildList {
            add(
                com.strangeparticle.luther.core.client.provider.openai.request.OpenAiMessageDto(
                    role = "system",
                    content = request.systemPrompt.toJsonElement()
                )
            )
            addAll(request.messages.map(::toOpenAiMessage))
        }

        private fun toOpenAiMessage(message: ChatMessage): com.strangeparticle.luther.core.client.provider.openai.request.OpenAiMessageDto = when (message) {
            is ChatMessage.User -> com.strangeparticle.luther.core.client.provider.openai.request.OpenAiMessageDto(
                role = "user",
                content = message.text.toJsonElement(),
            )
            is ChatMessage.Assistant -> com.strangeparticle.luther.core.client.provider.openai.request.OpenAiMessageDto(
                role = "assistant",
                content = message.text?.toJsonElement() ?: JsonNull,
                toolCalls = message.toolCalls.takeIf { it.isNotEmpty() }?.map(::toOpenAiToolCall),
            )
            is ChatMessage.ToolResult -> com.strangeparticle.luther.core.client.provider.openai.request.OpenAiMessageDto(
                role = "tool",
                toolCallId = message.toolCallId,
                content = message.content.toJsonElement(),
            )
            is ChatMessage.SystemState -> com.strangeparticle.luther.core.client.provider.openai.request.OpenAiMessageDto(
                role = "user",
                content = "<current_state>${message.snapshotJson}</current_state>".toJsonElement(),
            )
        }

        private fun toOpenAiToolCall(toolCall: ToolCall): com.strangeparticle.luther.core.client.provider.openai.request.OpenAiToolCallDto =
            com.strangeparticle.luther.core.client.provider.openai.request.OpenAiToolCallDto(
                id = toolCall.id,
                type = "function",
                function = com.strangeparticle.luther.core.client.provider.openai.request.OpenAiToolCallFunctionDto(
                    name = toolCall.name,
                    arguments = toolCall.argumentsJson,
                ),
            )

        private fun toOpenAiTool(tool: ToolDefinition): com.strangeparticle.luther.core.client.provider.openai.request.OpenAiToolDto =
            com.strangeparticle.luther.core.client.provider.openai.request.OpenAiToolDto(
                type = "function",
                function = com.strangeparticle.luther.core.client.provider.openai.request.OpenAiToolFunctionDto(
                    name = tool.name,
                    description = tool.description,
                    parameters = tool.schema,
                ),
            )

        private fun String.toJsonElement(): JsonElement = JsonPrimitive(this)
    }
}
