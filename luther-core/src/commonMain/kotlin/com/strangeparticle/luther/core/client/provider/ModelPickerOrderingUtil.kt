package com.strangeparticle.luther.core.client.provider

internal fun orderModelsForPicker(models: List<Model>, preferredModelIds: List<String>): List<Model> {
    val toolCapableModels = models.filter { model -> model.supportsToolCalling }
    val toolCapableById = toolCapableModels.associateBy { model -> model.id }

    val preferredModels = preferredModelIds.mapNotNull { preferredId -> toolCapableById[preferredId] }
    val preferredIds = preferredModels.map { model -> model.id }.toSet()
    val remainingModels = toolCapableModels.filter { model -> model.id !in preferredIds }

    return preferredModels + remainingModels
}
