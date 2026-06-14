package com.strangeparticle.luther.core.toolcall

import kotlinx.serialization.SerializationException

/** Dispatches model-requested tool calls to registered handlers. */
internal class ToolCallDispatcher(
    private val registry: ToolCallRegistry,
) {
    suspend fun execute(
        toolCallId: String,
        providerToolId: String,
        argumentsAsJsonString: String,
        context: ToolCallExecutionContext,
    ): ToolCallHandlerResponse {
        val handler = registry.getHandler(providerToolId)
            ?: return ToolCallExecutionResult(
                success = false,
                message = "Unknown tool: '$providerToolId'",
                code = "unknown_tool",
            )
        return try {
            handler.executeToolCallHandler(toolCallId, argumentsAsJsonString, context)
        } catch (e: SerializationException) {
            ToolCallExecutionResult(
                success = false,
                message = "Invalid arguments for '$providerToolId': ${e.message ?: "deserialization failed"}",
                code = "invalid_arguments",
            )
        }
    }
}
