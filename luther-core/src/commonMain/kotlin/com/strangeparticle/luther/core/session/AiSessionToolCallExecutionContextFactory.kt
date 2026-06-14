package com.strangeparticle.luther.core.session

import com.strangeparticle.luther.core.toolcall.ToolCallExecutionContext

interface AiSessionToolCallExecutionContextFactory {
    fun createToolCallExecutionContext(
        onStateChanged: () -> Unit,
        awaitUserApproval: suspend (toolCallId: String) -> Boolean,
    ): ToolCallExecutionContext
}
