package com.sermilion.personalgraph.data.search

import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.search.TraversalEdge
import com.sermilion.personalgraph.domain.search.TraversalPrunedCandidate
import com.sermilion.personalgraph.domain.search.TraversalPrunedReason
import com.sermilion.personalgraph.domain.search.TraversalSuggestedRead
import com.sermilion.personalgraph.domain.search.TraverseGraphQuery
import com.sermilion.personalgraph.domain.tokens.TokenEstimator

internal class TraversalSelectionBuilder(
  private val tokenEstimator: TokenEstimator,
  private val query: TraverseGraphQuery,
  private val allEdges: List<TraversalEdge>,
) {
  private val edgesByEndpoint = edgesByEndpoint(allEdges)

  fun select(ranked: List<TraversalCandidate>): TraversalSelection {
    val included = mutableListOf<TraversalCandidate>()
    val includedIds = mutableSetOf<NodeId>()
    val pruned = mutableListOf<TraversalPrunedCandidate>()
    val maxNodes = query.maxNodes.coerceAtLeast(0)
    val budgetTokens = query.budgetTokens.coerceAtLeast(0)
    var usedTokens = 0
    for (candidate in ranked) {
      val nodeEstimate = estimateCandidate(tokenEstimator, candidate, query.includeBodies)
      val edgeEstimate = edgeEstimateForCandidate(candidate.entry.id, includedIds)
      val estimate = nodeEstimate + edgeEstimate
      when {
        included.size >= maxNodes -> pruned += prunedCandidate(candidate, TraversalPrunedReason.MaxNodes, estimate)
        usedTokens + estimate > budgetTokens -> {
          pruned += prunedCandidate(candidate, TraversalPrunedReason.BudgetTokens, estimate)
        }
        else -> {
          included += candidate
          includedIds += candidate.entry.id
          usedTokens += estimate
        }
      }
    }
    return TraversalSelection(included = included, pruned = pruned)
  }

  fun trimToBudget(selection: TraversalSelection): TraversalSelection {
    val budgetTokens = query.budgetTokens.coerceAtLeast(0)
    val included = selection.included.toMutableList()
    val includedIds = included.mapTo(mutableSetOf()) { it.entry.id }
    val pruned = selection.pruned.toMutableList()
    var usedTokens = included.sumOf { estimateCandidate(tokenEstimator, it, query.includeBodies) } +
      includedEdgeEstimate(includedIds)
    while (included.isNotEmpty() && usedTokens > budgetTokens) {
      val removed = included.removeAt(included.lastIndex)
      includedIds -= removed.entry.id
      val removedEstimate = estimateCandidate(tokenEstimator, removed, query.includeBodies)
      val removedEdgeEstimate = edgeEstimateForCandidate(removed.entry.id, includedIds)
      usedTokens -= removedEstimate + removedEdgeEstimate
      pruned += prunedCandidate(
        candidate = removed,
        reason = TraversalPrunedReason.BudgetTokens,
        estimate = removedEstimate,
      )
    }
    return TraversalSelection(included = included, pruned = pruned)
  }

  private fun includedEdgeEstimate(includedIds: Set<NodeId>): Int = allEdges.sumOf { edge ->
    if (edge.from in includedIds && edge.to in includedIds) estimateEdge(tokenEstimator, edge) else 0
  }

  private fun edgeEstimateForCandidate(candidateId: NodeId, includedIds: Set<NodeId>): Int {
    val candidateEdges = edgesByEndpoint[candidateId].orEmpty()
    return candidateEdges.sumOf { edge ->
      if (edgeTouchesCandidateAndIncluded(edge, candidateId, includedIds)) estimateEdge(tokenEstimator, edge) else 0
    }
  }

  private fun edgesByEndpoint(edges: List<TraversalEdge>): Map<NodeId, List<TraversalEdge>> {
    val byEndpoint = LinkedHashMap<NodeId, MutableList<TraversalEdge>>()
    for (edge in edges) {
      byEndpoint.getOrPut(edge.from) { mutableListOf() } += edge
      byEndpoint.getOrPut(edge.to) { mutableListOf() } += edge
    }
    return byEndpoint
  }

  private fun edgeTouchesCandidateAndIncluded(
    edge: TraversalEdge,
    candidateId: NodeId,
    includedIds: Set<NodeId>,
  ): Boolean = (edge.from == candidateId && edge.to in includedIds) ||
    (edge.to == candidateId && edge.from in includedIds)

  private fun prunedCandidate(
    candidate: TraversalCandidate,
    reason: TraversalPrunedReason,
    estimate: Int,
  ): TraversalPrunedCandidate = TraversalPrunedCandidate(
    id = candidate.entry.id,
    reason = reason,
    score = candidate.score,
    estimatedTokens = estimate,
  )
}

internal fun suggestedReads(pruned: List<TraversalPrunedCandidate>): List<TraversalSuggestedRead> {
  val ranked = pruned.sortedWith(
    compareByDescending<TraversalPrunedCandidate> { it.score }.thenBy { it.id.value },
  )
  return ranked.take(MAX_SUGGESTED_READS).map {
    TraversalSuggestedRead(
      id = it.id,
      reason = it.reason.name,
      priority = it.score,
    )
  }
}

private const val MAX_SUGGESTED_READS: Int = 5
