package com.strangeparticle.luther.core.client.provider

import com.strangeparticle.luther.core.client.provider.ChatRequest
import com.strangeparticle.luther.core.client.provider.ChatResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private data class FakeConfig(val key: String) : ProviderConfig

private class FakeProvider(
    private val models: List<Model>,
) : AiProvider {
    override val id = "fake"
    override val displayName = "Fake"
    override fun isConfigured(config: ProviderConfig) = (config as FakeConfig).key.isNotBlank()
    override suspend fun listModels(config: ProviderConfig): List<Model> = models
    override suspend fun sendChat(config: ProviderConfig, request: ChatRequest): ChatResponse =
        throw UnsupportedOperationException()
}

class LutherProviderCatalogTest {
    @Test
    fun availableProviders_listsRegisteredProviders() {
        val catalog = LutherProviderCatalog(listOf(FakeProvider(emptyList())))
        assertEquals(listOf(Choice("fake", "Fake")), catalog.availableProviders())
    }

    @Test
    fun availableModels_filtersAndMapsThroughProviderOrdering() = runTest {
        val models = listOf(
            Model("m1", "Model One", supportsToolCalling = true),
            Model("m2", null, supportsToolCalling = false),
        )
        val catalog = LutherProviderCatalog(listOf(FakeProvider(models)))
        val result = catalog.availableModels("fake", FakeConfig("k"))
        assertEquals(listOf(Choice("m1", "Model One")), result)
    }
}
