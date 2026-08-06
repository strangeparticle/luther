package com.strangeparticle.luther.core.client.provider.openai

import com.strangeparticle.luther.core.client.provider.AiProvider
import com.strangeparticle.luther.core.client.provider.ChatRequest
import com.strangeparticle.luther.core.client.provider.ChatResponse
import com.strangeparticle.luther.core.client.provider.Model
import com.strangeparticle.luther.core.client.provider.ProviderConfig
import io.ktor.client.HttpClient

internal class OpenAiProvider(private val httpClient: HttpClient) : AiProvider {
    override val id = "openai"
    override val displayName = "OpenAI"
    override val preferredModelIds = listOf("gpt-5", "gpt-4.1")

    override fun isConfigured(config: ProviderConfig): Boolean =
        (config as OpenAiConfig).apiKey.isNotBlank()

    override suspend fun listModels(config: ProviderConfig): List<Model> =
        client(config).listModels()

    override suspend fun respond(config: ProviderConfig, request: ChatRequest): ChatResponse =
        client(config).sendChat(request)

    private fun client(config: ProviderConfig) =
        AiProviderClientOpenAi(httpClient = httpClient, apiKeyProvider = { (config as OpenAiConfig).apiKey })
}
