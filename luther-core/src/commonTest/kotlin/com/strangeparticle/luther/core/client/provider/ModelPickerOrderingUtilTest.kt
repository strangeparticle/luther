package com.strangeparticle.luther.core.client.provider

import kotlin.test.Test
import kotlin.test.assertEquals

class ModelPickerOrderingUtilTest {

    private fun model(id: String, supportsToolCalling: Boolean = true): Model =
        Model(id = id, displayName = "$id-display", supportsToolCalling = supportsToolCalling)

    @Test
    fun nonToolCapableModelsAreDropped() {
        val models = listOf(
            model("tool-capable", supportsToolCalling = true),
            model("not-capable", supportsToolCalling = false),
        )
        val result = orderModelsForPicker(models, preferredModelIds = emptyList<String>())
        assertEquals(listOf(model("tool-capable")), result)
    }

    @Test
    fun preferredIdsAreOrderedFirst() {
        val models = listOf(
            model("alpha"),
            model("beta"),
            model("gamma"),
        )
        val result = orderModelsForPicker(models, preferredModelIds = listOf("gamma", "alpha"))
        assertEquals(listOf(model("gamma"), model("alpha"), model("beta")), result)
    }

    @Test
    fun preferredIdAbsentFromModelsIsSkipped() {
        val models = listOf(
            model("alpha"),
            model("beta"),
        )
        val result = orderModelsForPicker(models, preferredModelIds = listOf("missing", "beta"))
        assertEquals(listOf(model("beta"), model("alpha")), result)
    }

    @Test
    fun remainderPreservesOriginalInputOrder() {
        val models = listOf(
            model("first"),
            model("second"),
            model("third"),
            model("fourth"),
        )
        val result = orderModelsForPicker(models, preferredModelIds = listOf("third"))
        assertEquals(
            listOf(model("third"), model("first"), model("second"), model("fourth")),
            result,
        )
    }

    @Test
    fun emptyPreferredIdsReturnsAllToolCapableModelsInOriginalOrder() {
        val models = listOf(
            model("alpha"),
            model("beta", supportsToolCalling = false),
            model("gamma"),
        )
        val result = orderModelsForPicker(models, preferredModelIds = emptyList<String>())
        assertEquals(listOf(model("alpha"), model("gamma")), result)
    }

    @Test
    fun preferredIdPresentButNotToolCapableIsStillDropped() {
        val models = listOf(
            model("capable"),
            model("not-capable", supportsToolCalling = false),
        )
        // "not-capable" is listed as preferred but fails the tool-calling filter — it must not appear
        val result = orderModelsForPicker(models, preferredModelIds = listOf("not-capable", "capable"))
        assertEquals(listOf(model("capable")), result)
    }
}
