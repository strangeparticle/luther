package com.strangeparticle.luther.core.toolcall

import com.strangeparticle.luther.core.client.provider.ToolDefinition
import kotlinx.serialization.json.JsonObject

/**
 * Provider-visible tool call implementation. Host applications provide concrete
 * handlers and a host-specific [ToolCallExecutionContext].
 */
interface ToolCallHandler {
    val providerToolId: String
    val description: String
    val schema: JsonObject

    val requiresUserConfirmation: Boolean
        get() = false

    /**
     * Run the tool call against the supplied raw provider argument JSON and the
     * runtime [context]. Concrete implementations should parse the request via
     * [decodeToolCallHandlerRequest], then delegate to a typed overload that does
     * the real work.
     *
     * Tool calls should let request decoding failures propagate; [ToolCallDispatcher]
     * converts them into host-specific invalid-arguments responses. Tool calls must
     * NOT throw on domain validation or precondition failures — they should return a
     * structured [ToolCallHandlerResponse].
     */
    suspend fun executeToolCallHandler(
        toolCallId: String,
        argumentsAsJsonString: String,
        context: ToolCallExecutionContext,
    ): ToolCallHandlerResponse

    /** Provider-neutral definition for this tool call, used by the AI client. */
    fun toDefinition(): ToolDefinition = ToolDefinition(
        name = providerToolId,
        description = description,
        schema = schema,
    )
}
