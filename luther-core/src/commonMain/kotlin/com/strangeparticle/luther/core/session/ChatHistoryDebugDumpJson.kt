package com.strangeparticle.luther.core.session

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val chatHistoryDebugDumpJson = Json {
    prettyPrint = true
    encodeDefaults = true
    classDiscriminator = "kind"
}

fun buildChatHistoryDebugDumpJson(
    groups: List<ChatHistoryGroup>,
    providerLabel: String,
    modelLabel: String,
    systemPrompt: String,
): String {
    val dto = ChatHistoryDebugDumpDto(
        provider = providerLabel,
        model = modelLabel,
        systemPrompt = systemPrompt,
        groupCount = groups.size,
        itemCount = groups.sumOf { it.items.size },
        groups = groups.mapIndexed { index, group -> ChatHistoryGroupDebugDto.from(index, group) },
    )
    return chatHistoryDebugDumpJson.encodeToString(dto)
}
