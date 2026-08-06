package com.strangeparticle.luther.core.client.provider.sse

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line

/**
 * Reads a Server-Sent-Events stream line by line off [channel] and invokes [onData] once per event
 * with the concatenated `data:` payload. Deliberately minimal: it ignores `event:` lines and
 * comments (`:`-prefixed) because our providers carry the discriminating type inside the JSON
 * payload. No ktor SSE plugin, so this works across every engine that streams a response body.
 */
internal suspend fun readSseData(channel: ByteReadChannel, onData: suspend (String) -> Unit) {
    val data = StringBuilder()
    while (true) {
        val line = channel.readUTF8Line() ?: break
        when {
            line.isEmpty() -> {
                if (data.isNotEmpty()) { onData(data.toString()); data.clear() }
            }
            line.startsWith(":") -> Unit // comment / keep-alive
            line.startsWith("data:") -> data.append(line.removePrefix("data:").trim())
            else -> Unit // event:, id:, retry: — not needed here
        }
    }
    if (data.isNotEmpty()) onData(data.toString())
}
