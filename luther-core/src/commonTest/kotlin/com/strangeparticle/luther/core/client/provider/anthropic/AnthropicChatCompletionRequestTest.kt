package com.strangeparticle.luther.core.client.provider.anthropic

import com.strangeparticle.luther.core.client.provider.anthropic.request.AnthropicChatCompletionRequestDto
import com.strangeparticle.luther.core.client.provider.ChatMessage
import com.strangeparticle.luther.core.client.provider.ChatRequest
import com.strangeparticle.luther.core.client.provider.ToolCall
import com.strangeparticle.luther.core.client.provider.ToolDefinition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [AnthropicChatCompletionRequestDto]. Covers the Anthropic Messages API request
 * envelope: top-level shape, message role mapping for each message variant (including
 * consecutive user-turn merging), and tool-definition adaptation.
 */
internal class AnthropicChatCompletionRequestTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun emptyRequest(
        history: List<ChatMessage> = emptyList(),
        tools: List<ToolDefinition> = emptyList(),
        maxTokens: Int? = null,
    ) = ChatRequest(
        modelId = "claude-sonnet-4-6",
        systemPrompt = "you are an assistant",
        messages = history,
        tools = tools,
        maxTokens = maxTokens,
    )

    private fun buildBody(request: ChatRequest): JsonObject {
        val rawJson = json.encodeToString(
            AnthropicChatCompletionRequestDto.serializer(),
            AnthropicChatCompletionRequestDto.from(request),
        )
        return json.parseToJsonElement(rawJson).jsonObject
    }

    @Test
    fun `body includes model, messages, system, and max_tokens`() {
        val body = buildBody(emptyRequest())

        assertEquals("claude-sonnet-4-6", body["model"]!!.jsonPrimitive.content)
        assertTrue(body["messages"] is JsonArray)
        assertEquals("you are an assistant", body["system"]!!.jsonPrimitive.content)
        assertTrue(body["max_tokens"] is JsonPrimitive)
    }

    @Test
    fun `system prompt goes in top-level field, not in messages`() {
        val body = buildBody(emptyRequest())

        val messages = body["messages"]!!.jsonArray
        val roles = messages.map { it.jsonObject["role"]!!.jsonPrimitive.content }
        assertTrue(roles.none { it == "system" }, "system role must not appear in messages array")
        assertEquals("you are an assistant", body["system"]!!.jsonPrimitive.content)
    }

    @Test
    fun `uses default max_tokens when request maxTokens is null`() {
        val body = buildBody(emptyRequest(maxTokens = null))
        val maxTokens = body["max_tokens"]!!.jsonPrimitive.content.toInt()
        assertTrue(maxTokens > 0, "default max_tokens must be positive")
    }

    @Test
    fun `uses request maxTokens when provided`() {
        val body = buildBody(emptyRequest(maxTokens = 1024))
        assertEquals(1024, body["max_tokens"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `user message maps to user role with plain string content`() {
        val body = buildBody(emptyRequest(history = listOf(ChatMessage.User("hello"))))

        val messages = body["messages"]!!.jsonArray
        assertEquals(1, messages.size)
        val msg = messages[0].jsonObject
        assertEquals("user", msg["role"]!!.jsonPrimitive.content)
        assertEquals("hello", msg["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun `assistant text message maps to assistant role with plain string content`() {
        val body = buildBody(emptyRequest(history = listOf(
            ChatMessage.Assistant(text = "I can help with that.", toolCalls = emptyList()),
        )))

        val messages = body["messages"]!!.jsonArray
        assertEquals(1, messages.size)
        val msg = messages[0].jsonObject
        assertEquals("assistant", msg["role"]!!.jsonPrimitive.content)
        assertEquals("I can help with that.", msg["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun `assistant message with tool calls maps to content block array`() {
        val body = buildBody(emptyRequest(history = listOf(
            ChatMessage.Assistant(
                text = "I'll add that.",
                toolCalls = listOf(ToolCall("toolu_01", "add_app", """{"tab_id":"t1","id":"a1","name":"App","display_message":"x"}""")),
            ),
        )))

        val messages = body["messages"]!!.jsonArray
        assertEquals(1, messages.size)
        val msg = messages[0].jsonObject
        assertEquals("assistant", msg["role"]!!.jsonPrimitive.content)

        val content = msg["content"]!!.jsonArray
        assertEquals(2, content.size)
        assertEquals("text", content[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("I'll add that.", content[0].jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals("tool_use", content[1].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("toolu_01", content[1].jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("add_app", content[1].jsonObject["name"]!!.jsonPrimitive.content)
        assertTrue(content[1].jsonObject["input"] is JsonObject)
    }

    @Test
    fun `tool result maps to user role with tool_result content block`() {
        val body = buildBody(emptyRequest(history = listOf(
            ChatMessage.Assistant(text = null, toolCalls = listOf(ToolCall("toolu_01", "add_app", "{}"))),
            ChatMessage.ToolResult("toolu_01", "done"),
        )))

        val messages = body["messages"]!!.jsonArray
        assertEquals(2, messages.size)
        val resultMsg = messages[1].jsonObject
        assertEquals("user", resultMsg["role"]!!.jsonPrimitive.content)

        val content = resultMsg["content"]!!.jsonArray
        assertEquals(1, content.size)
        val block = content[0].jsonObject
        assertEquals("tool_result", block["type"]!!.jsonPrimitive.content)
        assertEquals("toolu_01", block["tool_use_id"]!!.jsonPrimitive.content)
        assertEquals("done", block["content"]!!.jsonPrimitive.content)
    }

    @Test
    fun `multiple consecutive tool results are merged into one user message`() {
        val body = buildBody(emptyRequest(history = listOf(
            ChatMessage.Assistant(text = null, toolCalls = listOf(
                ToolCall("id1", "add_app", "{}"),
                ToolCall("id2", "add_resource", "{}"),
            )),
            ChatMessage.ToolResult("id1", "result1"),
            ChatMessage.ToolResult("id2", "result2"),
        )))

        val messages = body["messages"]!!.jsonArray
        assertEquals(2, messages.size, "tool results must be merged into a single user message")

        val resultMsg = messages[1].jsonObject
        assertEquals("user", resultMsg["role"]!!.jsonPrimitive.content)
        val content = resultMsg["content"]!!.jsonArray
        assertEquals(2, content.size)
        assertEquals("id1", content[0].jsonObject["tool_use_id"]!!.jsonPrimitive.content)
        assertEquals("id2", content[1].jsonObject["tool_use_id"]!!.jsonPrimitive.content)
    }

    @Test
    fun `state injection and user message are merged into one user message`() {
        val body = buildBody(emptyRequest(history = listOf(
            ChatMessage.SystemState("""{"tabs":[]}"""),
            ChatMessage.User("add an app"),
        )))

        val messages = body["messages"]!!.jsonArray
        assertEquals(1, messages.size, "state injection + user message must merge into one")

        val msg = messages[0].jsonObject
        assertEquals("user", msg["role"]!!.jsonPrimitive.content)
        val content = msg["content"]!!.jsonArray
        assertEquals(2, content.size)
        assertEquals("text", content[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertTrue(content[0].jsonObject["text"]!!.jsonPrimitive.content.contains("<current_state>"))
        assertEquals("add an app", content[1].jsonObject["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun `tool definition uses input_schema not parameters`() {
        val schema = buildJsonObject { put("type", "object") }
        val body = buildBody(emptyRequest(tools = listOf(
            ToolDefinition(name = "add_app", description = "Add app", schema = schema),
        )))

        val tools = body["tools"]!!.jsonArray
        assertEquals(1, tools.size)
        val tool = tools[0].jsonObject
        assertEquals("add_app", tool["name"]!!.jsonPrimitive.content)
        assertEquals("Add app", tool["description"]!!.jsonPrimitive.content)
        assertTrue(tool.containsKey("input_schema"), "Anthropic uses input_schema, not parameters")
        assertTrue(!tool.containsKey("parameters"))
        assertTrue(!tool.containsKey("type"), "Anthropic tool defs are flat, not wrapped in {type:function}")
    }

    @Test
    fun `tool choice is an object not a string`() {
        val schema = buildJsonObject { put("type", "object") }
        val body = buildBody(emptyRequest(tools = listOf(
            ToolDefinition(name = "add_app", description = "Add app", schema = schema),
        )))

        val toolChoice = body["tool_choice"]!!.jsonObject
        assertEquals("auto", toolChoice["type"]!!.jsonPrimitive.content)
    }

    @Test
    fun `tools omitted when tool list is empty`() {
        val body = buildBody(emptyRequest(tools = emptyList()))

        assertNull(body["tools"], "tools must be omitted when none provided")
        assertNull(body["tool_choice"])
    }
}
