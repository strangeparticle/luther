package com.strangeparticle.luther.core.client.provider.openai.response

import com.strangeparticle.luther.core.client.provider.ChatResponseEvent
import com.strangeparticle.luther.core.client.provider.ProviderErrorType
import com.strangeparticle.luther.core.client.provider.ProviderException
import com.strangeparticle.luther.core.client.provider.StopReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OpenAiStreamAccumulatorTest {
    private fun feed(acc: OpenAiStreamAccumulator, vararg data: String): List<ChatResponseEvent> {
        val out = mutableListOf<ChatResponseEvent>()
        for (d in data) acc.onData(d)?.let { out += it }
        out += acc.completed()
        return out
    }

    @Test
    fun `content deltas concatenate and DONE is ignored`() {
        val events = feed(
            OpenAiStreamAccumulator(),
            """{"choices":[{"index":0,"delta":{"role":"assistant","content":""},"finish_reason":null}]}""",
            """{"choices":[{"index":0,"delta":{"content":"Hel"},"finish_reason":null}]}""",
            """{"choices":[{"index":0,"delta":{"content":"lo"},"finish_reason":null}]}""",
            """{"choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}""",
            "[DONE]",
        )
        assertEquals(ChatResponseEvent.TextDelta("Hel"), events[0])
        assertEquals(ChatResponseEvent.TextDelta("lo"), events[1])
        val completed = events.last() as ChatResponseEvent.Completed
        assertEquals("Hello", completed.response.text)
        assertEquals(StopReason.Stop, completed.response.stopReason)
    }

    @Test
    fun `tool_call fragments accumulate by index into a complete ToolCall`() {
        val events = feed(
            OpenAiStreamAccumulator(),
            """{"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"add_app","arguments":""}}]},"finish_reason":null}]}""",
            """{"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"name\":"}}]},"finish_reason":null}]}""",
            """{"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"Chrome\"}"}}]},"finish_reason":null}]}""",
            """{"choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}]}""",
        )
        val completed = events.last() as ChatResponseEvent.Completed
        assertEquals(StopReason.ToolUse, completed.response.stopReason)
        val call = completed.response.toolCalls.single()
        assertEquals("call_1", call.id)
        assertEquals("add_app", call.name)
        assertEquals("""{"name":"Chrome"}""", call.argumentsJson)
    }

    @Test
    fun `tool_call with empty arguments and no further fragments yields empty object arguments`() {
        val events = feed(
            OpenAiStreamAccumulator(),
            """{"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"list_apps","arguments":""}}]},"finish_reason":null}]}""",
            """{"choices":[{"index":0,"delta":{},"finish_reason":"tool_calls"}]}""",
        )
        val completed = events.last() as ChatResponseEvent.Completed
        val call = completed.response.toolCalls.single()
        assertEquals("{}", call.argumentsJson)
    }

    @Test
    fun `mid-stream error payload throws ProviderException classified as Unknown`() {
        val accumulator = OpenAiStreamAccumulator()
        accumulator.onData("""{"choices":[{"index":0,"delta":{"role":"assistant","content":"Hel"},"finish_reason":null}]}""")
        val exception = assertFailsWith<ProviderException> {
            accumulator.onData("""{"error":{"message":"boom","type":"server_error"}}""")
        }
        assertEquals(ProviderErrorType.Unknown, exception.classified)
    }
}
