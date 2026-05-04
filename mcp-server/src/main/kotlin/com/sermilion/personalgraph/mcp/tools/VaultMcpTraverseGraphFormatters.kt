package com.sermilion.personalgraph.mcp.tools

import com.sermilion.personalgraph.domain.search.TraversalEdge
import com.sermilion.personalgraph.domain.search.TraversalEdgeType
import com.sermilion.personalgraph.domain.search.TraversalEntrypoint
import com.sermilion.personalgraph.domain.search.TraversalNode
import com.sermilion.personalgraph.domain.search.TraversalPrunedCandidate
import com.sermilion.personalgraph.domain.search.TraversalPrunedReason
import com.sermilion.personalgraph.domain.search.TraversalSuggestedRead
import com.sermilion.personalgraph.domain.search.TraverseGraphOutcome
import com.sermilion.personalgraph.domain.tokens.TokenEstimator
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

internal fun TraverseGraphOutcome.toJson(): JsonObject {
  var metadataTokens = 0
  var bodyTokens = 0
  var prunedBodyTokens = 0

  val entrypointsJson = buildJsonArray {
    for (entrypoint in entrypoints) {
      val formatted = traversalEntrypointJson(entrypoint)
      metadataTokens += formatted.tokens
      add(formatted.json)
    }
  }
  val nodesJson = buildJsonArray {
    for (node in nodes) {
      val formatted = traversalNodeJson(node)
      metadataTokens += formatted.tokens
      bodyTokens += formatted.bodyTokens
      add(formatted.json)
    }
  }
  val edgesJson = buildJsonArray {
    for (edge in edges) {
      val formatted = traversalEdgeJson(edge)
      metadataTokens += formatted.tokens
      add(formatted.json)
    }
  }
  val prunedJson = buildJsonArray {
    for (candidate in pruned) {
      val formatted = traversalPrunedJson(candidate)
      metadataTokens += formatted.tokens
      prunedBodyTokens += formatted.prunedBodyTokens
      add(formatted.json)
    }
  }
  val suggestedReadsJson = buildJsonArray {
    for (read in suggestedReads) {
      val formatted = traversalSuggestedReadJson(read)
      metadataTokens += formatted.tokens
      add(formatted.json)
    }
  }

  return buildJsonObject {
    put(ToolSchemas.KEY_STATUS, JsonPrimitive(ToolSchemas.STATUS_OK))
    put(ToolSchemas.KEY_ENTRYPOINTS, entrypointsJson)
    put(ToolSchemas.KEY_NODES, nodesJson)
    put(ToolSchemas.KEY_EDGES, edgesJson)
    put(ToolSchemas.KEY_PRUNED, prunedJson)
    put(ToolSchemas.KEY_SUGGESTED_READS, suggestedReadsJson)
    put(
      ToolSchemas.KEY_ESTIMATED_TOKENS,
      traversalTokenAccountingJson(
        responseTotal = metadataTokens + bodyTokens + prunedBodyTokens,
        metadataTokens = metadataTokens,
        bodyTokens = bodyTokens,
        prunedBodyTokens = prunedBodyTokens,
      ),
    )
  }
}

private data class JsonWithTokens(
  val json: JsonObject,
  val tokens: Int,
  val bodyTokens: Int = 0,
  val prunedBodyTokens: Int = 0,
)

private fun traversalEntrypointJson(entrypoint: TraversalEntrypoint): JsonWithTokens {
  val tokens = estimateTraversalTokens(
    listOf(entrypoint.id.value, entrypoint.reason, entrypoint.score.toString()),
  )
  return JsonWithTokens(
    json = buildJsonObject {
      put(ToolSchemas.KEY_ID, JsonPrimitive(entrypoint.id.value))
      put(ToolSchemas.KEY_REASON, JsonPrimitive(entrypoint.reason))
      put(ToolSchemas.KEY_SCORE, JsonPrimitive(entrypoint.score))
    },
    tokens = tokens,
  )
}

private fun traversalNodeJson(node: TraversalNode): JsonWithTokens {
  val reason = nodeReason(node)
  val bodyTokens = node.body?.let(TokenEstimator::estimateBody).orZero()
  val tokens = estimateTraversalTokens(
    listOf(
      node.id.value,
      node.type,
      node.depth.toString(),
      node.score.toString(),
      reason,
      node.snippet,
    ) + node.matchFields,
  )
  return JsonWithTokens(
    json = buildJsonObject {
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
    },
    tokens = tokens,
    bodyTokens = bodyTokens,
  )
}

private fun traversalEdgeJson(edge: TraversalEdge): JsonWithTokens {
  val reason = traversalEdgeReason(edge.type)
  val tokens = estimateTraversalTokens(
    listOf(edge.from.value, edge.to.value, edge.type.labelValue(), edge.weight.toString(), reason),
  )
  return JsonWithTokens(
    json = buildJsonObject {
      put(ToolSchemas.KEY_FROM, JsonPrimitive(edge.from.value))
      put(ToolSchemas.KEY_TO, JsonPrimitive(edge.to.value))
      put(ToolSchemas.KEY_TYPE, JsonPrimitive(edge.type.labelValue()))
      put(ToolSchemas.KEY_LABEL, JsonPrimitive(edge.type.labelValue()))
      put(ToolSchemas.KEY_WEIGHT, JsonPrimitive(edge.weight))
      put(ToolSchemas.KEY_REASON, JsonPrimitive(reason))
    },
    tokens = tokens,
  )
}

private fun traversalPrunedJson(candidate: TraversalPrunedCandidate): JsonWithTokens {
  val reason = traversalPrunedReasonWire(candidate.reason)
  val tokens = estimateTraversalTokens(
    listOf(candidate.id.value, reason, candidate.score.toString(), candidate.estimatedTokens.toString()),
  )
  return JsonWithTokens(
    json = buildJsonObject {
      put(ToolSchemas.KEY_ID, JsonPrimitive(candidate.id.value))
      put(ToolSchemas.KEY_REASON, JsonPrimitive(reason))
      put(ToolSchemas.KEY_SCORE, JsonPrimitive(candidate.score))
      put(ToolSchemas.KEY_ESTIMATED_TOKENS, JsonPrimitive(candidate.estimatedTokens))
    },
    tokens = tokens,
    prunedBodyTokens = candidate.estimatedTokens,
  )
}

private fun traversalSuggestedReadJson(read: TraversalSuggestedRead): JsonWithTokens {
  val priority = traversalPriority(read.priority)
  val reason = traversalSuggestedReadReason(read.reason)
  val tokens = estimateTraversalTokens(
    listOf(read.id.value, reason, priority),
  )
  return JsonWithTokens(
    json = buildJsonObject {
      put(ToolSchemas.KEY_ID, JsonPrimitive(read.id.value))
      put(ToolSchemas.KEY_REASON, JsonPrimitive(reason))
      put(ToolSchemas.KEY_PRIORITY, JsonPrimitive(priority))
    },
    tokens = tokens,
  )
}

private fun traversalTokenAccountingJson(
  responseTotal: Int,
  metadataTokens: Int,
  bodyTokens: Int,
  prunedBodyTokens: Int,
): JsonObject = buildJsonObject {
  put(ToolSchemas.KEY_RESPONSE_TOTAL, JsonPrimitive(responseTotal))
  put(ToolSchemas.KEY_METADATA_TOKENS, JsonPrimitive(metadataTokens))
  put(ToolSchemas.KEY_BODY_TOKENS, JsonPrimitive(bodyTokens))
  put(ToolSchemas.KEY_PRUNED_BODY_TOKENS, JsonPrimitive(prunedBodyTokens))
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

private fun estimateTraversalTokens(values: Iterable<String>): Int {
  var total = 0
  for (value in values) {
    total += TokenEstimator.estimateString(value)
  }
  return total
}

private fun Int?.orZero(): Int = this ?: 0

private const val HIGH_PRIORITY_SCORE: Int = 80
private const val MEDIUM_PRIORITY_SCORE: Int = 40
