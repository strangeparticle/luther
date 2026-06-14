package com.strangeparticle.luther.core.toolcall

import kotlinx.serialization.json.Json

/** Marker for host-specific tool-call execution results. */
interface ToolCallHandlerResponse {
    val endsTurn: Boolean
        get() = false

    fun toProviderMessageContent(json: Json = Json): String

    fun toTranscriptOutput(providerMessageContent: String): String = providerMessageContent
}
