package com.strangeparticle.luther.core.client.provider.anthropic

import com.strangeparticle.luther.core.client.provider.ChatRequest
import com.strangeparticle.luther.core.client.provider.ChatResponseEvent
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AiProviderClientAnthropicStreamTest {
    private fun req() = ChatRequest("claude-sonnet-4-6", "sys", emptyList(), emptyList())
    private val sse = buildString {
        append("event: content_block_start\ndata: {\"type\":\"content_block_start\",\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}\n\n")
        append("event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"Hi\"}}\n\n")
        append("event: message_delta\ndata: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"}}\n\n")
        append("event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n")
    }

    @Test
    fun `responseStream posts stream true and emits deltas then completed`() = runTest {
        var capturedBody = ""
        val client = HttpClient(MockEngine { request ->
            capturedBody = (request.body as io.ktor.http.content.TextContent).text
            respond(sse, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/event-stream"))
        })
        val events = AiProviderClientAnthropic(client, apiKeyProvider = { "sk-ant-test" })
            .responseStream(req()).toList()

        assertEquals(true, capturedBody.contains("\"stream\":true"))
        assertEquals(ChatResponseEvent.TextDelta("Hi"), events.first())
        val completed = assertIs<ChatResponseEvent.Completed>(events.last())
        assertEquals("Hi", completed.response.text)
    }
}
