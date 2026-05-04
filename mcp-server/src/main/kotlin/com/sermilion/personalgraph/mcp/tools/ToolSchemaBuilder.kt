package com.sermilion.personalgraph.mcp.tools

import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
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
        buildJsonArray { for (value in values) add(JsonPrimitive(value)) },
      )
    },
  )
  if (description != null) put("description", JsonPrimitive(description))
}

internal object ToolSchemaBuilder {

  fun writeStateSchema(): ToolSchema = ToolSchema(
    properties = buildJsonObject {
      put(ToolSchemas.KEY_ID, ToolSchemaProperties.string(ToolSchemas.DESC_FIELD_STATE_ID))
      put(ToolSchemas.KEY_CATEGORY, ToolSchemaProperties.enum(ToolSchemas.ENUM_STATE_CATEGORIES))
      put(ToolSchemas.KEY_CONFIDENCE, ToolSchemaProperties.enum(ToolSchemas.ENUM_CONFIDENCES))
      put(ToolSchemas.KEY_BODY, ToolSchemaProperties.string())
      put(ToolSchemas.KEY_LINKS, ToolSchemaProperties.stringArray(ToolSchemas.DESC_FIELD_LINKS))
      put(ToolSchemas.KEY_SCOPE, ToolSchemaProperties.string(ToolSchemas.DESC_FIELD_SCOPE))
      put(ToolSchemas.KEY_SCOPES, ToolSchemaProperties.stringArray(ToolSchemas.DESC_FIELD_SCOPES))
      put(ToolSchemas.KEY_SENSITIVE, ToolSchemaProperties.boolean())
    },
    required = listOf(
      ToolSchemas.KEY_ID,
      ToolSchemas.KEY_CATEGORY,
      ToolSchemas.KEY_CONFIDENCE,
    ),
  )

  fun captureObservationSchema(): ToolSchema = ToolSchema(
    properties = buildJsonObject {
      put(
        ToolSchemas.KEY_OBSERVATION,
        ToolSchemaProperties.string(
          "Candidate memory text. Personal-graph owns the save, stage, update, or reject decision.",
        ),
      )
      put(ToolSchemas.KEY_SOURCE_CONTEXT, ToolSchemaProperties.string("Short source context for provenance."))
      put(
        ToolSchemas.KEY_SUGGESTED_KIND,
        ToolSchemaProperties.enum(
          ToolSchemas.ENUM_CAPTURE_OBSERVATION_KINDS,
          "Optional caller hint only; personal-graph may ignore it.",
        ),
      )
      put(ToolSchemas.KEY_ID, ToolSchemaProperties.string("Optional id hint. Personal-graph can generate one."))
      put(ToolSchemas.KEY_CATEGORY, ToolSchemaProperties.enum(ToolSchemas.ENUM_STATE_CATEGORIES))
      put(ToolSchemas.KEY_CONFIDENCE, ToolSchemaProperties.enum(ToolSchemas.ENUM_CONFIDENCES))
      put(ToolSchemas.KEY_DATE, ToolSchemaProperties.string(ToolSchemas.DESC_FIELD_DATE))
      put(ToolSchemas.KEY_EPISODE_TYPE, ToolSchemaProperties.enum(ToolSchemas.ENUM_EPISODE_TYPES))
      put(ToolSchemas.KEY_DOMAIN, ToolSchemaProperties.string())
      put(ToolSchemas.KEY_TOPIC, ToolSchemaProperties.string())
      put(ToolSchemas.KEY_INTENSITY, ToolSchemaProperties.enum(ToolSchemas.ENUM_INTENSITIES))
      put(ToolSchemas.KEY_LINKS, ToolSchemaProperties.stringArray(ToolSchemas.DESC_FIELD_LINKS))
      put(ToolSchemas.KEY_SCOPE, ToolSchemaProperties.string(ToolSchemas.DESC_FIELD_SCOPE))
      put(ToolSchemas.KEY_SCOPES, ToolSchemaProperties.stringArray(ToolSchemas.DESC_FIELD_SCOPES))
      put(ToolSchemas.KEY_SENSITIVE, ToolSchemaProperties.boolean())
    },
    required = listOf(ToolSchemas.KEY_OBSERVATION),
  )

  fun writeEpisodeSchema(): ToolSchema = ToolSchema(
    properties = buildJsonObject {
      put(ToolSchemas.KEY_ID, ToolSchemaProperties.string(ToolSchemas.DESC_FIELD_NODE_ID))
      put(ToolSchemas.KEY_DATE, ToolSchemaProperties.string(ToolSchemas.DESC_FIELD_DATE))
      put(ToolSchemas.KEY_EPISODE_TYPE, ToolSchemaProperties.enum(ToolSchemas.ENUM_EPISODE_TYPES))
      put(ToolSchemas.KEY_DOMAIN, ToolSchemaProperties.string())
      put(ToolSchemas.KEY_TOPIC, ToolSchemaProperties.string())
      put(ToolSchemas.KEY_INTENSITY, ToolSchemaProperties.enum(ToolSchemas.ENUM_INTENSITIES))
      put(ToolSchemas.KEY_BODY, ToolSchemaProperties.string())
      put(ToolSchemas.KEY_LINKED, ToolSchemaProperties.stringArray(ToolSchemas.DESC_FIELD_LINKS))
      put(ToolSchemas.KEY_SENSITIVE, ToolSchemaProperties.boolean())
    },
    required = listOf(
      ToolSchemas.KEY_ID,
      ToolSchemas.KEY_DATE,
      ToolSchemas.KEY_EPISODE_TYPE,
      ToolSchemas.KEY_DOMAIN,
      ToolSchemas.KEY_TOPIC,
      ToolSchemas.KEY_INTENSITY,
    ),
  )

  fun writeToStagingSchema(): ToolSchema = ToolSchema(
    properties = buildJsonObject {
      put(ToolSchemas.KEY_ID, ToolSchemaProperties.string(ToolSchemas.DESC_FIELD_STATE_ID))
      put(ToolSchemas.KEY_CATEGORY, ToolSchemaProperties.enum(ToolSchemas.ENUM_STATE_CATEGORIES))
      put(ToolSchemas.KEY_CONFIDENCE, ToolSchemaProperties.enum(ToolSchemas.ENUM_CONFIDENCES))
      put(ToolSchemas.KEY_BODY, ToolSchemaProperties.string())
      put(ToolSchemas.KEY_LINKS, ToolSchemaProperties.stringArray(ToolSchemas.DESC_FIELD_LINKS))
      put(ToolSchemas.KEY_SCOPE, ToolSchemaProperties.string(ToolSchemas.DESC_FIELD_SCOPE))
      put(ToolSchemas.KEY_SCOPES, ToolSchemaProperties.stringArray(ToolSchemas.DESC_FIELD_SCOPES))
    },
    required = listOf(
      ToolSchemas.KEY_ID,
      ToolSchemas.KEY_CATEGORY,
      ToolSchemas.KEY_CONFIDENCE,
    ),
  )

  fun flagSensitiveSchema(): ToolSchema = ToolSchema(
    properties = buildJsonObject {
      put(ToolSchemas.KEY_TARGET_PATH, ToolSchemaProperties.string(ToolSchemas.DESC_FIELD_TARGET_PATH))
      put(
        ToolSchemas.KEY_PAYLOAD_KIND,
        ToolSchemaProperties.enum(ToolSchemas.ENUM_PAYLOAD_KINDS, ToolSchemas.DESC_FIELD_PAYLOAD_KIND),
      )
    },
    required = listOf(ToolSchemas.KEY_TARGET_PATH),
  )

  fun listPendingSensitiveSchema(): ToolSchema = ToolSchema(
    properties = buildJsonObject {
      put(ToolSchemas.KEY_INCLUDE_EXCERPTS, ToolSchemaProperties.boolean())
    },
    required = emptyList(),
  )

  fun readNodeSchema(): ToolSchema = ToolSchema(
    properties = buildJsonObject {
      put(ToolSchemas.KEY_ID, ToolSchemaProperties.string(ToolSchemas.DESC_FIELD_NODE_ID))
    },
    required = listOf(ToolSchemas.KEY_ID),
  )

  fun listBranchSchema(): ToolSchema = ToolSchema(
    properties = buildJsonObject {
      put(ToolSchemas.KEY_BRANCH, ToolSchemaProperties.string(ToolSchemas.DESC_FIELD_BRANCH))
      put(
        ToolSchemas.KEY_MODE,
        ToolSchemaProperties.enum(ToolSchemas.ENUM_LIST_MODES, ToolSchemas.DESC_FIELD_LIST_MODE),
      )
      put(ToolSchemas.KEY_FILTER, ToolSchemaProperties.string(ToolSchemas.DESC_FIELD_LIST_FILTER))
      put(ToolSchemas.KEY_LIMIT, integerSchema(ToolSchemas.DESC_FIELD_LIST_LIMIT))
      put(ToolSchemas.KEY_INCLUDE_LINKS, ToolSchemaProperties.boolean(ToolSchemas.DESC_FIELD_INCLUDE_LINKS))
      put(ToolSchemas.KEY_INCLUDE_BODY, ToolSchemaProperties.boolean(ToolSchemas.DESC_FIELD_INCLUDE_BODY))
    },
    required = listOf(ToolSchemas.KEY_BRANCH),
  )

  fun searchNodesSchema(): ToolSchema = ToolSchema(
    properties = buildJsonObject {
      put(ToolSchemas.KEY_QUERY, ToolSchemaProperties.string(ToolSchemas.DESC_FIELD_SEARCH_QUERY))
      put(ToolSchemas.KEY_BRANCHES, ToolSchemaProperties.stringArray(ToolSchemas.DESC_FIELD_SEARCH_BRANCHES))
      put(ToolSchemas.KEY_LIMIT, integerSchema(ToolSchemas.DESC_FIELD_SEARCH_LIMIT))
      put(
        ToolSchemas.KEY_SEARCH_FIELDS,
        stringEnumArraySchema(ToolSchemas.ENUM_SEARCH_FIELDS, ToolSchemas.DESC_FIELD_SEARCH_FIELDS),
      )
      put(ToolSchemas.KEY_BODY_FALLBACK, ToolSchemaProperties.boolean(ToolSchemas.DESC_FIELD_BODY_FALLBACK))
      put(ToolSchemas.KEY_INCLUDE_BODY, ToolSchemaProperties.boolean(ToolSchemas.DESC_FIELD_SEARCH_INCLUDE_BODY))
    },
    required = listOf(ToolSchemas.KEY_QUERY),
  )

  fun sessionStartSchema(): ToolSchema = ToolSchema(
    properties = buildJsonObject {
      put(ToolSchemas.KEY_MESSAGE, ToolSchemaProperties.string())
      put(
        ToolSchemas.KEY_RETRIEVAL_MODE,
        ToolSchemaProperties.enum(ToolSchemas.ENUM_RETRIEVAL_MODES, ToolSchemas.DESC_FIELD_RETRIEVAL_MODE),
      )
    },
    required = listOf(ToolSchemas.KEY_MESSAGE),
  )
}
