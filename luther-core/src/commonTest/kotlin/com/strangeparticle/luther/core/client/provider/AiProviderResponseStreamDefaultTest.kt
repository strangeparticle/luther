package com.strangeparticle.luther.core.client.provider

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AiProviderResponseStreamDefaultTest {
    private val cfg = object : ProviderConfig {}
    private val req = ChatRequest(modelId = "m", systemPrompt = "s", messages = emptyList(), tools = emptyList())

    private val blockingOnlyProvider = object : AiProvider {
        override val id = "fake"
        override val displayName = "Fake"
        override fun isConfigured(config: ProviderConfig) = true
        override suspend fun listModels(config: ProviderConfig): List<Model> = emptyList()
        override suspend fun respond(config: ProviderConfig, request: ChatRequest): ChatResponse =
            ChatResponse(text = "hi", toolCalls = emptyList(), stopReason = StopReason.Stop)
        // does NOT override responseStream — exercises the default
    }

    @Test
    fun `default responseStream emits a single Completed and no text deltas`() = runTest {
        val events = blockingOnlyProvider.responseStream(cfg, req).toList()
        assertEquals(1, events.size)
        val completed = assertIs<ChatResponseEvent.Completed>(events.single())
        assertEquals("hi", completed.response.text)
    }
}
