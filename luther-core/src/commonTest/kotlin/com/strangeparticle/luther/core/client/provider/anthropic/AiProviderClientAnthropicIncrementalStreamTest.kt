package com.strangeparticle.luther.core.client.provider.anthropic

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
 * Guards the *timing* of streaming, which the delta-parsing tests cannot observe.
 *
 * The other stream tests hand MockEngine a complete SSE body, so they pass whether or not the
 * client streams — a fully buffered read produces exactly the same events, just all at once.
 * This test keeps the response body channel OPEN and asserts a delta surfaces anyway. If the
 * client reads the response with a body-loading call (`httpClient.post(...)`) instead of a
 * prepared/streaming one, nothing is emitted until the body completes and this test times out.
 */
class AiProviderClientAnthropicIncrementalStreamTest {

    private fun request() = ChatRequest("claude-sonnet-4-6", "sys", emptyList(), emptyList())

    private val openingChunks =
        "event: content_block_start\ndata: {\"type\":\"content_block_start\",\"index\":0," +
            "\"content_block\":{\"type\":\"text\",\"text\":\"\"}}\n\n" +
            "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"index\":0," +
            "\"delta\":{\"type\":\"text_delta\",\"text\":\"Hi\"}}\n\n"

    private val closingChunks =
        "event: message_delta\ndata: {\"type\":\"message_delta\"," +
            "\"delta\":{\"stop_reason\":\"end_turn\"}}\n\n" +
            "event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n"

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
            AiProviderClientAnthropic(client, apiKeyProvider = { "sk-ant-test" })
                .responseStream(request())
                .collect { event -> received.send(event) }
        }

        bodyChannel.writeStringUtf8(openingChunks)
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
