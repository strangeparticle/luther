package com.strangeparticle.luther.core.session

import com.strangeparticle.luther.core.client.provider.AiProvider
import com.strangeparticle.luther.core.client.provider.ProviderConfig

/** All runtime-mutable assistant settings. Used both as the initial config at
 *  construction and as the whole-object argument to LutherSession.updateConfiguration. */
data class LutherSettings(
    val providerId: String,
    val modelId: String,
    val providerConfig: ProviderConfig,
) {
    fun isComplete(provider: AiProvider): Boolean =
        modelId.isNotBlank() && provider.isConfigured(providerConfig)
}
