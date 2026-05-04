package com.sermilion.personalgraph.data.search

import com.sermilion.personalgraph.common.di.AppScope
import com.sermilion.personalgraph.common.dispatcher.DispatcherProvider
import com.sermilion.personalgraph.domain.graph.GraphIndexEntry
import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.repository.GraphIndexRepository
import com.sermilion.personalgraph.domain.repository.VaultRepository
import com.sermilion.personalgraph.domain.search.TraversalEdgeType
import com.sermilion.personalgraph.domain.search.TraversalEntrypoint
import com.sermilion.personalgraph.domain.search.TraverseGraphOutcome
import com.sermilion.personalgraph.domain.search.TraverseGraphQuery
import com.sermilion.personalgraph.domain.search.TraverseGraphService
import com.sermilion.personalgraph.domain.tokens.TokenEstimator
import kotlinx.coroutines.withContext
import me.tatarka.inject.annotations.Inject

@AppScope
@Inject
class PersonalGraphTraverseGraphService(
  private val graphIndexRepository: GraphIndexRepository,
  private val vaultRepository: VaultRepository,
  private val tokenEstimator: TokenEstimator,
  private val dispatcherProvider: DispatcherProvider,
) : TraverseGraphService {

  override suspend fun traverse(query: TraverseGraphQuery): TraverseGraphOutcome = withContext(dispatcherProvider.io) {
    val scope = GraphTraversalRequestScope(
      branches = effectiveBranches(query.branches).filter(::idAllowed),
      startIds = query.startIds.filter(::nodeIdAllowed).distinctBy { it.value },
    )
    val branchEntries = collectBranchEntries(scope)
    val expansionBudget = expansionBudget(query)
    val entrypoints = collectEntrypoints(
      rawQuery = query.query.trim(),
      scope = scope,
      branchEntries = branchEntries,
      maxEntrypoints = expansionBudget.maxCandidates,
    )
    val backlinkSourcesByTarget = if (shouldCollectBacklinks(query)) {
      buildBacklinkSourcesByTarget(branchEntries)
    } else {
      emptyMap()
    }
    val state = expandTraversal(query, scope, branchEntries, backlinkSourcesByTarget, entrypoints)
    applyRanking(query, state)
    val ranked = state.candidates.values
      .sortedWith(candidateComparator(query))
    val selectionBuilder = TraversalSelectionBuilder(tokenEstimator, query, state.edges)
    var selection = selectionBuilder.select(ranked)
    hydrateBodiesIfRequested(query, selection.included)
    selection = selectionBuilder.trimToBudget(selection)
    val nodes = selection.included.map(::toNode)
    val returnedIds = nodes.mapTo(mutableSetOf()) { it.id }
    val edges = state.edges.filter { it.from in returnedIds && it.to in returnedIds }
    TraverseGraphOutcome(
      entrypoints = state.entrypoints,
      nodes = nodes,
      edges = edges,
      pruned = selection.pruned,
      suggestedReads = suggestedReads(selection.pruned),
      estimatedTokens = estimateTokens(tokenEstimator, nodes, edges),
    )
  }

  private suspend fun collectBranchEntries(
    scope: GraphTraversalRequestScope,
  ): LinkedHashMap<NodeId, GraphIndexEntry> {
    val entries = LinkedHashMap<NodeId, GraphIndexEntry>()
    for (branch in scope.branches) {
      for (entry in graphIndexRepository.listEntriesInBranch(branch)) {
        if (entryAllowed(entry)) entries.putIfAbsent(entry.id, entry)
      }
    }
    return entries
  }

  private suspend fun collectEntrypoints(
    rawQuery: String,
    scope: GraphTraversalRequestScope,
    branchEntries: Map<NodeId, GraphIndexEntry>,
    maxEntrypoints: Int,
  ): List<TraversalCandidate> {
    val results = LinkedHashMap<NodeId, TraversalCandidate>()
    for (id in scope.startIds) {
      val entry = findAllowedEntry(id, scope, branchEntries) ?: continue
      addOrImproveEntrypoint(
        results,
        entry,
        depth = 0,
        score = SCORE_START_ID,
        field = MATCH_FIELD_START_ID,
        maxEntrypoints = maxEntrypoints,
      )
    }
    if (rawQuery.isNotEmpty()) {
      addQueryLookupEntrypoints(rawQuery, scope, branchEntries, results, maxEntrypoints)
      addQueryScanEntrypoints(rawQuery, branchEntries.values, results, maxEntrypoints)
    }
    return results.values.toList()
  }

  private suspend fun addQueryLookupEntrypoints(
    rawQuery: String,
    scope: GraphTraversalRequestScope,
    branchEntries: Map<NodeId, GraphIndexEntry>,
    results: MutableMap<NodeId, TraversalCandidate>,
    maxEntrypoints: Int,
  ) {
    val effectiveQuery = stripRecencyTriggers(rawQuery).ifEmpty { rawQuery }
    val lookups = listOfNotNull(
      graphIndexRepository.findEntryByPath(effectiveQuery),
      graphIndexRepository.findEntryByTitle(effectiveQuery),
      graphIndexRepository.findEntryByAlias(effectiveQuery),
    )
    for (entry in lookups) {
      val allowedEntry = findAllowedEntry(entry.id, scope, branchEntries)
      if (allowedEntry != null) {
        val score = if (allowedEntry.id.value == effectiveQuery) SCORE_EXACT_MATCH else SCORE_METADATA_MATCH
        addOrImproveEntrypoint(
          results,
          allowedEntry,
          depth = 0,
          score = score,
          field = MATCH_FIELD_QUERY,
          maxEntrypoints = maxEntrypoints,
        )
      }
    }
    val nodeIdEntry = parseNodeIdOrNull(effectiveQuery)?.let { findAllowedEntry(it, scope, branchEntries) }
    if (nodeIdEntry != null) {
      addOrImproveEntrypoint(
        results,
        nodeIdEntry,
        depth = 0,
        score = SCORE_EXACT_MATCH,
        field = MATCH_FIELD_QUERY,
        maxEntrypoints = maxEntrypoints,
      )
    }
  }

  private fun addQueryScanEntrypoints(
    rawQuery: String,
    entries: Collection<GraphIndexEntry>,
    results: MutableMap<NodeId, TraversalCandidate>,
    maxEntrypoints: Int,
  ) {
    val effectiveQuery = stripRecencyTriggers(rawQuery).ifEmpty { rawQuery }
    val lowerQuery = effectiveQuery.lowercase()
    for (entry in entries) {
      when {
        entry.id.value == effectiveQuery -> {
          addOrImproveEntrypoint(
            results,
            entry,
            depth = 0,
            score = SCORE_EXACT_MATCH,
            field = MATCH_FIELD_QUERY,
            maxEntrypoints = maxEntrypoints,
          )
        }
        leafOf(entry.id.value) == leafOf(effectiveQuery) -> {
          addOrImproveEntrypoint(
            results,
            entry,
            depth = 0,
            score = SCORE_LEAF_MATCH,
            field = MATCH_FIELD_QUERY,
            maxEntrypoints = maxEntrypoints,
          )
        }
        metadataMatches(entry, lowerQuery) -> {
          addOrImproveEntrypoint(
            results,
            entry,
            depth = 0,
            score = SCORE_METADATA_MATCH,
            field = MATCH_FIELD_METADATA,
            maxEntrypoints = maxEntrypoints,
          )
        }
        branchMatches(entry, lowerQuery) -> {
          addOrImproveEntrypoint(
            results,
            entry,
            depth = 0,
            score = SCORE_BRANCH_MATCH,
            field = MATCH_FIELD_BRANCH,
            maxEntrypoints = maxEntrypoints,
          )
        }
      }
    }
  }

  private suspend fun expandTraversal(
    query: TraverseGraphQuery,
    scope: GraphTraversalRequestScope,
    branchEntries: Map<NodeId, GraphIndexEntry>,
    backlinkSourcesByTarget: Map<NodeId, List<GraphIndexEntry>>,
    entrypoints: List<TraversalCandidate>,
  ): TraversalState {
    val frontier = TraversalFrontier(
      candidates = LinkedHashMap(),
      queue = ArrayDeque(),
      edges = LinkedHashMap(),
    )
    val expansionBudget = expansionBudget(query)
    for (entrypoint in entrypoints) {
      if (!expansionBudget.canAddCandidate(frontier.candidates.size)) break
      frontier.candidates[entrypoint.entry.id] = entrypoint
      frontier.queue.add(entrypoint.entry.id)
    }
    val context = TraversalExpansionContext(
      query = query,
      scope = scope,
      branchEntries = branchEntries,
      expansionBudget = expansionBudget,
      frontier = frontier,
    )
    while (frontier.queue.isNotEmpty()) {
      if (!expansionBudget.canAddCandidate(frontier.candidates.size) &&
        !expansionBudget.canAddEdge(frontier.edges.size)
      ) {
        break
      }
      val currentId = frontier.queue.removeFirst()
      val current = frontier.candidates[currentId]
      if (current != null && current.depth < query.maxDepth.coerceAtLeast(0)) {
        collectForwardLinks(context, current)
        collectBacklinks(context, backlinkSourcesByTarget, current)
      }
    }
    return TraversalState(
      entrypoints = entrypoints.map { TraversalEntrypoint(it.entry.id, it.reason(), it.score) },
      candidates = frontier.candidates,
      edges = frontier.edges.values.toList(),
    )
  }

  private suspend fun collectForwardLinks(
    context: TraversalExpansionContext,
    current: TraversalCandidate,
  ) {
    for (targetId in current.entry.links.filter(::nodeIdAllowed)) {
      val target = findAllowedEntry(targetId, context.scope, context.branchEntries)
      if (target != null && !collectForwardLinkTarget(context, current, target)) return
    }
  }

  private fun collectForwardLinkTarget(
    context: TraversalExpansionContext,
    current: TraversalCandidate,
    target: GraphIndexEntry,
  ): Boolean {
    val type = classifyForwardEdge(current.entry, target)
    if (type !in context.query.edgeTypes) return true
    val hasBudget = hasExpansionBudgetFor(context, target.id)
    if (hasBudget) {
      addEdge(context.frontier.edges, current.entry.id, target.id, type)
      addExpansionCandidate(context, current, target, type)
    }
    return hasBudget
  }

  private fun collectBacklinks(
    context: TraversalExpansionContext,
    backlinkSourcesByTarget: Map<NodeId, List<GraphIndexEntry>>,
    current: TraversalCandidate,
  ) {
    if (TraversalEdgeType.Backlink !in context.query.edgeTypes) return
    for (source in backlinkSourcesByTarget[current.entry.id].orEmpty()) {
      if (!hasExpansionBudgetFor(context, source.id)) return
      addEdge(context.frontier.edges, source.id, current.entry.id, TraversalEdgeType.Backlink)
      addExpansionCandidate(context, current, source, TraversalEdgeType.Backlink)
    }
  }

  private fun addExpansionCandidate(
    context: TraversalExpansionContext,
    current: TraversalCandidate,
    entry: GraphIndexEntry,
    edgeType: TraversalEdgeType,
  ) {
    val frontier = context.frontier
    val score = (current.score - SCORE_DEPTH_DECAY + edgeWeight(edgeType)).coerceAtLeast(0)
    val existing = frontier.candidates[entry.id]
    if (existing == null) {
      if (!context.expansionBudget.canAddCandidate(frontier.candidates.size)) return
      frontier.candidates[entry.id] = TraversalCandidate(
        entry = entry,
        depth = current.depth + 1,
        score = score,
        matchFields = mutableListOf(edgeLabel(edgeType)),
      )
      frontier.queue.add(entry.id)
      return
    }
    if (score > existing.score) existing.score = score
    if (current.depth + 1 < existing.depth) existing.depth = current.depth + 1
    val field = edgeLabel(edgeType)
    if (field !in existing.matchFields) existing.matchFields += field
  }

  private fun hasExpansionBudgetFor(context: TraversalExpansionContext, targetId: NodeId): Boolean {
    val frontier = context.frontier
    val hasCandidateBudget = targetId in frontier.candidates ||
      context.expansionBudget.canAddCandidate(frontier.candidates.size)
    return hasCandidateBudget && context.expansionBudget.canAddEdge(frontier.edges.size)
  }

  private fun applyRanking(query: TraverseGraphQuery, state: TraversalState) {
    val effectiveQuery = stripRecencyTriggers(query.query.trim()).ifEmpty { query.query.trim() }
    val lowerQuery = effectiveQuery.lowercase()
    val recencyRequested = recencyRequested(query)
    for (candidate in state.candidates.values) {
      val boost = exactMatchBoost(candidate.entry, effectiveQuery) +
        subjectHubBoost(candidate.entry) +
        directEvidenceBoost(candidate) +
        recencyBoost(candidate.entry, recencyRequested)
      val penalty = highDegreePenalty(candidate.entry) + unrelatedHubPenalty(candidate.entry, lowerQuery)
      candidate.score = (candidate.score + boost - penalty).coerceAtLeast(0)
    }
  }

  private suspend fun hydrateBodiesIfRequested(query: TraverseGraphQuery, candidates: List<TraversalCandidate>) {
    if (!query.includeBodies) return
    for (candidate in candidates) {
      val node = vaultRepository.findNode(candidate.entry.id)?.takeIf(::nodeAllowed) ?: continue
      candidate.body = node.body
    }
  }

  private fun findAllowedEntry(
    id: NodeId,
    scope: GraphTraversalRequestScope,
    branchEntries: Map<NodeId, GraphIndexEntry>,
  ): GraphIndexEntry? = if (nodeIdAllowed(id)) {
    branchEntries[id]?.takeIf { entryAllowed(it) && entryWithinScope(it, scope) }
  } else {
    null
  }

  private fun buildBacklinkSourcesByTarget(
    branchEntries: Map<NodeId, GraphIndexEntry>,
  ): Map<NodeId, List<GraphIndexEntry>> {
    val sourcesByTarget = LinkedHashMap<NodeId, MutableList<GraphIndexEntry>>()
    for (source in branchEntries.values.filter(::entryAllowed)) {
      source.links.asSequence()
        .filter(::nodeIdAllowed)
        .mapNotNull { targetId -> branchEntries[targetId]?.takeIf(::entryAllowed) }
        .forEach { target -> sourcesByTarget.getOrPut(target.id) { mutableListOf() } += source }
    }
    return sourcesByTarget
  }

  private fun shouldCollectBacklinks(query: TraverseGraphQuery): Boolean {
    val backlinksRequested = TraversalEdgeType.Backlink in query.edgeTypes
    return backlinksRequested && query.maxDepth > 0
  }

  private fun parseNodeIdOrNull(raw: String): NodeId? = try {
    NodeId(raw)
  } catch (_: IllegalArgumentException) {
    null
  }

  companion object {
    private const val MATCH_FIELD_START_ID: String = "start_id"
    private const val MATCH_FIELD_QUERY: String = "query"
    private const val MATCH_FIELD_METADATA: String = "metadata"
    private const val MATCH_FIELD_BRANCH: String = "branch"
    private const val SCORE_EXACT_MATCH: Int = 100
    private const val SCORE_START_ID: Int = 95
    private const val SCORE_LEAF_MATCH: Int = 80
    private const val SCORE_METADATA_MATCH: Int = 60
    private const val SCORE_BRANCH_MATCH: Int = 40
    private const val SCORE_DEPTH_DECAY: Int = 12
  }
}
