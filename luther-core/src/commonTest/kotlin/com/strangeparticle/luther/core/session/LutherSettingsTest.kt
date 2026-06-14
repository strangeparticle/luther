package com.strangeparticle.luther.core.session

import com.strangeparticle.luther.core.client.provider.AiProvider
import com.strangeparticle.luther.core.client.provider.ChatRequest
import com.strangeparticle.luther.core.client.provider.ChatResponse
import com.strangeparticle.luther.core.client.provider.Model
import com.strangeparticle.luther.core.client.provider.ProviderConfig
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private data class Cfg(val key: String) : ProviderConfig
private val provider = object : AiProvider {
    override val id = "p"; override val displayName = "P"
    override fun isConfigured(config: ProviderConfig) = (config as Cfg).key.isNotBlank()
    override suspend fun listModels(config: ProviderConfig): List<Model> = emptyList()
    override suspend fun sendChat(config: ProviderConfig, request: ChatRequest): ChatResponse =
        throw UnsupportedOperationException()
}

class LutherSettingsTest {
    @Test fun complete_whenProviderConfiguredAndModelSet() {
        val s = LutherSettings("p", "m", Cfg("k"))
        assertTrue(s.isComplete(provider))
    }
    @Test fun incomplete_whenModelBlank() {
        assertFalse(LutherSettings("p", "", Cfg("k")).isComplete(provider))
    }
    @Test fun incomplete_whenProviderNotConfigured() {
        assertFalse(LutherSettings("p", "m", Cfg("")).isComplete(provider))
    }
}
