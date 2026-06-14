package com.strangeparticle.luther.core.toolcall

import com.strangeparticle.luther.core.client.provider.ToolDefinition

/**
 * Registry of provider-visible tool calls. Dispatch belongs to [ToolCallDispatcher]
 * so registry lookup stays reusable across host applications.
 */
internal class ToolCallRegistry {

    // Key = provider-visible tool-call name (for example `add_app` or `save_springboard`).
    private val tools: MutableMap<String, ToolCallHandler> = linkedMapOf()

    fun register(tool: ToolCallHandler) {
        require(!tools.containsKey(tool.providerToolId)) {
            "Tool '${tool.providerToolId}' is already registered."
        }
        tools[tool.providerToolId] = tool
    }

    fun getDefinitions(): List<ToolDefinition> = tools.values.map { it.toDefinition() }

    fun getHandlers(): List<ToolCallHandler> = tools.values.toList()

    fun isRegistered(toolName: String): Boolean = toolName in tools

    fun getHandler(providerToolId: String): ToolCallHandler? = tools[providerToolId]
}
