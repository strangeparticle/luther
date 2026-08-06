package com.strangeparticle.luther.core.client.provider.openai

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

class AiProviderClientOpenAiStreamTest {
    private fun req() = ChatRequest("gpt-5", "sys", emptyList(), emptyList())
    private val sse = buildString {
        append("data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hi\"},\"finish_reason\":null}]}\n\n")
        append("data: {\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n")
        append("data: [DONE]\n\n")
    }

    @Test
    fun `responseStream posts stream true and emits deltas then completed`() = runTest {
        var capturedBody = ""
        val client = HttpClient(MockEngine { request ->
            capturedBody = (request.body as io.ktor.http.content.TextContent).text
            respond(sse, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/event-stream"))
        })
        val events = AiProviderClientOpenAi(client, apiKeyProvider = { "sk-test" }).responseStream(req()).toList()
        assertEquals(true, capturedBody.contains("\"stream\":true"))
        assertEquals(ChatResponseEvent.TextDelta("Hi"), events.first())
        val completed = assertIs<ChatResponseEvent.Completed>(events.last())
        assertEquals("Hi", completed.response.text)
    }
}
