package com.strangeparticle.luther.core.client.provider.openai

import com.strangeparticle.luther.core.client.provider.ChatMessage
import com.strangeparticle.luther.core.client.provider.ChatRequest
import com.strangeparticle.luther.core.client.provider.ToolCall
import com.strangeparticle.luther.core.client.provider.ToolDefinition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [com.strangeparticle.luther.core.client.provider.openai.request.OpenAiChatCompletionRequestDto]. Covers the OpenAI chat-completions request
     * envelope: top-level shape, message role mapping for each [ChatMessage] variant,
 * and tool-definition adaptation.
 */
internal class OpenAiChatCompletionRequestTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun emptyRequest(
        history: List<ChatMessage> = emptyList(),
        tools: List<ToolDefinition> = emptyList(),
    ) = ChatRequest(
        modelId = "gpt-5",
        systemPrompt = "you are an assistant",
        messages = history,
        tools = tools,
    )

    private fun buildBody(request: ChatRequest): JsonObject {
        val rawJson = json.encodeToString(
            com.strangeparticle.luther.core.client.provider.openai.request.OpenAiChatCompletionRequestDto.serializer(),
            com.strangeparticle.luther.core.client.provider.openai.request.OpenAiChatCompletionRequestDto.from(request),
        )
        return json.parseToJsonElement(rawJson).jsonObject
    }

    @Test
    fun `body has model, messages, and no tools when none provided`() {
        val body = buildBody(emptyRequest())

        assertEquals("gpt-5", (body["model"] as JsonPrimitive).content)
        assertTrue(body["messages"] is JsonArray)
        assertNull(body["tools"], "tools should be omitted when no tool defs are passed")
        assertNull(body["tool_choice"])
    }

    @Test
    fun `body matches rendered request example without tools`() {
        val body = buildBody(emptyRequest(
            history = listOf(ChatMessage.User("hello")),
        ))

        val expected = json.parseToJsonElement(
            """
            {
              "model": "gpt-5",
              "messages": [
                {
                  "role": "system",
                  "content": "you are an assistant"
                },
                {
                  "role": "user",
                  "content": "hello"
                }
              ]
            }
            """.trimIndent(),
        ) as JsonObject

        assertEquals(expected, body)
    }

    @Test
    fun `tools and tool_choice are included when at least one tool is passed`() {
        val tool = ToolDefinition(
            name = "add_app",
            description = "Add an app to a tab.",
            schema = buildJsonObject {
                put("type", "object")
                put("properties", buildJsonObject {})
            },
        )
        val body = buildBody(emptyRequest(tools = listOf(tool)))

        val tools = body["tools"] as JsonArray
        assertEquals(1, tools.size)

        val function = (tools[0] as JsonObject)["function"] as JsonObject
        assertEquals("add_app", (function["name"] as JsonPrimitive).content)
        assertEquals("Add an app to a tab.", (function["description"] as JsonPrimitive).content)
        assertEquals("auto", (body["tool_choice"] as JsonPrimitive).content)
    }

    @Test
    fun `body matches rendered request example with assistant tool call and tool definitions`() {
        val schema = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("tab_id", buildJsonObject { put("type", "string") })
            })
            put("required", kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive("tab_id"))))
        }
        val body = buildBody(
            emptyRequest(
                history = listOf(
                    ChatMessage.User("Add an app."),
                    ChatMessage.Assistant(
                        text = null,
                        toolCalls = listOf(ToolCall("call-1", "add_app", """{"tab_id":"tab-1"}""")),
                    ),
                    ChatMessage.ToolResult("call-1", """{"ok":true}"""),
                ),
                tools = listOf(ToolDefinition("add_app", "Add a new app to the springboard.", schema)),
            ),
        )

        val expected = json.parseToJsonElement(
            """
            {
              "model": "gpt-5",
              "messages": [
                {
                  "role": "system",
                  "content": "you are an assistant"
                },
                {
                  "role": "user",
                  "content": "Add an app."
                },
                {
                  "role": "assistant",
                  "content": null,
                  "tool_calls": [
                    {
                      "id": "call-1",
                      "type": "function",
                      "function": {
                        "name": "add_app",
                        "arguments": "{\"tab_id\":\"tab-1\"}"
                      }
                    }
                  ]
                },
                {
                  "role": "tool",
                  "content": "{\"ok\":true}",
                  "tool_call_id": "call-1"
                }
              ],
              "tools": [
                {
                  "type": "function",
                  "function": {
                    "name": "add_app",
                    "description": "Add a new app to the springboard.",
                    "parameters": {
                      "type": "object",
                      "properties": {
                        "tab_id": {
                          "type": "string"
                        }
                      },
                      "required": [
                        "tab_id"
                      ]
                    }
                  }
                }
              ],
              "tool_choice": "auto"
            }
            """.trimIndent(),
        ) as JsonObject

        assertEquals(expected, body)
    }

    @Test
    fun `system prompt is the first message with role=system`() {
        val body = buildBody(emptyRequest())
        val messages = body["messages"] as JsonArray

        val first = messages[0] as JsonObject
        assertEquals("system", (first["role"] as JsonPrimitive).content)
        assertEquals("you are an assistant", (first["content"] as JsonPrimitive).content)
    }

    @Test
    fun `UserMessage maps to role=user with content`() {
        val body = buildBody(emptyRequest(
            history = listOf(ChatMessage.User("hello")),
        ))
        val messages = body["messages"] as JsonArray
        val userMsg = messages[1] as JsonObject

        assertEquals("user", (userMsg["role"] as JsonPrimitive).content)
        assertEquals("hello", (userMsg["content"] as JsonPrimitive).content)
    }

    @Test
    fun `AssistantMessage with text only maps to role=assistant with content`() {
        val body = buildBody(emptyRequest(
            history = listOf(ChatMessage.Assistant(text = "ok", toolCalls = emptyList())),
        ))
        val assistantMsg = (body["messages"] as JsonArray)[1] as JsonObject

        assertEquals("assistant", (assistantMsg["role"] as JsonPrimitive).content)
        assertEquals("ok", (assistantMsg["content"] as JsonPrimitive).content)
        assertNull(assistantMsg["tool_calls"])
    }

    @Test
    fun `AssistantMessage with tool_calls maps to assistant with tool_calls array`() {
        val toolCall = ToolCall("call-1", "do_thing", """{"foo":"bar"}""")
        val body = buildBody(emptyRequest(
            history = listOf(ChatMessage.Assistant(text = null, toolCalls = listOf(toolCall))),
        ))
        val assistantMsg = (body["messages"] as JsonArray)[1] as JsonObject

        assertEquals("assistant", (assistantMsg["role"] as JsonPrimitive).content)
        val toolCalls = assistantMsg["tool_calls"] as JsonArray
        assertEquals(1, toolCalls.size)

        val first = toolCalls[0] as JsonObject
        assertEquals("call-1", (first["id"] as JsonPrimitive).content)
        assertEquals("function", (first["type"] as JsonPrimitive).content)
        val function = first["function"] as JsonObject
        assertEquals("do_thing", (function["name"] as JsonPrimitive).content)
        // arguments are serialized as a JSON string per OpenAI's spec.
        val argsString = (function["arguments"] as JsonPrimitive).content
        assertTrue(argsString.contains("\"foo\""))
        assertTrue(argsString.contains("\"bar\""))
    }

    @Test
    fun `ToolCallMessage maps to role=tool with tool_call_id and content`() {
        val body = buildBody(emptyRequest(
            history = listOf(ChatMessage.ToolResult("call-1", """{"ok":true}""")),
        ))
        val toolMsg = (body["messages"] as JsonArray)[1] as JsonObject

        assertEquals("tool", (toolMsg["role"] as JsonPrimitive).content)
        assertEquals("call-1", (toolMsg["tool_call_id"] as JsonPrimitive).content)
        assertEquals("""{"ok":true}""", (toolMsg["content"] as JsonPrimitive).content)
    }

    @Test
    fun `SystemStateMessage maps to a user-role message wrapped in current_state tags`() {
        val snapshot = """{"tabs":[],"activeTabId":null}"""
        val body = buildBody(emptyRequest(
            history = listOf(ChatMessage.SystemState(snapshot)),
        ))
        val stateMsg = (body["messages"] as JsonArray)[1] as JsonObject

        assertEquals("user", (stateMsg["role"] as JsonPrimitive).content)
        val content = (stateMsg["content"] as JsonPrimitive).content
        assertTrue(content.startsWith("<current_state>"))
        assertTrue(content.endsWith("</current_state>"))
        assertTrue(content.contains(snapshot))
    }

    @Test
    fun `tool definitions wrap their JSON schema unchanged as the function parameters`() {
        val schema = buildJsonObject {
            put("type", "object")
            put("required", kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive("tab_id"))))
        }
        val tool = ToolDefinition(name = "save_springboard", description = "Save.", schema = schema)
        val body = buildBody(emptyRequest(tools = listOf(tool)))
        val function = ((body["tools"] as JsonArray)[0] as JsonObject)["function"] as JsonObject

        // The schema passes through verbatim.
        assertEquals(schema, function["parameters"])
    }

    @Test
    fun `interpolated string values are escaped in the request JSON`() {
        val body = buildBody(
            ChatRequest(
                modelId = "gpt-5\"quoted",
                systemPrompt = "system prompt with \"quotes\" and newline\nnext line",
                messages = listOf(ChatMessage.User("user text with \"quotes\" and newline\nnext line")),
                tools = listOf(
                    ToolDefinition(
                        name = "tool_\"quoted",
                        description = "description with \"quotes\"",
                        schema = buildJsonObject { put("type", "object") },
                    ),
                ),
            ),
        )

        assertEquals("gpt-5\"quoted", (body["model"] as JsonPrimitive).content)
        val messages = body["messages"] as JsonArray
        assertEquals("system prompt with \"quotes\" and newline\nnext line", ((messages[0] as JsonObject)["content"] as JsonPrimitive).content)
        assertEquals("user text with \"quotes\" and newline\nnext line", ((messages[1] as JsonObject)["content"] as JsonPrimitive).content)
        val function = ((body["tools"] as JsonArray)[0] as JsonObject)["function"] as JsonObject
        assertEquals("tool_\"quoted", (function["name"] as JsonPrimitive).content)
        assertEquals("description with \"quotes\"", (function["description"] as JsonPrimitive).content)
    }
}
