package com.strangeparticle.luther.cmp

import com.strangeparticle.luther.core.client.provider.Choice

internal data class AiChatPaneModelPickerState(
    val selectedModelId: String,
    val selectedModelLabel: String,
    val options: List<Choice>,
    val isLoading: Boolean,
    val errorMessage: String?,
    val onRefresh: () -> Unit,
    val onSelectModel: (String) -> Unit,
)
