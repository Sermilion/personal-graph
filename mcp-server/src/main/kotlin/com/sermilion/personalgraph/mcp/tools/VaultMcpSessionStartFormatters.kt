package com.sermilion.personalgraph.mcp.tools

import com.sermilion.personalgraph.domain.retrieval.SessionStartTokenAccounting
import com.sermilion.personalgraph.domain.retrieval.SuggestedAction
import com.sermilion.personalgraph.domain.retrieval.SuggestedActionValue
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

internal fun suggestedActionJson(action: SuggestedAction): JsonObject = buildJsonObject {
  put(ToolSchemas.KEY_TOOL, JsonPrimitive(action.tool))
  put(ToolSchemas.KEY_REASON, JsonPrimitive(action.reason))
  put(ToolSchemas.KEY_PRIORITY, JsonPrimitive(action.priority.value))
  put(
    ToolSchemas.KEY_ARGS,
    buildJsonObject {
      for (arg in action.args) {
        put(arg.key, suggestedActionValueJson(arg.value))
      }
    },
  )
}

internal fun tokenAccountingJson(accounting: SessionStartTokenAccounting): JsonObject = buildJsonObject {
  put(ToolSchemas.KEY_RESPONSE_TOTAL, JsonPrimitive(accounting.responseTotal))
  put(ToolSchemas.KEY_METADATA_TOKENS, JsonPrimitive(accounting.metadataTokens))
  put(ToolSchemas.KEY_BODY_TOKENS, JsonPrimitive(accounting.bodyTokens))
  put(ToolSchemas.KEY_PRUNED_BODY_TOKENS, JsonPrimitive(accounting.prunedBodyTokens))
}

private fun suggestedActionValueJson(value: SuggestedActionValue): JsonElement = when (value) {
  is SuggestedActionValue.StringValue -> JsonPrimitive(value.value)
  is SuggestedActionValue.BooleanValue -> JsonPrimitive(value.value)
  is SuggestedActionValue.IntValue -> JsonPrimitive(value.value)
  is SuggestedActionValue.StringListValue -> stringArrayJsonOf(value.value)
}
