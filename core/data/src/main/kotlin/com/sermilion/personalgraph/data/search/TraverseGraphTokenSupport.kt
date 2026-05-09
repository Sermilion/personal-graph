package com.sermilion.personalgraph.data.search

import com.sermilion.personalgraph.domain.search.TraversalEdge
import com.sermilion.personalgraph.domain.search.TraversalEntrypoint
import com.sermilion.personalgraph.domain.search.TraversalNode
import com.sermilion.personalgraph.domain.search.TraversalPrunedCandidate
import com.sermilion.personalgraph.domain.search.TraversalSuggestedRead
import com.sermilion.personalgraph.domain.tokens.TokenEstimator

internal fun estimateTokens(
  tokenEstimator: TokenEstimator,
  entrypoints: List<TraversalEntrypoint>,
  nodes: List<TraversalNode>,
  edges: List<TraversalEdge>,
  pruned: List<TraversalPrunedCandidate>,
  suggestedReads: List<TraversalSuggestedRead>,
): Int {
  var total = 0
  for (entrypoint in entrypoints) {
    total += estimateEntrypoint(tokenEstimator, entrypoint)
  }
  for (node in nodes) {
    total += tokenEstimator.estimateString(node.id.value)
    total += tokenEstimator.estimateString(node.snippet)
    node.domain?.let { total += tokenEstimator.estimateString(it) }
    node.subject?.let { total += tokenEstimator.estimateString(it) }
    node.body?.let { total += tokenEstimator.estimateBody(it) }
  }
  for (edge in edges) {
    total += estimateEdge(tokenEstimator, edge)
  }
  for (candidate in pruned) {
    total += estimatePruned(tokenEstimator, candidate)
  }
  for (read in suggestedReads) {
    total += estimateSuggestedRead(tokenEstimator, read)
  }
  return total
}

internal fun estimateEntrypoints(
  tokenEstimator: TokenEstimator,
  entrypoints: List<TraversalEntrypoint>,
): Int = entrypoints.sumOf { estimateEntrypoint(tokenEstimator, it) }

internal fun estimateEntrypoint(
  tokenEstimator: TokenEstimator,
  entrypoint: TraversalEntrypoint,
): Int = tokenEstimator.estimateString(entrypoint.id.value) +
  tokenEstimator.estimateString(entrypoint.reason)

internal fun estimateEdge(
  tokenEstimator: TokenEstimator,
  edge: TraversalEdge,
): Int = tokenEstimator.estimateString(edge.from.value) +
  tokenEstimator.estimateString(edge.to.value) +
  tokenEstimator.estimateString(edge.label)

internal fun estimateCandidate(
  tokenEstimator: TokenEstimator,
  candidate: TraversalCandidate,
  includeBody: Boolean,
): Int {
  val node = toNode(candidate)
  var total = 0
  total += tokenEstimator.estimateString(node.id.value)
  total += tokenEstimator.estimateString(node.snippet)
  node.domain?.let { total += tokenEstimator.estimateString(it) }
  node.subject?.let { total += tokenEstimator.estimateString(it) }
  if (includeBody) {
    total += node.body?.let(tokenEstimator::estimateBody) ?: candidate.entry.bodyTokenEstimate
  }
  return total
}

internal fun estimatePruned(
  tokenEstimator: TokenEstimator,
  candidate: TraversalPrunedCandidate,
): Int = tokenEstimator.estimateString(candidate.id.value) +
  tokenEstimator.estimateString(candidate.reason.name)

internal fun estimateSuggestedRead(
  tokenEstimator: TokenEstimator,
  read: TraversalSuggestedRead,
): Int = tokenEstimator.estimateString(read.id.value) +
  tokenEstimator.estimateString(read.reason)
