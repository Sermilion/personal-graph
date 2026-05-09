package com.sermilion.personalgraph.mcp.tools

import com.sermilion.personalgraph.domain.search.TraversalEdge
import com.sermilion.personalgraph.domain.search.TraversalEdgeType
import com.sermilion.personalgraph.domain.search.TraversalEntrypoint
import com.sermilion.personalgraph.domain.search.TraversalNode
import com.sermilion.personalgraph.domain.search.TraversalPrunedCandidate
import com.sermilion.personalgraph.domain.search.TraversalPrunedReason
import com.sermilion.personalgraph.domain.search.TraversalSuggestedRead
import com.sermilion.personalgraph.domain.search.TraversalTokenAccounting
import com.sermilion.personalgraph.domain.search.TraverseGraphOutcome
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

internal fun TraverseGraphOutcome.toJson(): JsonObject {
  val entrypointsJson = buildJsonArray {
    for (entrypoint in entrypoints) add(traversalEntrypointJson(entrypoint))
  }
  val nodesJson = buildJsonArray {
    for (node in nodes) add(traversalNodeJson(node))
  }
  val edgesJson = buildJsonArray {
    for (edge in edges) add(traversalEdgeJson(edge))
  }
  val prunedJson = buildJsonArray {
    for (candidate in pruned) add(traversalPrunedJson(candidate))
  }
  val suggestedReadsJson = buildJsonArray {
    for (read in suggestedReads) add(traversalSuggestedReadJson(read))
  }

  return buildJsonObject {
    put(ToolSchemas.KEY_STATUS, JsonPrimitive(ToolSchemas.STATUS_OK))
    put(ToolSchemas.KEY_ENTRYPOINTS, entrypointsJson)
    put(ToolSchemas.KEY_NODES, nodesJson)
    put(ToolSchemas.KEY_EDGES, edgesJson)
    put(ToolSchemas.KEY_PRUNED, prunedJson)
    put(ToolSchemas.KEY_SUGGESTED_READS, suggestedReadsJson)
    put(ToolSchemas.KEY_ESTIMATED_TOKENS, traversalTokenAccountingJson(tokenAccounting))
  }
}

private fun traversalEntrypointJson(entrypoint: TraversalEntrypoint): JsonObject = buildJsonObject {
  put(ToolSchemas.KEY_ID, JsonPrimitive(entrypoint.id.value))
  put(ToolSchemas.KEY_REASON, JsonPrimitive(entrypoint.reason))
  put(ToolSchemas.KEY_SCORE, JsonPrimitive(entrypoint.score))
}

private fun traversalNodeJson(node: TraversalNode): JsonObject {
  val reason = nodeReason(node)
  return buildJsonObject {
    put(ToolSchemas.KEY_ID, JsonPrimitive(node.id.value))
    put(ToolSchemas.KEY_TYPE, JsonPrimitive(node.type))
    put(ToolSchemas.KEY_DISTANCE, JsonPrimitive(node.depth))
    put(ToolSchemas.KEY_SCORE, JsonPrimitive(node.score))
    put(ToolSchemas.KEY_REASON, JsonPrimitive(reason))
    put(ToolSchemas.KEY_SNIPPET, JsonPrimitive(node.snippet))
    if (node.matchFields.isNotEmpty()) {
      put(ToolSchemas.KEY_MATCH_FIELDS, stringArrayJsonOf(node.matchFields))
    }
    node.body?.let { put(ToolSchemas.KEY_BODY, JsonPrimitive(it)) }
  }
}

private fun traversalEdgeJson(edge: TraversalEdge): JsonObject {
  val reason = traversalEdgeReason(edge.type)
  return buildJsonObject {
    put(ToolSchemas.KEY_FROM, JsonPrimitive(edge.from.value))
    put(ToolSchemas.KEY_TO, JsonPrimitive(edge.to.value))
    put(ToolSchemas.KEY_TYPE, JsonPrimitive(edge.type.labelValue()))
    put(ToolSchemas.KEY_LABEL, JsonPrimitive(edge.type.labelValue()))
    put(ToolSchemas.KEY_WEIGHT, JsonPrimitive(edge.weight))
    put(ToolSchemas.KEY_REASON, JsonPrimitive(reason))
  }
}

private fun traversalPrunedJson(candidate: TraversalPrunedCandidate): JsonObject {
  val reason = traversalPrunedReasonWire(candidate.reason)
  return buildJsonObject {
    put(ToolSchemas.KEY_ID, JsonPrimitive(candidate.id.value))
    put(ToolSchemas.KEY_REASON, JsonPrimitive(reason))
    put(ToolSchemas.KEY_SCORE, JsonPrimitive(candidate.score))
    put(ToolSchemas.KEY_ESTIMATED_TOKENS, JsonPrimitive(candidate.estimatedTokens))
  }
}

private fun traversalSuggestedReadJson(read: TraversalSuggestedRead): JsonObject {
  val priority = traversalPriority(read.priority)
  val reason = traversalSuggestedReadReason(read.reason)
  return buildJsonObject {
    put(ToolSchemas.KEY_ID, JsonPrimitive(read.id.value))
    put(ToolSchemas.KEY_REASON, JsonPrimitive(reason))
    put(ToolSchemas.KEY_PRIORITY, JsonPrimitive(priority))
  }
}

private fun traversalTokenAccountingJson(accounting: TraversalTokenAccounting): JsonObject = buildJsonObject {
  put(ToolSchemas.KEY_RESPONSE_TOTAL, JsonPrimitive(accounting.responseTotal))
  put(ToolSchemas.KEY_METADATA_TOKENS, JsonPrimitive(accounting.metadataTokens))
  put(ToolSchemas.KEY_BODY_TOKENS, JsonPrimitive(accounting.bodyTokens))
  put(ToolSchemas.KEY_PRUNED_BODY_TOKENS, JsonPrimitive(accounting.prunedBodyTokens))
}

private fun traversalEdgeReason(type: TraversalEdgeType): String = when (type) {
  TraversalEdgeType.Link -> "generic link"
  TraversalEdgeType.Backlink -> "reverse link"
  TraversalEdgeType.SubjectEvidence -> "subject evidence link"
  TraversalEdgeType.Timeline -> "timeline link"
  TraversalEdgeType.State -> "state link"
  TraversalEdgeType.Pattern -> "pattern link"
  TraversalEdgeType.Contradiction -> "contradiction link"
  TraversalEdgeType.Background -> "background link"
}

private fun traversalPrunedReasonWire(reason: TraversalPrunedReason): String = when (reason) {
  TraversalPrunedReason.MaxNodes -> "max_nodes"
  TraversalPrunedReason.BudgetTokens -> "budget_tokens"
}

private fun traversalSuggestedReadReason(reason: String): String = when (reason.lowercase()) {
  "maxnodes" -> "pruned by max_nodes"
  "budgettokens" -> "pruned by budget_tokens"
  else -> reason.lowercase()
}

private fun traversalPriority(score: Int): String = when {
  score >= HIGH_PRIORITY_SCORE -> "high"
  score >= MEDIUM_PRIORITY_SCORE -> "medium"
  else -> "low"
}

private fun TraversalEdgeType.labelValue(): String = when (this) {
  TraversalEdgeType.Link -> ToolSchemas.TRAVERSAL_EDGE_TYPE_LINK
  TraversalEdgeType.Backlink -> ToolSchemas.TRAVERSAL_EDGE_TYPE_BACKLINK
  TraversalEdgeType.SubjectEvidence -> ToolSchemas.TRAVERSAL_EDGE_TYPE_SUBJECT_EVIDENCE
  TraversalEdgeType.Timeline -> ToolSchemas.TRAVERSAL_EDGE_TYPE_TIMELINE
  TraversalEdgeType.State -> ToolSchemas.TRAVERSAL_EDGE_TYPE_STATE
  TraversalEdgeType.Pattern -> ToolSchemas.TRAVERSAL_EDGE_TYPE_PATTERN
  TraversalEdgeType.Contradiction -> ToolSchemas.TRAVERSAL_EDGE_TYPE_CONTRADICTION
  TraversalEdgeType.Background -> ToolSchemas.TRAVERSAL_EDGE_TYPE_BACKGROUND
}

private fun nodeReason(node: TraversalNode): String = node.matchFields.firstOrNull().orEmpty()

private const val HIGH_PRIORITY_SCORE: Int = 80
private const val MEDIUM_PRIORITY_SCORE: Int = 40
