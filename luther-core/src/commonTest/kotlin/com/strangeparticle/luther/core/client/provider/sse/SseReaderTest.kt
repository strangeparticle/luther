package com.strangeparticle.luther.core.client.provider.sse

import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SseReaderTest {
    private suspend fun collect(raw: String): List<String> {
        val out = mutableListOf<String>()
        readSseData(ByteReadChannel(raw)) { out += it }
        return out
    }

    @Test
    fun `emits one payload per event and skips framing lines`() = runTest {
        val raw = "event: a\ndata: {\"x\":1}\n\n" + ": keep-alive comment\n\n" + "data: {\"y\":2}\n\n"
        assertEquals(listOf("{\"x\":1}", "{\"y\":2}"), collect(raw))
    }

    @Test
    fun `concatenates multiple data lines within one event`() = runTest {
        val raw = "data: line1\ndata: line2\n\n"
        assertEquals(listOf("line1line2"), collect(raw))
    }

    @Test
    fun `flushes a trailing event with no blank terminator`() = runTest {
        assertEquals(listOf("{\"z\":3}"), collect("data: {\"z\":3}\n"))
    }
}
