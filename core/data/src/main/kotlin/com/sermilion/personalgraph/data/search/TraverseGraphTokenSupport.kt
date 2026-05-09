package com.sermilion.personalgraph.data.search

import com.sermilion.personalgraph.domain.search.TraversalEdge
import com.sermilion.personalgraph.domain.search.TraversalEdgeType
import com.sermilion.personalgraph.domain.search.TraversalEntrypoint
import com.sermilion.personalgraph.domain.search.TraversalNode
import com.sermilion.personalgraph.domain.search.TraversalPrunedCandidate
import com.sermilion.personalgraph.domain.search.TraversalPrunedReason
import com.sermilion.personalgraph.domain.search.TraversalSuggestedRead
import com.sermilion.personalgraph.domain.search.TraversalTokenAccounting
import com.sermilion.personalgraph.domain.tokens.TokenEstimator

internal fun estimateTokenAccounting(
  tokenEstimator: TokenEstimator,
  entrypoints: List<TraversalEntrypoint>,
  nodes: List<TraversalNode>,
  edges: List<TraversalEdge>,
  pruned: List<TraversalPrunedCandidate>,
  suggestedReads: List<TraversalSuggestedRead>,
): TraversalTokenAccounting {
  val bodyTokens = nodes.sumOf { node -> node.body?.let(tokenEstimator::estimateBody) ?: 0 }
  val prunedBodyTokens = pruned.sumOf { it.bodyTokenEstimate }
  val metadataTokens = estimateMetadataTokens(
    TraversalTokenEstimateInput(
      tokenEstimator = tokenEstimator,
      content = TraversalTokenContent(
        entrypoints = entrypoints,
        nodes = nodes,
        edges = edges,
        pruned = pruned,
        suggestedReads = suggestedReads,
      ),
      bodyAccounting = TraversalTokenAccounting(
        bodyTokens = bodyTokens,
        prunedBodyTokens = prunedBodyTokens,
      ),
    ),
  )
  val responseTotal = metadataTokens + bodyTokens
  return TraversalTokenAccounting(
    responseTotal = responseTotal,
    metadataTokens = metadataTokens,
    bodyTokens = bodyTokens,
    prunedBodyTokens = prunedBodyTokens,
  )
}

private data class TraversalTokenEstimateInput(
  val tokenEstimator: TokenEstimator,
  val content: TraversalTokenContent,
  val bodyAccounting: TraversalTokenAccounting,
)

private data class TraversalTokenContent(
  val entrypoints: List<TraversalEntrypoint>,
  val nodes: List<TraversalNode>,
  val edges: List<TraversalEdge>,
  val pruned: List<TraversalPrunedCandidate>,
  val suggestedReads: List<TraversalSuggestedRead>,
)

internal fun estimateTokens(
  tokenEstimator: TokenEstimator,
  entrypoints: List<TraversalEntrypoint>,
  nodes: List<TraversalNode>,
  edges: List<TraversalEdge>,
  pruned: List<TraversalPrunedCandidate>,
  suggestedReads: List<TraversalSuggestedRead>,
): Int = estimateTokenAccounting(
  tokenEstimator = tokenEstimator,
  entrypoints = entrypoints,
  nodes = nodes,
  edges = edges,
  pruned = pruned,
  suggestedReads = suggestedReads,
).responseTotal

private fun estimateMetadataTokens(input: TraversalTokenEstimateInput): Int {
  var metadataTokens = 0
  repeat(METADATA_ACCOUNTING_ITERATIONS) {
    val accounting = TraversalTokenAccounting(
      responseTotal = metadataTokens + input.bodyAccounting.bodyTokens,
      metadataTokens = metadataTokens,
      bodyTokens = input.bodyAccounting.bodyTokens,
      prunedBodyTokens = input.bodyAccounting.prunedBodyTokens,
    )
    val next = estimateMetadataTokensOnce(
      input = input,
      accounting = accounting,
    )
    if (next == metadataTokens) return metadataTokens
    metadataTokens = next
  }
  return metadataTokens
}

private fun estimateMetadataTokensOnce(
  input: TraversalTokenEstimateInput,
  accounting: TraversalTokenAccounting,
): Int = input.tokenEstimator.estimateMetadata(
  buildString {
    appendLine("status:ok")
    appendLine("entrypoints:")
    for (entrypoint in input.content.entrypoints) {
      appendTraversalEntrypoint(entrypoint)
    }
    appendLine("nodes:")
    for (node in input.content.nodes) {
      appendTraversalNode(node, includeBodyValue = false)
    }
    appendLine("edges:")
    for (edge in input.content.edges) {
      appendTraversalEdge(edge)
    }
    appendLine("pruned:")
    for (candidate in input.content.pruned) {
      appendTraversalPruned(candidate)
    }
    appendLine("suggested_reads:")
    for (read in input.content.suggestedReads) {
      appendTraversalSuggestedRead(read)
    }
    appendLine("estimated_tokens:")
    appendLine("response_total:${accounting.responseTotal}")
    appendLine("metadata_tokens:${accounting.metadataTokens}")
    appendLine("body_tokens:${accounting.bodyTokens}")
    appendLine("pruned_body_tokens:${accounting.prunedBodyTokens}")
  },
)

internal fun estimateEntrypoints(
  tokenEstimator: TokenEstimator,
  entrypoints: List<TraversalEntrypoint>,
): Int = entrypoints.sumOf { estimateEntrypoint(tokenEstimator, it) }

internal fun estimateEntrypoint(
  tokenEstimator: TokenEstimator,
  entrypoint: TraversalEntrypoint,
): Int = tokenEstimator.estimateMetadata(
  buildString {
    appendTraversalEntrypoint(entrypoint)
  },
)

internal fun estimateEdge(
  tokenEstimator: TokenEstimator,
  edge: TraversalEdge,
): Int = tokenEstimator.estimateMetadata(
  buildString {
    appendTraversalEdge(edge)
  },
)

internal fun estimateCandidate(
  tokenEstimator: TokenEstimator,
  candidate: TraversalCandidate,
  includeBody: Boolean,
): Int {
  val node = toNode(candidate)
  var total = tokenEstimator.estimateMetadata(
    buildString {
      appendTraversalNode(node, includeBodyValue = false)
    },
  )
  if (includeBody) {
    total += node.body?.let(tokenEstimator::estimateBody) ?: candidate.entry.bodyTokenEstimate
  }
  return total
}

internal fun estimatePruned(
  tokenEstimator: TokenEstimator,
  candidate: TraversalPrunedCandidate,
): Int = tokenEstimator.estimateMetadata(
  buildString {
    appendTraversalPruned(candidate)
  },
)

internal fun estimateSuggestedRead(
  tokenEstimator: TokenEstimator,
  read: TraversalSuggestedRead,
): Int = tokenEstimator.estimateMetadata(
  buildString {
    appendTraversalSuggestedRead(read)
  },
)

private fun StringBuilder.appendTraversalEntrypoint(entrypoint: TraversalEntrypoint) {
  appendLine("id:${entrypoint.id.value}")
  appendLine("reason:${entrypoint.reason}")
  appendLine("score:${entrypoint.score}")
}

private fun StringBuilder.appendTraversalNode(
  node: TraversalNode,
  includeBodyValue: Boolean,
) {
  appendLine("id:${node.id.value}")
  appendLine("type:${node.type}")
  appendLine("distance:${node.depth}")
  appendLine("score:${node.score}")
  appendLine("reason:${node.reason()}")
  appendLine("snippet:${node.snippet}")
  if (node.matchFields.isNotEmpty()) {
    appendLine("match_fields:${node.matchFields.joinToString(",")}")
  }
  node.body?.let { body ->
    if (includeBodyValue) {
      appendLine("body:$body")
    } else {
      appendLine("body:")
    }
  }
}

private fun StringBuilder.appendTraversalEdge(edge: TraversalEdge) {
  appendLine("from:${edge.from.value}")
  appendLine("to:${edge.to.value}")
  appendLine("type:${edge.type.labelValue()}")
  appendLine("label:${edge.type.labelValue()}")
  appendLine("weight:${edge.weight}")
  appendLine("reason:${edge.type.reasonValue()}")
}

private fun StringBuilder.appendTraversalPruned(candidate: TraversalPrunedCandidate) {
  appendLine("id:${candidate.id.value}")
  appendLine("reason:${candidate.reason.wireValue()}")
  appendLine("score:${candidate.score}")
  appendLine("estimated_tokens:${candidate.estimatedTokens}")
}

private fun StringBuilder.appendTraversalSuggestedRead(read: TraversalSuggestedRead) {
  appendLine("id:${read.id.value}")
  appendLine("reason:${read.reason.suggestedReadReasonValue()}")
  appendLine("priority:${read.priority.priorityValue()}")
}

private fun TraversalNode.reason(): String = matchFields.firstOrNull().orEmpty()

private fun TraversalEdgeType.labelValue(): String = when (this) {
  TraversalEdgeType.Link -> "link"
  TraversalEdgeType.Backlink -> "backlink"
  TraversalEdgeType.SubjectEvidence -> "subject_evidence"
  TraversalEdgeType.Timeline -> "timeline"
  TraversalEdgeType.State -> "state"
  TraversalEdgeType.Pattern -> "pattern"
  TraversalEdgeType.Contradiction -> "contradiction"
  TraversalEdgeType.Background -> "background"
}

private fun TraversalEdgeType.reasonValue(): String = when (this) {
  TraversalEdgeType.Link -> "generic link"
  TraversalEdgeType.Backlink -> "reverse link"
  TraversalEdgeType.SubjectEvidence -> "subject evidence link"
  TraversalEdgeType.Timeline -> "timeline link"
  TraversalEdgeType.State -> "state link"
  TraversalEdgeType.Pattern -> "pattern link"
  TraversalEdgeType.Contradiction -> "contradiction link"
  TraversalEdgeType.Background -> "background link"
}

private fun TraversalPrunedReason.wireValue(): String = when (this) {
  TraversalPrunedReason.MaxNodes -> "max_nodes"
  TraversalPrunedReason.BudgetTokens -> "budget_tokens"
}

private const val METADATA_ACCOUNTING_ITERATIONS: Int = 4
