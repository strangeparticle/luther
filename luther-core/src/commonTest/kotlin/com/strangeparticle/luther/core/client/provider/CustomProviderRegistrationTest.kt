package com.strangeparticle.luther.core.client.provider

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The built-in providers require an HttpClient, but these registration tests never make a
// request. A MockEngine client constructs on every platform, unlike the default HttpClient(),
// which needs a concrete platform engine (cio/darwin/curl/js) on the classpath.
private fun mockHttpClient(): HttpClient = HttpClient(MockEngine { respond("ok") })

private data class CustomFakeConfig(val token: String) : ProviderConfig

private class CustomFakeProvider : AiProvider {
    override val id = "fake"
    override val displayName = "Fake"
    override val preferredModelIds = listOf("fake-pro")

    override fun isConfigured(config: ProviderConfig): Boolean {
        return (config as CustomFakeConfig).token.isNotBlank()
    }

    override suspend fun listModels(config: ProviderConfig): List<Model> = listOf(
        Model("fake-mini", "Fake Mini", supportsToolCalling = true),
        Model("fake-pro", "Fake Pro", supportsToolCalling = true),
        Model("fake-embed", "Fake Embed", supportsToolCalling = false),
    )

    override suspend fun sendChat(config: ProviderConfig, request: ChatRequest): ChatResponse =
        ChatResponse("ok", emptyList(), StopReason.Stop)
}

class CustomProviderRegistrationTest {
    @Test
    fun availableProviders_includesFakeAndBuiltIns() {
        val providers = LutherBuiltInProviders.all(mockHttpClient()) + listOf(CustomFakeProvider())
        val catalog = LutherProviderCatalog(providers)

        val available = catalog.availableProviders()

        assertTrue(
            available.contains(Choice("fake", "Fake")),
            "Expected custom fake provider in availableProviders()",
        )
        assertTrue(
            available.contains(Choice("openai", "OpenAI")),
            "Expected built-in openai provider in availableProviders()",
        )
        assertTrue(
            available.contains(Choice("anthropic", "Anthropic")),
            "Expected built-in anthropic provider in availableProviders()",
        )
    }

    @Test
    fun availableModels_returnsToolCapableModelsInPreferredFirstOrder() = runTest {
        val providers = LutherBuiltInProviders.all(mockHttpClient()) + listOf(CustomFakeProvider())
        val catalog = LutherProviderCatalog(providers)

        val models = catalog.availableModels("fake", CustomFakeConfig("t"))

        assertEquals(
            listOf(Choice("fake-pro", "Fake Pro"), Choice("fake-mini", "Fake Mini")),
            models,
            "Expected tool-capable models in preferred-first order; non-tool-capable fake-embed filtered out",
        )
    }

    @Test
    fun availableModels_returnsEmptyWhenNotConfigured() = runTest {
        val providers = LutherBuiltInProviders.all(mockHttpClient()) + listOf(CustomFakeProvider())
        val catalog = LutherProviderCatalog(providers)

        val models = catalog.availableModels("fake", CustomFakeConfig(""))

        assertEquals(emptyList(), models, "Expected empty list when token is blank (not configured)")
    }
}
