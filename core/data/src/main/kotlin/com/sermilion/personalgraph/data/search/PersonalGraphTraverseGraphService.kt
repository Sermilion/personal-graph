package com.sermilion.personalgraph.data.search

import com.sermilion.personalgraph.common.di.AppScope
import com.sermilion.personalgraph.common.dispatcher.DispatcherProvider
import com.sermilion.personalgraph.domain.graph.GraphIndexEntry
import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.repository.GraphIndexRepository
import com.sermilion.personalgraph.domain.repository.VaultRepository
import com.sermilion.personalgraph.domain.search.TraversalEdge
import com.sermilion.personalgraph.domain.search.TraversalEdgeType
import com.sermilion.personalgraph.domain.search.TraversalEntrypoint
import com.sermilion.personalgraph.domain.search.TraversalNode
import com.sermilion.personalgraph.domain.search.TraversalPrunedCandidate
import com.sermilion.personalgraph.domain.search.TraversalSuggestedRead
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
    val expansionBudget = expansionBudget(query)
    if (expansionBudget.maxCandidates == 0 || query.budgetTokens <= 0) {
      return@withContext TraverseGraphOutcome(
        entrypoints = emptyList(),
        nodes = emptyList(),
        edges = emptyList(),
        pruned = emptyList(),
        suggestedReads = emptyList(),
        estimatedTokens = 0,
      )
    }
    val branchEntries = LinkedHashMap<NodeId, GraphIndexEntry>()
    val entrypoints = collectEntrypoints(
      rawQuery = query.query.trim(),
      scope = scope,
      branchEntries = branchEntries,
      maxEntrypoints = expansionBudget.maxCandidates,
    )
    val state = expandTraversal(query, scope, branchEntries, entrypoints)
    applyRanking(query, state)
    val ranked = state.candidates.values
      .sortedWith(candidateComparator(query))
    val selectionBuilder = TraversalSelectionBuilder(
      tokenEstimator = tokenEstimator,
      query = query,
      allEdges = state.edges,
      baseTokenEstimate = estimateEntrypoints(tokenEstimator, state.entrypoints),
    )
    var selection = if (query.includeBodies) {
      selectionBuilder.selectWithBodyHydration(ranked, ::hydrateBody)
    } else {
      selectionBuilder.select(ranked)
    }
    selection = selectionBuilder.trimToBudget(selection)
    val nodes = selection.included.map(::toNode)
    val returnedIds = nodes.mapTo(mutableSetOf()) { it.id }
    val edges = state.edges.filter { it.from in returnedIds && it.to in returnedIds }
    budgetedOutcome(
      query = query,
      entrypoints = state.entrypoints,
      nodes = nodes,
      edges = edges,
      pruned = selection.pruned,
    )
  }

  private suspend fun collectBranchEntries(
    scope: GraphTraversalRequestScope,
    entries: LinkedHashMap<NodeId, GraphIndexEntry>,
  ) {
    for (branch in scope.branches) {
      for (entry in graphIndexRepository.listEntriesInBranch(branch)) {
        if (entryAllowed(entry)) entries.putIfAbsent(entry.id, entry)
      }
    }
  }

  private suspend fun collectEntrypoints(
    rawQuery: String,
    scope: GraphTraversalRequestScope,
    branchEntries: LinkedHashMap<NodeId, GraphIndexEntry>,
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
      val scanNeeded = addQueryLookupEntrypoints(rawQuery, scope, branchEntries, results, maxEntrypoints)
      if (scanNeeded) {
        collectBranchEntries(scope, branchEntries)
        val retryScanNeeded = addQueryLookupEntrypoints(rawQuery, scope, branchEntries, results, maxEntrypoints)
        if (retryScanNeeded && results.size < maxEntrypoints) {
          addQueryScanEntrypoints(rawQuery, branchEntries.values, results, maxEntrypoints)
        }
      }
    }
    return results.values.toList()
  }

  private suspend fun addQueryLookupEntrypoints(
    rawQuery: String,
    scope: GraphTraversalRequestScope,
    branchEntries: LinkedHashMap<NodeId, GraphIndexEntry>,
    results: MutableMap<NodeId, TraversalCandidate>,
    maxEntrypoints: Int,
  ): Boolean {
    val effectiveQuery = stripRecencyTriggers(rawQuery).ifEmpty { rawQuery }
    var exactLookupResolved = false
    val lookups = listOfNotNull(
      graphIndexRepository.findEntryByPath(effectiveQuery)?.let { PathLookup(it) },
      graphIndexRepository.findEntryByTitle(effectiveQuery)?.let { MetadataLookup(it) },
      graphIndexRepository.findEntryByAlias(effectiveQuery)?.let { MetadataLookup(it) },
    )
    for (lookup in lookups) {
      val entry = lookup.entry
      val allowedEntry = findAllowedEntry(entry.id, scope, branchEntries)
      if (allowedEntry != null) {
        val exactLookup = lookup is PathLookup || allowedEntry.id.value == effectiveQuery
        val score = if (exactLookup) SCORE_EXACT_MATCH else SCORE_METADATA_MATCH
        exactLookupResolved = exactLookupResolved || exactLookup
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
      exactLookupResolved = true
      addOrImproveEntrypoint(
        results,
        nodeIdEntry,
        depth = 0,
        score = SCORE_EXACT_MATCH,
        field = MATCH_FIELD_QUERY,
        maxEntrypoints = maxEntrypoints,
      )
    }
    return !exactLookupResolved
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
    branchEntries: LinkedHashMap<NodeId, GraphIndexEntry>,
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
        collectBacklinks(context, current)
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
    if (!context.expansionBudget.canAddEdge(context.frontier.edges.size)) return false
    val canAddTarget = target.id in context.frontier.candidates ||
      context.expansionBudget.canAddCandidate(context.frontier.candidates.size)
    if (canAddTarget) {
      addEdge(context.frontier.edges, current.entry.id, target.id, type)
      addExpansionCandidate(context, current, target, type)
    }
    return true
  }

  private suspend fun collectBacklinks(
    context: TraversalExpansionContext,
    current: TraversalCandidate,
  ) {
    if (TraversalEdgeType.Backlink !in context.query.edgeTypes) return
    if (!context.expansionBudget.canAddEdge(context.frontier.edges.size)) return
    ensureScopedBranchEntriesWarmed(context)
    val seen = mutableSetOf<NodeId>()
    for (source in backlinkSourcesByTarget(context)[current.entry.id].orEmpty()) {
      if (!collectBacklinkSource(context, current, source, seen)) return
    }
  }

  private suspend fun ensureScopedBranchEntriesWarmed(context: TraversalExpansionContext) {
    if (context.scopedBranchesWarmed) return
    collectBranchEntries(context.scope, context.branchEntries)
    context.scopedBranchesWarmed = true
  }

  private fun collectBacklinkSource(
    context: TraversalExpansionContext,
    current: TraversalCandidate,
    source: GraphIndexEntry,
    seen: MutableSet<NodeId>,
  ): Boolean {
    if (!seen.add(source.id)) return true
    if (!context.expansionBudget.canAddEdge(context.frontier.edges.size)) return false
    val canAddSource = source.id in context.frontier.candidates ||
      context.expansionBudget.canAddCandidate(context.frontier.candidates.size)
    if (canAddSource) {
      addEdge(context.frontier.edges, source.id, current.entry.id, TraversalEdgeType.Backlink)
      addExpansionCandidate(context, current, source, TraversalEdgeType.Backlink)
    }
    return true
  }

  private fun backlinkSourcesByTarget(
    context: TraversalExpansionContext,
  ): Map<NodeId, List<GraphIndexEntry>> {
    val warmedEntryCount = context.branchEntries.size
    val existing = context.backlinkSourcesByTarget
    if (existing != null && context.backlinkSourceEntryCount == warmedEntryCount) return existing

    val sourcesByTarget = LinkedHashMap<NodeId, MutableList<GraphIndexEntry>>()
    for (source in context.branchEntries.values) {
      if (!entryAllowed(source) || !entryWithinScope(source, context.scope)) continue
      for (targetId in source.links.filter(::nodeIdAllowed)) {
        sourcesByTarget.getOrPut(targetId) { mutableListOf() } += source
      }
    }
    val built = sourcesByTarget.mapValues { (_, sources) -> sources.toList() }
    context.backlinkSourcesByTarget = built
    context.backlinkSourceEntryCount = warmedEntryCount
    return built
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

  private suspend fun hydrateBody(candidate: TraversalCandidate) {
    val node = vaultRepository.findNode(candidate.entry.id)?.takeIf(::nodeAllowed) ?: return
    candidate.body = node.body
  }

  private suspend fun findAllowedEntry(
    id: NodeId,
    scope: GraphTraversalRequestScope,
    branchEntries: LinkedHashMap<NodeId, GraphIndexEntry>,
  ): GraphIndexEntry? {
    if (!nodeIdAllowed(id)) return null
    val cached = branchEntries[id]
    val entry = cached ?: graphIndexRepository.findEntry(id)
    val allowed = entry?.takeIf { entryAllowed(it) && entryWithinScope(it, scope) }
    if (allowed != null) branchEntries.putIfAbsent(allowed.id, allowed)
    return allowed
  }

  private fun budgetedOutcome(
    query: TraverseGraphQuery,
    entrypoints: List<TraversalEntrypoint>,
    nodes: List<TraversalNode>,
    edges: List<TraversalEdge>,
    pruned: List<TraversalPrunedCandidate>,
  ): TraverseGraphOutcome {
    val budgetTokens = query.budgetTokens.coerceAtLeast(0)
    val finalEntrypoints = entrypoints.toMutableList()
    val finalNodes = nodes.toMutableList()
    val finalEdges = edges.toMutableList()
    val finalPruned = pruned.toMutableList()
    var suggestedLimit = Int.MAX_VALUE

    fun currentSuggested(): List<TraversalSuggestedRead> = suggestedReads(finalPruned, suggestedLimit)
    fun currentEstimate(): Int = estimateTokens(
      tokenEstimator = tokenEstimator,
      entrypoints = finalEntrypoints,
      nodes = finalNodes,
      edges = finalEdges,
      pruned = finalPruned,
      suggestedReads = currentSuggested(),
    )

    while (currentEstimate() > budgetTokens) {
      when {
        currentSuggested().isNotEmpty() -> suggestedLimit = currentSuggested().size - 1
        finalPruned.isNotEmpty() -> {
          finalPruned.removeAt(finalPruned.lastIndex)
          suggestedLimit = Int.MAX_VALUE
        }
        finalEdges.isNotEmpty() -> finalEdges.removeAt(finalEdges.lastIndex)
        finalNodes.isNotEmpty() -> {
          val removed = finalNodes.removeAt(finalNodes.lastIndex)
          finalEdges.removeAll { it.from == removed.id || it.to == removed.id }
        }
        finalEntrypoints.isNotEmpty() -> finalEntrypoints.removeAt(finalEntrypoints.lastIndex)
        else -> break
      }
    }

    val finalSuggested = currentSuggested()
    return TraverseGraphOutcome(
      entrypoints = finalEntrypoints,
      nodes = finalNodes,
      edges = finalEdges,
      pruned = finalPruned,
      suggestedReads = finalSuggested,
      estimatedTokens = estimateTokens(
        tokenEstimator = tokenEstimator,
        entrypoints = finalEntrypoints,
        nodes = finalNodes,
        edges = finalEdges,
        pruned = finalPruned,
        suggestedReads = finalSuggested,
      ),
    )
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
