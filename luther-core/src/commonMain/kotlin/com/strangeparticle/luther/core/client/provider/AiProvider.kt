package com.strangeparticle.luther.core.client.provider

/**
 * Framework contract for an AI provider integration. Providers live whole inside
 * luther-core and know nothing about the host's settings system: the host supplies
 * a typed [ProviderConfig], and the provider owns its own transport.
 *
 * Each provider casts the marker [ProviderConfig] to its own concrete config type
 * (e.g. AnthropicConfig) at the top of each method.
 */
interface AiProvider {
    val id: String
    val displayName: String

    /** Model ids the picker should surface first, in order. Empty by default. */
    val preferredModelIds: List<String> get() = emptyList()

    /** True if [config] carries everything needed to make a working request. */
    fun isConfigured(config: ProviderConfig): Boolean

    /** List the chat-completion-capable models the provider exposes for [config]. */
    suspend fun listModels(config: ProviderConfig): List<Model>

    /** Send [request] using [config] and suspend until the provider returns a full response. */
    suspend fun sendChat(config: ProviderConfig, request: ChatRequest): ChatResponse
}
