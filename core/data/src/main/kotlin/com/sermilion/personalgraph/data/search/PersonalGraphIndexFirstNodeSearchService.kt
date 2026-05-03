package com.sermilion.personalgraph.data.search

import com.sermilion.personalgraph.common.di.AppScope
import com.sermilion.personalgraph.common.dispatcher.DispatcherProvider
import com.sermilion.personalgraph.domain.graph.GraphIndexEntry
import com.sermilion.personalgraph.domain.layout.VaultLayout
import com.sermilion.personalgraph.domain.layout.VaultPolicy
import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.repository.GraphIndexRepository
import com.sermilion.personalgraph.domain.repository.VaultRepository
import com.sermilion.personalgraph.domain.search.NodeSearchService
import com.sermilion.personalgraph.domain.search.SearchField
import com.sermilion.personalgraph.domain.search.SearchHit
import com.sermilion.personalgraph.domain.search.SearchOutcome
import com.sermilion.personalgraph.domain.search.SearchPlan
import com.sermilion.personalgraph.domain.search.SearchQuery
import com.sermilion.personalgraph.domain.search.SearchRankingTier
import com.sermilion.personalgraph.domain.search.SearchRecency
import com.sermilion.personalgraph.domain.tokens.TokenEstimator
import kotlinx.coroutines.withContext
import me.tatarka.inject.annotations.Inject

@AppScope
@Inject
class PersonalGraphIndexFirstNodeSearchService(
  private val graphIndexRepository: GraphIndexRepository,
  private val vaultRepository: VaultRepository,
  private val tokenEstimator: TokenEstimator,
  private val dispatcherProvider: DispatcherProvider,
) : NodeSearchService {

  override suspend fun search(query: SearchQuery): SearchOutcome = withContext(dispatcherProvider.io) {
    val normalized = query.query.trim()
    if (normalized.isEmpty()) {
      return@withContext emptyOutcome()
    }
    val branches = effectiveBranches(query.branches).filter(::branchAllowed)
    val candidates = collectCandidates(branches)
    val matches = matchCandidates(normalized, candidates, query)
    val withBodyFallback = applyBodyFallback(normalized, candidates, matches, query)
    val ranked = withBodyFallback.values
      .sortedWith(compareByDescending<MatchAccumulator> { it.score }.thenBy { it.entry.id.value })
      .take(query.limit)
    val hits = ranked.map { buildHit(it, query) }
    val bodyFallbackUsed = withBodyFallback.values.any { it.bodyMatched }
    val plan = SearchPlan(
      metadataIndexUsed = true,
      bodyFallbackUsed = bodyFallbackUsed,
      branchesSearched = branches,
    )
    SearchOutcome(
      hits = hits,
      plan = plan,
      estimatedTokens = estimateTokens(hits),
    )
  }

  private fun emptyOutcome(): SearchOutcome = SearchOutcome(
    hits = emptyList(),
    plan = SearchPlan(metadataIndexUsed = true, bodyFallbackUsed = false, branchesSearched = emptyList()),
    estimatedTokens = 0,
  )

  private fun effectiveBranches(requested: List<String>): List<String> {
    if (requested.isNotEmpty()) return requested.map { it.trim('/') }.filter { it.isNotEmpty() }
    return DEFAULT_SEARCH_BRANCHES
  }

  private fun branchAllowed(branch: String): Boolean = idAllowed(branch)

  private suspend fun collectCandidates(branches: List<String>): List<GraphIndexEntry> {
    val seen = LinkedHashMap<NodeId, GraphIndexEntry>()
    for (branch in branches) {
      for (entry in graphIndexRepository.listEntriesInBranch(branch)) {
        if (entryAllowed(entry)) seen.putIfAbsent(entry.id, entry)
      }
    }
    return seen.values.toList()
  }

  private fun entryAllowed(entry: GraphIndexEntry): Boolean {
    val id = entry.id.value
    return !VaultPolicy.isReadBlocked(id) && !VaultPolicy.isIndexExcluded(id)
  }

  private suspend fun matchCandidates(
    query: String,
    candidates: List<GraphIndexEntry>,
    request: SearchQuery,
  ): MutableMap<NodeId, MatchAccumulator> {
    val accumulators = mutableMapOf<NodeId, MatchAccumulator>()
    val lowerQuery = query.lowercase()
    val recencyBoost = if (containsRecencyTrigger(lowerQuery)) SearchRecency.BOOST else 0
    val effectiveQuery = stripRecencyTriggers(query).ifEmpty { query }
    val effectiveLower = effectiveQuery.lowercase()
    val idLookupEnabled = SearchField.Id in request.searchFields
    val metadataEnabled = SearchField.Metadata in request.searchFields
    if (idLookupEnabled) addIdLookupMatches(effectiveQuery, candidates, accumulators)
    if (metadataEnabled) addMetadataMatches(effectiveQuery, effectiveLower, candidates, accumulators)
    if (recencyBoost > 0) {
      for (acc in accumulators.values) acc.score += recencyBoost
    }
    return accumulators
  }

  private fun stripRecencyTriggers(query: String): String {
    val tokens = query.split(' ').filter { token ->
      val lower = token.lowercase()
      lower.isNotEmpty() && SearchRecency.TRIGGERS.none { it == lower }
    }
    return tokens.joinToString(" ")
  }

  private suspend fun addIdLookupMatches(
    rawQuery: String,
    candidates: List<GraphIndexEntry>,
    accumulators: MutableMap<NodeId, MatchAccumulator>,
  ) {
    val pathHit = graphIndexRepository.findEntryByPath(rawQuery)
    if (pathHit != null && entryAllowed(pathHit)) {
      addMatch(accumulators, pathHit, SearchRankingTier.ExactFullIdOrPath, MATCH_FIELD_ID)
    }
    val candidateById = candidates.firstOrNull { it.id.value == rawQuery }
    if (candidateById != null) {
      addMatch(accumulators, candidateById, SearchRankingTier.ExactFullIdOrPath, MATCH_FIELD_ID)
    }
    val queryLeaf = leafOf(rawQuery)
    for (entry in candidates) {
      val leaf = leafOf(entry.id.value)
      if (leaf.isNotEmpty() && leaf == queryLeaf) {
        addMatch(accumulators, entry, SearchRankingTier.LeafIdOrSlug, MATCH_FIELD_ID)
      }
    }
  }

  private suspend fun addMetadataMatches(
    rawQuery: String,
    lowerQuery: String,
    candidates: List<GraphIndexEntry>,
    accumulators: MutableMap<NodeId, MatchAccumulator>,
  ) {
    val titleHit = graphIndexRepository.findEntryByTitle(rawQuery)
    if (titleHit != null && entryAllowed(titleHit)) {
      addMatch(accumulators, titleHit, SearchRankingTier.SubjectTopicAliasHypothesis, MATCH_FIELD_TITLE)
    }
    val aliasHit = graphIndexRepository.findEntryByAlias(rawQuery)
    if (aliasHit != null && entryAllowed(aliasHit)) {
      addMatch(accumulators, aliasHit, SearchRankingTier.SubjectTopicAliasHypothesis, MATCH_FIELD_ALIAS)
    }
    for (entry in candidates) {
      addMetadataMatchesForEntry(lowerQuery, entry, accumulators)
    }
  }

  private fun addMetadataMatchesForEntry(
    lowerQuery: String,
    entry: GraphIndexEntry,
    accumulators: MutableMap<NodeId, MatchAccumulator>,
  ) {
    if (entry.subject?.contains(lowerQuery, ignoreCase = true) == true) {
      addMatch(accumulators, entry, SearchRankingTier.SubjectTopicAliasHypothesis, MATCH_FIELD_SUBJECT)
    }
    if (entry.topic?.contains(lowerQuery, ignoreCase = true) == true) {
      addMatch(accumulators, entry, SearchRankingTier.SubjectTopicAliasHypothesis, MATCH_FIELD_TOPIC)
    }
    if (entry.aliases.any { it.contains(lowerQuery, ignoreCase = true) }) {
      addMatch(accumulators, entry, SearchRankingTier.SubjectTopicAliasHypothesis, MATCH_FIELD_ALIAS)
    }
    if (entry.hypothesis?.contains(lowerQuery, ignoreCase = true) == true) {
      addMatch(accumulators, entry, SearchRankingTier.SubjectTopicAliasHypothesis, MATCH_FIELD_HYPOTHESIS)
    }
    if (entry.domain?.contains(lowerQuery, ignoreCase = true) == true) {
      addMatch(accumulators, entry, SearchRankingTier.DomainOrBranchRelevance, MATCH_FIELD_DOMAIN)
    }
    if (entry.branch.contains(lowerQuery, ignoreCase = true)) {
      addMatch(accumulators, entry, SearchRankingTier.DomainOrBranchRelevance, MATCH_FIELD_BRANCH)
    }
  }

  private suspend fun applyBodyFallback(
    rawQuery: String,
    candidates: List<GraphIndexEntry>,
    matches: MutableMap<NodeId, MatchAccumulator>,
    request: SearchQuery,
  ): MutableMap<NodeId, MatchAccumulator> {
    val bodyFieldRequested = SearchField.Body in request.searchFields
    if (!bodyFieldRequested || !request.bodyFallback) return matches
    if (matches.isNotEmpty()) return matches
    for (entry in candidates) {
      val node = vaultRepository.findNode(entry.id) ?: continue
      if (node.body.contains(rawQuery, ignoreCase = true)) {
        val acc = addMatch(matches, entry, SearchRankingTier.BodyMention, MATCH_FIELD_BODY)
        acc.bodyMatched = true
        acc.body = node.body
      }
    }
    return matches
  }

  private fun addMatch(
    accumulators: MutableMap<NodeId, MatchAccumulator>,
    entry: GraphIndexEntry,
    tier: SearchRankingTier,
    field: String,
  ): MatchAccumulator {
    val existing = accumulators[entry.id]
    if (existing != null) {
      if (tier.score > existing.score) existing.score = tier.score
      if (field !in existing.matchFields) existing.matchFields += field
      return existing
    }
    val created = MatchAccumulator(
      entry = entry,
      score = tier.score,
      matchFields = mutableListOf(field),
    )
    accumulators[entry.id] = created
    return created
  }

  private fun containsRecencyTrigger(query: String): Boolean = SearchRecency.TRIGGERS.any { query.contains(it) }

  private fun leafOf(id: String): String = id.substringAfterLast('/')

  private fun buildHit(acc: MatchAccumulator, query: SearchQuery): SearchHit {
    val entry = acc.entry
    val filteredLinks = entry.links.filter { branchAllowed(it.value) && entryLinkAllowed(it) }
    return SearchHit(
      id = entry.id,
      type = entry.type,
      domain = entry.domain,
      subject = entry.subject ?: entry.topic ?: entry.category,
      matchFields = acc.matchFields.toList(),
      snippet = entry.snippet,
      links = filteredLinks,
      score = acc.score,
      body = if (query.includeBody) acc.body else null,
    )
  }

  private fun entryLinkAllowed(link: NodeId): Boolean = idAllowed(link.value)

  private fun idAllowed(id: String): Boolean = !VaultPolicy.isReadBlocked(id) && !VaultPolicy.isIndexExcluded(id)

  private fun estimateTokens(hits: List<SearchHit>): Int {
    var total = 0
    for (hit in hits) {
      total += tokenEstimator.estimateString(hit.id.value)
      total += tokenEstimator.estimateString(hit.snippet)
      hit.subject?.let { total += tokenEstimator.estimateString(it) }
      hit.domain?.let { total += tokenEstimator.estimateString(it) }
      hit.body?.let { total += tokenEstimator.estimateBody(it) }
    }
    return total
  }

  private data class MatchAccumulator(
    val entry: GraphIndexEntry,
    var score: Int,
    val matchFields: MutableList<String>,
    var bodyMatched: Boolean = false,
    var body: String? = null,
  )

  companion object {
    const val MATCH_FIELD_ID: String = "id"
    const val MATCH_FIELD_TITLE: String = "title"
    const val MATCH_FIELD_SUBJECT: String = "subject"
    const val MATCH_FIELD_TOPIC: String = "topic"
    const val MATCH_FIELD_ALIAS: String = "alias"
    const val MATCH_FIELD_HYPOTHESIS: String = "hypothesis"
    const val MATCH_FIELD_DOMAIN: String = "domain"
    const val MATCH_FIELD_BRANCH: String = "branch"
    const val MATCH_FIELD_BODY: String = "body"

    val DEFAULT_SEARCH_BRANCHES: List<String> = listOf(
      VaultLayout.BRANCH_STATE,
      VaultLayout.BRANCH_DOMAINS,
      VaultLayout.BRANCH_PATTERNS,
      VaultLayout.BRANCH_EMOTIONAL_STATES,
      VaultLayout.BRANCH_TIMELINE,
      VaultLayout.BRANCH_STAGING_OBSERVATIONS,
      VaultLayout.BRANCH_OUTDATED,
    )
  }
}
