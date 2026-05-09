package com.sermilion.personalgraph.data.search

import com.sermilion.personalgraph.domain.search.TraversalEdge
import com.sermilion.personalgraph.domain.search.TraversalNode
import com.sermilion.personalgraph.domain.tokens.TokenEstimator

internal fun estimateTokens(
  tokenEstimator: TokenEstimator,
  nodes: List<TraversalNode>,
  edges: List<TraversalEdge>,
): Int {
  var total = 0
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
  return total
}

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
