package com.strangeparticle.luther.core.toolcall

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialInfo
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@OptIn(ExperimentalSerializationApi::class)
@SerialInfo
@Target(AnnotationTarget.PROPERTY)
internal annotation class ToolFieldDescription(val text: String)

/** Convenience: extract the `enum` array from a property schema (used by tests). */
internal fun JsonObject.enumValues(): JsonArray? = this["enum"] as? JsonArray

/**
 * Derive the JSON Schema for a ToolCall request from its `@Serializable` metadata
 * and any [ToolFieldDescription] annotations on its fields.
 */
internal fun <T> requestSchema(serializer: KSerializer<T>): JsonObject =
    schemaForDescriptor(serializer.descriptor, description = null)

private fun schemaForDescriptor(
    descriptor: SerialDescriptor,
    description: String?,
): JsonObject = when (descriptor.kind) {
    PrimitiveKind.STRING -> stringSchema(description)
    PrimitiveKind.BOOLEAN -> booleanSchema(description)
    PrimitiveKind.BYTE -> integerSchema(description)
    PrimitiveKind.SHORT -> integerSchema(description)
    PrimitiveKind.INT -> integerSchema(description)
    PrimitiveKind.LONG -> integerSchema(description)
    PrimitiveKind.FLOAT -> numberSchema(description)
    PrimitiveKind.DOUBLE -> numberSchema(description)
    SerialKind.ENUM -> enumSchema(
        values = List(descriptor.elementsCount) { descriptor.getElementName(it) },
        description = description,
    )
    StructureKind.LIST -> arraySchema(
        itemSchema = schemaForDescriptor(descriptor.getElementDescriptor(0), description = null),
        description = description,
    )
    StructureKind.CLASS,
    StructureKind.OBJECT -> objectSchemaForDescriptor(descriptor, description)
    else -> error("Unsupported request schema kind: ${descriptor.kind}")
}

private fun stringSchema(description: String?): JsonObject = buildJsonObject {
    put("type", "string")
    if (description != null) put("description", description)
}

private fun booleanSchema(description: String?): JsonObject = buildJsonObject {
    put("type", "boolean")
    if (description != null) put("description", description)
}

private fun numberSchema(description: String?): JsonObject = buildJsonObject {
    put("type", "number")
    if (description != null) put("description", description)
}

private fun integerSchema(description: String?): JsonObject = buildJsonObject {
    put("type", "integer")
    if (description != null) put("description", description)
}

private fun arraySchema(itemSchema: JsonObject, description: String?): JsonObject = buildJsonObject {
    put("type", "array")
    if (description != null) put("description", description)
    put("items", itemSchema)
}

private fun enumSchema(values: List<String>, description: String?): JsonObject = buildJsonObject {
    put("type", "string")
    if (description != null) put("description", description)
    put("enum", buildJsonArray { for (v in values) add(JsonPrimitive(v)) })
}

private fun objectSchemaForDescriptor(
    descriptor: SerialDescriptor,
    description: String?,
): JsonObject = buildJsonObject {
    put("type", "object")
    if (description != null) put("description", description)
    put("properties", buildJsonObject {
        for (index in 0 until descriptor.elementsCount) {
            val elementName = descriptor.getElementName(index)
            val elementDescriptor = descriptor.getElementDescriptor(index)
            val elementDescription = descriptor.getElementAnnotations(index)
                .filterIsInstance<ToolFieldDescription>()
                .firstOrNull()
                ?.text
            put(elementName, schemaForDescriptor(elementDescriptor, elementDescription))
        }
    })
    val requiredNames = buildList {
        for (index in 0 until descriptor.elementsCount) {
            if (!descriptor.isElementOptional(index)) {
                add(descriptor.getElementName(index))
            }
        }
    }
    if (requiredNames.isNotEmpty()) {
        put("required", buildJsonArray {
            for (name in requiredNames) add(JsonPrimitive(name))
        })
    }
}
