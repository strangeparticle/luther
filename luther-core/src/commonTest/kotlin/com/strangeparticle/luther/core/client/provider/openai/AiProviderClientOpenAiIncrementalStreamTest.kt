package com.strangeparticle.luther.core.client.provider.openai

import com.strangeparticle.luther.core.client.provider.ChatRequest
import com.strangeparticle.luther.core.client.provider.ChatResponseEvent
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/**
 * OpenAI counterpart to [AiProviderClientAnthropicIncrementalStreamTest] — guards the *timing* of
 * streaming, which the delta-parsing tests cannot observe because they hand MockEngine a complete
 * SSE body. See that test for the full rationale.
 */
class AiProviderClientOpenAiIncrementalStreamTest {

    private fun request() = ChatRequest("gpt-5", "sys", emptyList(), emptyList())

    private val openingChunk =
        "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hi\"},\"finish_reason\":null}]}\n\n"

    private val closingChunks =
        "data: {\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n" +
            "data: [DONE]\n\n"

    @Test
    fun `emits a delta before the response body is complete`() = runTest(timeout = 10.seconds) {
        val bodyChannel = ByteChannel(autoFlush = true)
        val client = HttpClient(
            MockEngine {
                respond(bodyChannel, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/event-stream"))
            },
        )
        val received = Channel<ChatResponseEvent>(Channel.UNLIMITED)
        val collector = launch {
            AiProviderClientOpenAi(client, apiKeyProvider = { "sk-test" })
                .responseStream(request())
                .collect { event -> received.send(event) }
        }

        bodyChannel.writeStringUtf8(openingChunk)
        bodyChannel.flush()

        // The body is still open. A buffering client cannot have produced anything yet, so this
        // receive() is what fails (by timeout) when the streaming path regresses.
        assertEquals(ChatResponseEvent.TextDelta("Hi"), received.receive())

        bodyChannel.writeStringUtf8(closingChunks)
        bodyChannel.flush()
        bodyChannel.close()
        collector.join()
    }
}
