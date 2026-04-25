package com.sermilion.personalgraph.mcp.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
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

  fun string(): JsonObject = buildJsonObject {
    put(KEY_TYPE, JsonPrimitive(TYPE_STRING))
  }

  fun boolean(): JsonObject = buildJsonObject {
    put(KEY_TYPE, JsonPrimitive(TYPE_BOOLEAN))
  }

  fun stringArray(): JsonObject = buildJsonObject {
    put(KEY_TYPE, JsonPrimitive(TYPE_ARRAY))
    put(
      KEY_ITEMS,
      buildJsonObject { put(KEY_TYPE, JsonPrimitive(TYPE_STRING)) },
    )
  }

  fun enum(values: List<String>): JsonObject = buildJsonObject {
    put(KEY_TYPE, JsonPrimitive(TYPE_STRING))
    put(KEY_ENUM, enumArray(values))
  }

  private fun enumArray(values: List<String>): JsonArray = buildJsonArray {
    for (value in values) add(JsonPrimitive(value))
  }
}
