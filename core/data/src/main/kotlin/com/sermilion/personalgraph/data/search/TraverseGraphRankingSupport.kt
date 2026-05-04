package com.sermilion.personalgraph.data.search

import com.sermilion.personalgraph.domain.graph.GraphIndexEntry
import com.sermilion.personalgraph.domain.layout.VaultLayout
import com.sermilion.personalgraph.domain.search.TraversalEdgeType
import com.sermilion.personalgraph.domain.search.TraversalRankBy
import com.sermilion.personalgraph.domain.search.TraverseGraphQuery

internal fun exactMatchBoost(entry: GraphIndexEntry, query: String): Int {
  if (query.isBlank()) return 0
  return when {
    entry.id.value == query -> BOOST_EXACT
    leafOf(entry.id.value) == leafOf(query) -> BOOST_EXACT
    entry.subject.equals(query, ignoreCase = true) -> BOOST_EXACT
    entry.topic.equals(query, ignoreCase = true) -> BOOST_EXACT
    entry.aliases.any { it.equals(query, ignoreCase = true) } -> BOOST_EXACT
    else -> 0
  }
}

internal fun subjectHubBoost(entry: GraphIndexEntry): Int {
  val subjectHub = entry.type == TYPE_SUBJECT || entry.id.value.contains("/${VaultLayout.SUB_DOMAIN_SUBJECTS}/")
  return if (subjectHub) BOOST_SUBJECT_HUB else 0
}

internal fun directEvidenceBoost(candidate: TraversalCandidate): Int {
  val directEvidenceField = edgeLabel(TraversalEdgeType.SubjectEvidence)
  return if (directEvidenceField in candidate.matchFields) BOOST_DIRECT_EVIDENCE else 0
}

internal fun recencyBoost(entry: GraphIndexEntry, requested: Boolean): Int {
  if (!requested) return 0
  val eventLike = entry.date != null ||
    entry.type == TYPE_EPISODE ||
    entry.type == TYPE_EMOTIONAL_STATE ||
    entry.id.value.startsWith("${VaultLayout.BRANCH_TIMELINE}/")
  return if (eventLike) BOOST_RECENT_EVENT else 0
}

internal fun highDegreePenalty(entry: GraphIndexEntry): Int = when {
  entry.linkCount >= VERY_HIGH_DEGREE -> PENALTY_VERY_HIGH_DEGREE
  entry.linkCount >= HIGH_DEGREE -> PENALTY_HIGH_DEGREE
  entry.linkCount >= MODERATE_DEGREE -> PENALTY_MODERATE_DEGREE
  else -> 0
}

internal fun unrelatedHubPenalty(entry: GraphIndexEntry, lowerQuery: String): Int {
  if (lowerQuery.isBlank()) return 0
  val hubLike = entry.type == TYPE_SUBJECT || entry.linkCount >= HIGH_DEGREE
  if (!hubLike) return 0
  val related = entry.id.value.contains(lowerQuery, ignoreCase = true) ||
    metadataMatches(entry, lowerQuery) ||
    branchMatches(entry, lowerQuery)
  return if (related) 0 else PENALTY_UNRELATED_HUB
}

internal fun recencyRequested(query: TraverseGraphQuery): Boolean {
  val lowerQuery = query.query.lowercase()
  return query.rankBy == TraversalRankBy.Recency || containsRecencyTrigger(lowerQuery)
}

private const val TYPE_EPISODE: String = "episode"
private const val TYPE_EMOTIONAL_STATE: String = "emotional-state"
private const val TYPE_SUBJECT: String = "subject"
private const val BOOST_EXACT: Int = 20
private const val BOOST_SUBJECT_HUB: Int = 14
private const val BOOST_DIRECT_EVIDENCE: Int = 12
private const val BOOST_RECENT_EVENT: Int = 15
private const val MODERATE_DEGREE: Int = 10
private const val HIGH_DEGREE: Int = 20
private const val VERY_HIGH_DEGREE: Int = 50
private const val PENALTY_MODERATE_DEGREE: Int = 8
private const val PENALTY_HIGH_DEGREE: Int = 16
private const val PENALTY_VERY_HIGH_DEGREE: Int = 28
private const val PENALTY_UNRELATED_HUB: Int = 12
