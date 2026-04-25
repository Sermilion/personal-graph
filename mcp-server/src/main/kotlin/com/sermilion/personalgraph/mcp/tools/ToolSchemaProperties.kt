package com.sermilion.personalgraph.mcp.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

internal object ToolSchemaProperties {

  private const val TYPE_STRING: String = "string"
  private const val TYPE_BOOLEAN: String = "boolean"
  private const val TYPE_ARRAY: String = "array"
  private const val KEY_TYPE: String = "type"
  private const val KEY_ITEMS: String = "items"
  private const val KEY_ENUM: String = "enum"
  private const val KEY_DESCRIPTION: String = "description"

  fun string(description: String? = null): JsonObject = buildJsonObject {
    put(KEY_TYPE, JsonPrimitive(TYPE_STRING))
    putDescription(description)
  }

  fun boolean(description: String? = null): JsonObject = buildJsonObject {
    put(KEY_TYPE, JsonPrimitive(TYPE_BOOLEAN))
    putDescription(description)
  }

  fun stringArray(description: String? = null): JsonObject = buildJsonObject {
    put(KEY_TYPE, JsonPrimitive(TYPE_ARRAY))
    put(
      KEY_ITEMS,
      buildJsonObject { put(KEY_TYPE, JsonPrimitive(TYPE_STRING)) },
    )
    putDescription(description)
  }

  fun enum(values: List<String>, description: String? = null): JsonObject = buildJsonObject {
    put(KEY_TYPE, JsonPrimitive(TYPE_STRING))
    put(KEY_ENUM, enumArray(values))
    putDescription(description)
  }

  private fun JsonObjectBuilder.putDescription(description: String?) {
    if (description != null) {
      put(KEY_DESCRIPTION, JsonPrimitive(description))
    }
  }

  private fun enumArray(values: List<String>): JsonArray = buildJsonArray {
    for (value in values) add(JsonPrimitive(value))
  }
}
