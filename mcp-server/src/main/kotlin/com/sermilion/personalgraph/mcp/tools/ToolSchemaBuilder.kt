package com.sermilion.personalgraph.mcp.tools

import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject

internal object ToolSchemaBuilder {

  fun writeStateSchema(): ToolSchema = ToolSchema(
    properties = buildJsonObject {
      put(ToolSchemas.KEY_ID, ToolSchemaProperties.string(ToolSchemas.DESC_FIELD_STATE_ID))
      put(ToolSchemas.KEY_CATEGORY, ToolSchemaProperties.enum(ToolSchemas.ENUM_STATE_CATEGORIES))
      put(ToolSchemas.KEY_CONFIDENCE, ToolSchemaProperties.enum(ToolSchemas.ENUM_CONFIDENCES))
      put(ToolSchemas.KEY_BODY, ToolSchemaProperties.string())
      put(ToolSchemas.KEY_LINKS, ToolSchemaProperties.stringArray(ToolSchemas.DESC_FIELD_LINKS))
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
    },
    required = listOf(ToolSchemas.KEY_BRANCH),
  )

  fun sessionStartSchema(): ToolSchema = ToolSchema(
    properties = buildJsonObject {
      put(ToolSchemas.KEY_MESSAGE, ToolSchemaProperties.string())
    },
    required = listOf(ToolSchemas.KEY_MESSAGE),
  )
}
