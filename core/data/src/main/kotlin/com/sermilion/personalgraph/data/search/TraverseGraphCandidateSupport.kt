package com.sermilion.personalgraph.data.search

import com.sermilion.personalgraph.domain.graph.GraphIndexEntry
import com.sermilion.personalgraph.domain.model.NodeId

internal fun addOrImprove(
  candidates: MutableMap<NodeId, TraversalCandidate>,
  entry: GraphIndexEntry,
  depth: Int,
  score: Int,
  field: String,
) {
  val existing = candidates[entry.id]
  if (existing == null) {
    candidates[entry.id] = TraversalCandidate(
      entry = entry,
      depth = depth,
      score = score,
      matchFields = mutableListOf(field),
    )
  } else {
    if (score > existing.score) existing.score = score
    if (depth < existing.depth) existing.depth = depth
    if (field !in existing.matchFields) existing.matchFields += field
  }
}

internal fun addOrImproveEntrypoint(
  candidates: MutableMap<NodeId, TraversalCandidate>,
  entry: GraphIndexEntry,
  depth: Int,
  score: Int,
  field: String,
  maxEntrypoints: Int,
) {
  when {
    entry.id in candidates -> addOrImprove(candidates, entry, depth, score, field)
    maxEntrypoints <= 0 -> Unit
    candidates.size < maxEntrypoints -> addOrImprove(candidates, entry, depth, score, field)
    else -> replaceWorstEntrypointIfBetter(
      candidates,
      TraversalCandidate(
        entry = entry,
        depth = depth,
        score = score,
        matchFields = mutableListOf(field),
      ),
    )
  }
}

private fun replaceWorstEntrypointIfBetter(
  candidates: MutableMap<NodeId, TraversalCandidate>,
  replacement: TraversalCandidate,
) {
  val comparator = entrypointBetterFirstComparator()
  val worst = candidates.values.maxWithOrNull(comparator)
  if (worst != null && comparator.compare(replacement, worst) < 0) {
    candidates.remove(worst.entry.id)
    candidates[replacement.entry.id] = replacement
  }
}

private fun entrypointBetterFirstComparator(): Comparator<TraversalCandidate> {
  val scoreComparator = compareByDescending<TraversalCandidate> { it.score }
  return scoreComparator
    .thenBy { it.depth }
    .thenBy { it.entry.id.value }
}
