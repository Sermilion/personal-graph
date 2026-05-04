package com.sermilion.personalgraph.mcp.tools

import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

private fun integerSchema(description: String? = null): JsonObject = buildJsonObject {
  put("type", JsonPrimitive("integer"))
  if (description != null) put("description", JsonPrimitive(description))
}

private fun stringEnumArraySchema(values: List<String>, description: String? = null): JsonObject = buildJsonObject {
  put("type", JsonPrimitive("array"))
  put(
    "items",
    buildJsonObject {
      put("type", JsonPrimitive("string"))
      put(
        "enum",
        JsonArray(values.map { JsonPrimitive(it) }),
      )
    },
  )
  if (description != null) put("description", JsonPrimitive(description))
}

internal fun traverseGraphSchema(): ToolSchema = ToolSchema(
  properties = buildJsonObject {
    put(ToolSchemas.KEY_QUERY, ToolSchemaProperties.string(ToolSchemas.DESC_FIELD_TRAVERSE_QUERY))
    put(ToolSchemas.KEY_START_IDS, ToolSchemaProperties.stringArray(ToolSchemas.DESC_FIELD_TRAVERSE_START_IDS))
    put(ToolSchemas.KEY_BRANCHES, ToolSchemaProperties.stringArray(ToolSchemas.DESC_FIELD_TRAVERSE_BRANCHES))
    put(
      ToolSchemas.KEY_EDGE_TYPES,
      stringEnumArraySchema(ToolSchemas.ENUM_TRAVERSAL_EDGE_TYPES, ToolSchemas.DESC_FIELD_TRAVERSE_EDGE_TYPES),
    )
    put(ToolSchemas.KEY_MAX_DEPTH, integerSchema(ToolSchemas.DESC_FIELD_TRAVERSE_MAX_DEPTH))
    put(ToolSchemas.KEY_MAX_NODES, integerSchema(ToolSchemas.DESC_FIELD_TRAVERSE_MAX_NODES))
    put(ToolSchemas.KEY_BUDGET_TOKENS, integerSchema(ToolSchemas.DESC_FIELD_TRAVERSE_BUDGET_TOKENS))
    put(ToolSchemas.KEY_INCLUDE_BODIES, ToolSchemaProperties.boolean(ToolSchemas.DESC_FIELD_TRAVERSE_INCLUDE_BODIES))
    put(
      ToolSchemas.KEY_RANK_BY,
      stringEnumArraySchema(ToolSchemas.ENUM_TRAVERSAL_RANK_BY, ToolSchemas.DESC_FIELD_TRAVERSE_RANK_BY),
    )
  },
  required = listOf(ToolSchemas.KEY_QUERY),
)
