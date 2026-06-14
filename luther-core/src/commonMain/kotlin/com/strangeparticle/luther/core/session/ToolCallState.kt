package com.strangeparticle.luther.core.session

sealed class ToolCallState {
    data object Pending : ToolCallState()

    data object ApprovalRequested : ToolCallState()

    data class ApprovalResponded(val approved: Boolean) : ToolCallState()

    data class OutputAvailable(val output: String) : ToolCallState()

    data class OutputError(val message: String) : ToolCallState()

    data object OutputDenied : ToolCallState()
}
