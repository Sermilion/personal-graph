package com.sermilion.personalgraph.mcp.tools

import com.sermilion.personalgraph.domain.retrieval.CompactMapEntry
import com.sermilion.personalgraph.domain.retrieval.LoadedFullBodyContext
import com.sermilion.personalgraph.domain.retrieval.RetrievalAuditEntry
import com.sermilion.personalgraph.domain.retrieval.RetrievalClassification
import com.sermilion.personalgraph.domain.retrieval.SkippedBranch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

internal fun classificationJsonOf(classification: RetrievalClassification): JsonObject = buildJsonObject {
  put(ToolSchemas.KEY_DOMAIN, JsonPrimitive(classification.domain.value))
  put(ToolSchemas.KEY_MATCHED_TERMS, stringArrayJsonOf(classification.matchedTerms))
  put(ToolSchemas.KEY_EMOTIONAL_CONTEXT, JsonPrimitive(classification.emotionalContextRequested))
  put(ToolSchemas.KEY_EMOTIONAL_TERMS, stringArrayJsonOf(classification.emotionalMatchedTerms))
}

internal fun loadedFullBodyContextJsonOf(context: List<LoadedFullBodyContext>): JsonArray = buildJsonArray {
  for (entry in context) {
    add(
      buildJsonObject {
        put(ToolSchemas.KEY_ID, JsonPrimitive(entry.id))
        put(ToolSchemas.KEY_BODY, JsonPrimitive(entry.body))
        put(ToolSchemas.KEY_SOURCE, JsonPrimitive(entry.source.value))
        put(ToolSchemas.KEY_LOAD_ORDER, JsonPrimitive(entry.loadOrder))
        put(ToolSchemas.KEY_REASON, JsonPrimitive(entry.reason))
      },
    )
  }
}

internal fun compactMapEntriesJsonOf(entries: List<CompactMapEntry>): JsonArray = buildJsonArray {
  for (entry in entries) add(compactMapEntryJsonOf(entry))
}

private fun compactMapEntryJsonOf(entry: CompactMapEntry): JsonObject = buildJsonObject {
  put(ToolSchemas.KEY_ID, JsonPrimitive(entry.id))
  put(ToolSchemas.KEY_KIND, JsonPrimitive(entry.kind.value))
  put(ToolSchemas.KEY_REASON, JsonPrimitive(entry.reason))
  entry.nodeCount?.let { put(ToolSchemas.KEY_NODE_COUNT, JsonPrimitive(it)) }
  entry.type?.let { put(ToolSchemas.KEY_TYPE, JsonPrimitive(it)) }
  entry.category?.let { put(ToolSchemas.KEY_CATEGORY, JsonPrimitive(it)) }
  entry.domain?.let { put(ToolSchemas.KEY_DOMAIN, JsonPrimitive(it)) }
  entry.scope?.let { put(ToolSchemas.KEY_SCOPE, JsonPrimitive(it)) }
  if (entry.scopes.isNotEmpty()) put(ToolSchemas.KEY_SCOPES, stringArrayJsonOf(entry.scopes))
  entry.updated?.let { put(ToolSchemas.KEY_UPDATED, JsonPrimitive(it)) }
  entry.date?.let { put(ToolSchemas.KEY_DATE, JsonPrimitive(it)) }
  entry.summary?.let { put(ToolSchemas.KEY_SUMMARY, JsonPrimitive(it)) }
  if (entry.aliases.isNotEmpty()) put(ToolSchemas.KEY_ALIASES, stringArrayJsonOf(entry.aliases))
  entry.linkCount?.let { put(ToolSchemas.KEY_LINK_COUNT, JsonPrimitive(it)) }
  if (entry.links.isNotEmpty()) put(ToolSchemas.KEY_LINKS, stringArrayJsonOf(entry.links))
}

internal fun skippedBranchesJsonOf(branches: List<SkippedBranch>): JsonArray = buildJsonArray {
  for (branch in branches) {
    add(
      buildJsonObject {
        put(ToolSchemas.KEY_BRANCH, JsonPrimitive(branch.branch))
        put(ToolSchemas.KEY_REASON, JsonPrimitive(branch.reason))
      },
    )
  }
}

internal fun auditJsonOf(audit: List<RetrievalAuditEntry>): JsonArray = buildJsonArray {
  for (entry in audit) {
    add(
      buildJsonObject {
        put(ToolSchemas.KEY_ACTION, JsonPrimitive(entry.action))
        put(ToolSchemas.KEY_SUBJECT, JsonPrimitive(entry.subject))
        put(ToolSchemas.KEY_REASON, JsonPrimitive(entry.reason))
      },
    )
  }
}

internal fun stringArrayJsonOf(values: List<String>): JsonArray = buildJsonArray {
  for (value in values) add(JsonPrimitive(value))
}
