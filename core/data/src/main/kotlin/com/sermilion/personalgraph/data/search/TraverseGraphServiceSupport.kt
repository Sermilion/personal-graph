package com.sermilion.personalgraph.data.search

import com.sermilion.personalgraph.domain.graph.GraphIndexEntry
import com.sermilion.personalgraph.domain.layout.VaultLayout
import com.sermilion.personalgraph.domain.layout.VaultPolicy
import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.model.VaultNode
import com.sermilion.personalgraph.domain.search.SearchRecency
import com.sermilion.personalgraph.domain.search.TraversalEdge
import com.sermilion.personalgraph.domain.search.TraversalEdgeType
import com.sermilion.personalgraph.domain.search.TraversalEntrypoint
import com.sermilion.personalgraph.domain.search.TraversalNode
import com.sermilion.personalgraph.domain.search.TraversalPrunedCandidate
import com.sermilion.personalgraph.domain.search.TraverseGraphQuery

internal data class GraphTraversalRequestScope(
  val branches: List<String>,
  val startIds: List<NodeId>,
)

internal data class TraversalCandidate(
  val entry: GraphIndexEntry,
  var depth: Int,
  var score: Int,
  val matchFields: MutableList<String>,
  var body: String? = null,
) {
  fun reason(): String = matchFields.firstOrNull().orEmpty()
}

internal data class TraversalState(
  val entrypoints: List<TraversalEntrypoint>,
  val candidates: LinkedHashMap<NodeId, TraversalCandidate>,
  val edges: List<TraversalEdge>,
)

internal data class TraversalSelection(
  val included: List<TraversalCandidate>,
  val pruned: List<TraversalPrunedCandidate>,
)

internal data class TraversalExpansionBudget(
  val maxCandidates: Int,
  val maxEdges: Int,
) {
  fun canAddCandidate(currentSize: Int): Boolean = currentSize < maxCandidates

  fun canAddEdge(currentSize: Int): Boolean = currentSize < maxEdges
}

internal data class EdgeKey(
  val from: NodeId,
  val to: NodeId,
  val type: TraversalEdgeType,
)

internal data class TraversalFrontier(
  val candidates: LinkedHashMap<NodeId, TraversalCandidate>,
  val queue: ArrayDeque<NodeId>,
  val edges: LinkedHashMap<EdgeKey, TraversalEdge>,
)

internal data class TraversalExpansionContext(
  val query: TraverseGraphQuery,
  val scope: GraphTraversalRequestScope,
  val branchEntries: LinkedHashMap<NodeId, GraphIndexEntry>,
  val expansionBudget: TraversalExpansionBudget,
  val frontier: TraversalFrontier,
  var backlinkSourcesByTarget: Map<NodeId, List<GraphIndexEntry>>? = null,
  var backlinkSourceEntryCount: Int = -1,
  var scopedBranchesWarmed: Boolean = false,
)

internal sealed interface TraversalLookup {
  val entry: GraphIndexEntry
}

internal data class PathLookup(
  override val entry: GraphIndexEntry,
) : TraversalLookup

internal data class MetadataLookup(
  override val entry: GraphIndexEntry,
) : TraversalLookup

internal fun effectiveBranches(requested: List<String>): List<String> {
  if (requested.isNotEmpty()) return requested.map { it.trim('/') }.filter { it.isNotEmpty() }
  return DEFAULT_TRAVERSAL_BRANCHES
}

internal fun entryWithinScope(entry: GraphIndexEntry, scope: GraphTraversalRequestScope): Boolean {
  val branches = scope.branches
  return branches.any { branch ->
    entry.id.value == branch ||
      entry.id.value.startsWith("$branch/") ||
      entry.branch == branch ||
      entry.branch.startsWith("$branch/")
  }
}

internal fun classifyForwardEdge(
  source: GraphIndexEntry,
  target: GraphIndexEntry,
): TraversalEdgeType = when {
  target.id.value.contains("contradict", ignoreCase = true) -> TraversalEdgeType.Contradiction
  target.type == TYPE_PATTERN || target.id.value.startsWith("${VaultLayout.BRANCH_PATTERNS}/") -> {
    TraversalEdgeType.Pattern
  }
  source.id.value.startsWith("${VaultLayout.BRANCH_TIMELINE}/") ||
    target.id.value.startsWith("${VaultLayout.BRANCH_TIMELINE}/") ||
    target.type == TYPE_EPISODE -> TraversalEdgeType.Timeline
  target.type == TYPE_SUBJECT || target.id.value.contains("/${VaultLayout.SUB_DOMAIN_SUBJECTS}/") -> {
    TraversalEdgeType.SubjectEvidence
  }
  target.type == TYPE_STATE || target.id.value.startsWith("${VaultLayout.BRANCH_STATE}/") -> TraversalEdgeType.State
  target.id.value.startsWith("${VaultLayout.BRANCH_OUTDATED}/") -> TraversalEdgeType.Background
  else -> TraversalEdgeType.Link
}

internal fun addEdge(
  edges: MutableMap<EdgeKey, TraversalEdge>,
  from: NodeId,
  to: NodeId,
  type: TraversalEdgeType,
) {
  edges.putIfAbsent(
    EdgeKey(from, to, type),
    TraversalEdge(
      from = from,
      to = to,
      type = type,
      label = edgeLabel(type),
      weight = edgeWeight(type),
    ),
  )
}

internal fun edgeLabel(type: TraversalEdgeType): String = when (type) {
  TraversalEdgeType.Link -> "link"
  TraversalEdgeType.Backlink -> "backlink"
  TraversalEdgeType.SubjectEvidence -> "subject_evidence"
  TraversalEdgeType.Timeline -> "timeline"
  TraversalEdgeType.State -> "state"
  TraversalEdgeType.Pattern -> "pattern"
  TraversalEdgeType.Contradiction -> "contradiction"
  TraversalEdgeType.Background -> "background"
}

internal fun edgeWeight(type: TraversalEdgeType): Int = when (type) {
  TraversalEdgeType.Link -> EDGE_WEIGHT_LINK
  TraversalEdgeType.Backlink -> EDGE_WEIGHT_BACKLINK
  TraversalEdgeType.SubjectEvidence -> EDGE_WEIGHT_SUBJECT_EVIDENCE
  TraversalEdgeType.Timeline -> EDGE_WEIGHT_TIMELINE
  TraversalEdgeType.State -> EDGE_WEIGHT_STATE
  TraversalEdgeType.Pattern -> EDGE_WEIGHT_PATTERN
  TraversalEdgeType.Contradiction -> EDGE_WEIGHT_CONTRADICTION
  TraversalEdgeType.Background -> EDGE_WEIGHT_BACKGROUND
}

internal fun metadataMatches(entry: GraphIndexEntry, lowerQuery: String): Boolean {
  val subjectMatches = entry.subject?.contains(lowerQuery, ignoreCase = true) == true
  return subjectMatches ||
    entry.topic?.contains(lowerQuery, ignoreCase = true) == true ||
    entry.aliases.any { it.contains(lowerQuery, ignoreCase = true) } ||
    entry.hypothesis?.contains(lowerQuery, ignoreCase = true) == true
}

internal fun branchMatches(entry: GraphIndexEntry, lowerQuery: String): Boolean {
  val domainMatches = entry.domain?.contains(lowerQuery, ignoreCase = true) == true
  return domainMatches || entry.branch.contains(lowerQuery, ignoreCase = true)
}

internal fun stripRecencyTriggers(query: String): String {
  val tokens = query.split(' ').filter { token ->
    val lower = token.lowercase()
    lower.isNotEmpty() && SearchRecency.TRIGGERS.none { it == lower }
  }
  return tokens.joinToString(" ")
}

internal fun containsRecencyTrigger(query: String): Boolean = SearchRecency.TRIGGERS.any { query.contains(it) }

internal fun leafOf(id: String): String = id.substringAfterLast('/')

internal fun candidateComparator(query: TraverseGraphQuery): Comparator<TraversalCandidate> {
  val relevance = compareByDescending<TraversalCandidate> { it.score }
    .thenBy { it.depth }
  val recency = if (recencyRequested(query)) {
    relevance.thenByDescending { it.entry.date ?: it.entry.updated }
  } else {
    relevance
  }
  return recency.thenBy { it.entry.id.value }
}

internal fun toNode(candidate: TraversalCandidate): TraversalNode {
  val entry = candidate.entry
  return TraversalNode(
    id = entry.id,
    type = entry.type,
    domain = entry.domain,
    subject = entry.subject ?: entry.topic ?: entry.category,
    snippet = entry.snippet,
    score = candidate.score,
    depth = candidate.depth,
    matchFields = candidate.matchFields.toList(),
    body = candidate.body,
  )
}

internal fun expansionBudget(query: TraverseGraphQuery): TraversalExpansionBudget {
  val maxNodes = query.maxNodes.coerceAtLeast(0)
  val budgetTokens = query.budgetTokens.coerceAtLeast(0)
  val nodeCandidateCap = maxNodes * EXPANSION_OVERFETCH_FACTOR
  val tokenCandidateCap = if (budgetTokens == 0) {
    0
  } else {
    (budgetTokens / MIN_CANDIDATE_TOKEN_ESTIMATE).coerceAtLeast(1) * EXPANSION_OVERFETCH_FACTOR
  }
  val candidateCap = minOf(nodeCandidateCap, tokenCandidateCap)
  return TraversalExpansionBudget(
    maxCandidates = candidateCap,
    maxEdges = (candidateCap * EXPANSION_EDGE_FACTOR).coerceAtLeast(candidateCap),
  )
}

internal fun entryAllowed(entry: GraphIndexEntry): Boolean = idAllowed(entry.id.value) && idAllowed(entry.branch)

internal fun nodeAllowed(node: VaultNode): Boolean = nodeIdAllowed(node.id)

internal fun nodeIdAllowed(id: NodeId): Boolean = idAllowed(id.value)

internal fun idAllowed(raw: String): Boolean = VaultPolicy.isReadAllowed(raw) && !VaultPolicy.isIndexExcluded(raw)

private const val TYPE_EPISODE: String = "episode"
private const val TYPE_PATTERN: String = "pattern"
private const val TYPE_STATE: String = "state"
private const val TYPE_SUBJECT: String = "subject"
private const val EDGE_WEIGHT_LINK: Int = 8
private const val EDGE_WEIGHT_BACKLINK: Int = 7
private const val EDGE_WEIGHT_SUBJECT_EVIDENCE: Int = 14
private const val EDGE_WEIGHT_TIMELINE: Int = 12
private const val EDGE_WEIGHT_STATE: Int = 10
private const val EDGE_WEIGHT_PATTERN: Int = 12
private const val EDGE_WEIGHT_CONTRADICTION: Int = 15
private const val EDGE_WEIGHT_BACKGROUND: Int = 4
private const val EXPANSION_OVERFETCH_FACTOR: Int = 4
private const val EXPANSION_EDGE_FACTOR: Int = 6
private const val MIN_CANDIDATE_TOKEN_ESTIMATE: Int = 8

private val DEFAULT_TRAVERSAL_BRANCHES: List<String> = listOf(
  VaultLayout.BRANCH_STATE,
  VaultLayout.BRANCH_DOMAINS,
  VaultLayout.BRANCH_PATTERNS,
  VaultLayout.BRANCH_EMOTIONAL_STATES,
  VaultLayout.BRANCH_TIMELINE,
  VaultLayout.BRANCH_STAGING_OBSERVATIONS,
  VaultLayout.BRANCH_OUTDATED,
)
