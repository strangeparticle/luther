package com.strangeparticle.luther.core.client.provider.anthropic.response

import com.strangeparticle.luther.core.client.provider.ChatResponseEvent
import com.strangeparticle.luther.core.client.provider.StopReason
import kotlin.test.Test
import kotlin.test.assertEquals

class AnthropicStreamAccumulatorTest {
    private fun feed(acc: AnthropicStreamAccumulator, vararg data: String): List<ChatResponseEvent> {
        val out = mutableListOf<ChatResponseEvent>()
        for (d in data) acc.onData(d)?.let { out += it }
        out += acc.completed()
        return out
    }

    @Test
    fun `text deltas concatenate into a Completed response`() {
        val events = feed(
            AnthropicStreamAccumulator(),
            """{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}""",
            """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hel"}}""",
            """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"lo"}}""",
            """{"type":"content_block_stop","index":0}""",
            """{"type":"message_delta","delta":{"stop_reason":"end_turn"}}""",
            """{"type":"message_stop"}""",
        )
        assertEquals(ChatResponseEvent.TextDelta("Hel"), events[0])
        assertEquals(ChatResponseEvent.TextDelta("lo"), events[1])
        val completed = events.last() as ChatResponseEvent.Completed
        assertEquals("Hello", completed.response.text)
        assertEquals(StopReason.Stop, completed.response.stopReason)
        assertEquals(emptyList(), completed.response.toolCalls)
    }

    @Test
    fun `tool_use blocks accumulate partial json into a complete ToolCall`() {
        val events = feed(
            AnthropicStreamAccumulator(),
            """{"type":"content_block_start","index":0,"content_block":{"type":"tool_use","id":"tu_1","name":"add_app","input":{}}}""",
            """{"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"{\"name\":"}}""",
            """{"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"\"Chrome\"}"}}""",
            """{"type":"content_block_stop","index":0}""",
            """{"type":"message_delta","delta":{"stop_reason":"tool_use"}}""",
            """{"type":"message_stop"}""",
        )
        val completed = events.last() as ChatResponseEvent.Completed
        assertEquals(StopReason.ToolUse, completed.response.stopReason)
        assertEquals(1, completed.response.toolCalls.size)
        val call = completed.response.toolCalls.single()
        assertEquals("tu_1", call.id)
        assertEquals("add_app", call.name)
        assertEquals("""{"name":"Chrome"}""", call.argumentsJson)
        assertEquals(null, completed.response.text)
    }

    @Test
    fun `tool_use block with no input_json_delta fragments yields empty object arguments`() {
        val events = feed(
            AnthropicStreamAccumulator(),
            """{"type":"content_block_start","index":0,"content_block":{"type":"tool_use","id":"tu_1","name":"list_apps","input":{}}}""",
            """{"type":"content_block_stop","index":0}""",
            """{"type":"message_delta","delta":{"stop_reason":"tool_use"}}""",
            """{"type":"message_stop"}""",
        )
        val completed = events.last() as ChatResponseEvent.Completed
        assertEquals(1, completed.response.toolCalls.size)
        val call = completed.response.toolCalls.single()
        assertEquals("{}", call.argumentsJson)
    }
}
