package com.strangeparticle.luther.core.client.provider.anthropic

import com.strangeparticle.luther.core.client.provider.AiProvider
import com.strangeparticle.luther.core.client.provider.ChatRequest
import com.strangeparticle.luther.core.client.provider.ChatResponse
import com.strangeparticle.luther.core.client.provider.ChatResponseEvent
import com.strangeparticle.luther.core.client.provider.Model
import com.strangeparticle.luther.core.client.provider.ProviderConfig
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow

internal class AnthropicProvider(private val httpClient: HttpClient) : AiProvider {
    override val id = "anthropic"
    override val displayName = "Anthropic"
    override val preferredModelIds = listOf("claude-sonnet-4-6", "claude-3-5-sonnet-latest")

    override fun isConfigured(config: ProviderConfig): Boolean =
        (config as AnthropicConfig).apiKey.isNotBlank()

    override suspend fun listModels(config: ProviderConfig): List<Model> =
        client(config).listModels()

    override suspend fun respond(config: ProviderConfig, request: ChatRequest): ChatResponse =
        client(config).sendChat(request)

    override fun responseStream(config: ProviderConfig, request: ChatRequest): Flow<ChatResponseEvent> =
        client(config).responseStream(request)

    private fun client(config: ProviderConfig) =
        AiProviderClientAnthropic(httpClient = httpClient, apiKeyProvider = { (config as AnthropicConfig).apiKey })
}
