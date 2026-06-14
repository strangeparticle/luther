package com.strangeparticle.luther.core.session.event

data class ProviderModelChangedChatHistoryItem(
    val providerLabel: String,
    val modelLabel: String,
) : ChatHistoryItem {
    val displayText: String
        get() = "Active AI provider/model: $providerLabel:$modelLabel"
}
