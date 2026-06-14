package com.strangeparticle.luther.core.toolcall

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Generic result for dispatcher-level tool-call execution outcomes. */
@Serializable
internal data class ToolCallExecutionResult(
    val success: Boolean,
    val message: String? = null,
    val code: String? = null,
) : ToolCallHandlerResponse {
    override fun toProviderMessageContent(json: Json): String = json.encodeToString(this)
}
