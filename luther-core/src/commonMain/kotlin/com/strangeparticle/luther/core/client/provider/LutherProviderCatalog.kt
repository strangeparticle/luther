package com.strangeparticle.luther.core.client.provider

/**
 * Stateless model/provider discovery. Needs only {providerId, ProviderConfig} — no
 * running session. Populates the provider and model dropdowns for the host's settings
 * UI and the in-pane model picker. Each provider owns its own transport.
 */
class LutherProviderCatalog(
    private val providers: List<AiProvider>,
) {
    fun availableProviders(): List<Choice> =
        providers.map { Choice(it.id, it.displayName) }

    suspend fun availableModels(providerId: String, config: ProviderConfig): List<Choice> {
        val provider = providers.firstOrNull { it.id == providerId } ?: return emptyList()
        if (!provider.isConfigured(config)) return emptyList()
        val models = provider.listModels(config)
        return orderModelsForPicker(models, provider.preferredModelIds).map { Choice(it.id, it.displayName ?: it.id) }
    }
}
