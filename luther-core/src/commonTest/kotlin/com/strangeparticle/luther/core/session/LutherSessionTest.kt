package com.strangeparticle.luther.core.session

import com.strangeparticle.luther.core.client.provider.AiProvider
import com.strangeparticle.luther.core.client.provider.ChatRequest
import com.strangeparticle.luther.core.client.provider.ChatResponse
import com.strangeparticle.luther.core.client.provider.Model
import com.strangeparticle.luther.core.client.provider.ProviderConfig
import com.strangeparticle.luther.core.client.provider.StopReason
import com.strangeparticle.luther.core.toolcall.ToolCallExecutionContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private data class TestProviderConfig(val key: String) : ProviderConfig

/**
 * Test provider whose `respond` records the [TestProviderConfig.key] it was bound to.
 *
 * The old design exposed `createClient`, so "did the session rebuild?" was observable by
 * counting client-factory invocations. The new design binds `respond` per manager:
 * `buildManager` captures the current provider + config in a fresh `respond` lambda, and a
 * rebuild produces a new lambda bound to the new config. We make rebuilds observable by
 * recording, on every `respond` invocation, the config key the active binding closed over.
 * A model-only update reuses the existing binding (same key); a config change rebuilds and the
 * binding closes over the new key.
 */
private class RecordingTestProvider : AiProvider {
    val sendChatBoundKeys: MutableList<String> = mutableListOf()
    override val id = "p"
    override val displayName = "P"
    override fun isConfigured(config: ProviderConfig) = (config as TestProviderConfig).key.isNotBlank()
    override suspend fun listModels(config: ProviderConfig): List<Model> = emptyList()
    override suspend fun respond(config: ProviderConfig, request: ChatRequest): ChatResponse {
        sendChatBoundKeys += (config as TestProviderConfig).key
        return ChatResponse(text = "", toolCalls = emptyList(), stopReason = StopReason.Stop)
    }
}

private val NoopExecutionContextFactory = object : AiSessionToolCallExecutionContextFactory {
    override fun createToolCallExecutionContext(
        onStateChanged: () -> Unit,
        awaitUserApproval: suspend (toolCallId: String) -> Boolean,
    ): ToolCallExecutionContext = object : ToolCallExecutionContext {}
}

private val NoopSnapshotProvider = object : AiSessionSnapshotProvider {
    override fun getSnapshotJson(): String = "{}"
}

class LutherSessionTest {
    @Test fun factory_rejectsIncompleteSettings() {
        assertFailsWith<IllegalArgumentException> {
            createLutherSession(
                providers = listOf(RecordingTestProvider()),
                settings = LutherSettings("p", "", TestProviderConfig("")),
                toolHandlers = emptyList(),
                executionContextFactory = NoopExecutionContextFactory,
                snapshotProvider = NoopSnapshotProvider,
                systemPromptProvider = { "" },
            )
        }
    }

    @Test fun modelOnlyUpdate_doesNotRebuildClient() = runTest {
        val provider = RecordingTestProvider()
        val session = createLutherSession(
            providers = listOf(provider),
            settings = LutherSettings("p", "m1", TestProviderConfig("k")),
            toolHandlers = emptyList(),
            executionContextFactory = NoopExecutionContextFactory,
            snapshotProvider = NoopSnapshotProvider,
            systemPromptProvider = { "" },
        )
        session.updateConfiguration(LutherSettings("p", "m2", TestProviderConfig("k")))
        // The model-only change must NOT rebuild the manager: the still-active binding closed
        // over the original config key "k".
        session.submit("hi").join()
        assertEquals(listOf("k"), provider.sendChatBoundKeys)
        assertEquals("m2", session.status.value.modelId)
        session.close()
    }

    @Test fun configChange_rebuildsClient_keepsHistoryReference() = runTest {
        val provider = RecordingTestProvider()
        val session = createLutherSession(
            providers = listOf(provider),
            settings = LutherSettings("p", "m1", TestProviderConfig("k")),
            toolHandlers = emptyList(),
            executionContextFactory = NoopExecutionContextFactory,
            snapshotProvider = NoopSnapshotProvider,
            systemPromptProvider = { "" },
        )
        val historyRef = session.chatHistory
        session.updateConfiguration(LutherSettings("p", "m1", TestProviderConfig("k2")))
        // A config change rebuilds the manager, binding a fresh `sendChat` to the new config "k2".
        session.submit("hi").join()
        assertEquals(listOf("k2"), provider.sendChatBoundKeys)
        assertTrue(session.chatHistory === historyRef)
        session.close()
    }

    private fun newSession() = createLutherSession(
        providers = listOf(RecordingTestProvider()),
        settings = LutherSettings("p", "m1", TestProviderConfig("k")),
        toolHandlers = emptyList(),
        executionContextFactory = NoopExecutionContextFactory,
        snapshotProvider = NoopSnapshotProvider,
        systemPromptProvider = { "" },
    )

    @Test fun construction_seedsProviderModelThenTerseHelp() {
        val session = newSession()
        val groups = session.chatHistory.value
        assertEquals(ChatHistoryGroupType.PROVIDER_MODEL_CHANGE, groups.first().type)
        assertEquals(ChatHistoryGroupType.LOCAL_COMMAND, groups[1].type)
        session.close()
    }

    @Test fun notifyUndoPerformed_appendsLocalCommandEntry() {
        val session = newSession()
        val before = session.chatHistory.value.size
        session.notifyUndoPerformed("Undid last change.")
        val groups = session.chatHistory.value
        assertEquals(before + 1, groups.size)
        assertEquals(ChatHistoryGroupType.LOCAL_COMMAND, groups.last().type)
        session.close()
    }

    @Test fun modelChange_appendsProviderModelEntry() {
        val session = newSession()
        val before = session.chatHistory.value.count { it.type == ChatHistoryGroupType.PROVIDER_MODEL_CHANGE }
        session.updateConfiguration(LutherSettings("p", "m2", TestProviderConfig("k")))
        val after = session.chatHistory.value.count { it.type == ChatHistoryGroupType.PROVIDER_MODEL_CHANGE }
        assertEquals(before + 1, after)
        session.close()
    }
}
