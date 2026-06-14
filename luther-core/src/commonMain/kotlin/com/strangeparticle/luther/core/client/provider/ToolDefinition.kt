package com.strangeparticle.luther.core.client.provider

import kotlinx.serialization.json.JsonObject

data class ToolDefinition(val name: String, val description: String, val schema: JsonObject)
