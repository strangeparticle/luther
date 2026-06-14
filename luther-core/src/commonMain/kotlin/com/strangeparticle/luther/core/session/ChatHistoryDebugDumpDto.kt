package com.strangeparticle.luther.core.session

import kotlinx.serialization.Serializable

@Serializable
internal data class ChatHistoryDebugDumpDto(
    val kind: String = "SpringboardAiDebugDump",
    val provider: String,
    val model: String,
    val systemPrompt: String,
    val groupCount: Int,
    val itemCount: Int,
    val groups: List<ChatHistoryGroupDebugDto>,
)
