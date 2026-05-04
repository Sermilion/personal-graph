package com.sermilion.personalgraph.data.search

import com.sermilion.personalgraph.common.di.AppScope
import com.sermilion.personalgraph.common.dispatcher.DispatcherProvider
import com.sermilion.personalgraph.domain.graph.GraphIndexEntry
import com.sermilion.personalgraph.domain.layout.VaultPolicy
import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.model.VaultNode
import com.sermilion.personalgraph.domain.repository.GraphIndexRepository
import com.sermilion.personalgraph.domain.repository.VaultRepository
import com.sermilion.personalgraph.domain.search.BranchListEntry
import com.sermilion.personalgraph.domain.search.BranchListMode
import com.sermilion.personalgraph.domain.search.BranchListOutcome
import com.sermilion.personalgraph.domain.search.BranchListQuery
import com.sermilion.personalgraph.domain.search.BranchListTokenAccounting
import com.sermilion.personalgraph.domain.search.BranchListingService
import com.sermilion.personalgraph.domain.tokens.TokenEstimator
import kotlinx.coroutines.withContext
import me.tatarka.inject.annotations.Inject

@AppScope
@Inject
class PersonalGraphBranchListingService(
  private val vaultRepository: VaultRepository,
  private val graphIndexRepository: GraphIndexRepository,
  private val tokenEstimator: TokenEstimator,
  private val dispatcherProvider: DispatcherProvider,
) : BranchListingService {

  override suspend fun list(query: BranchListQuery): BranchListOutcome = withContext(dispatcherProvider.io) {
    when (query.mode) {
      BranchListMode.Full -> listFull(query)
      BranchListMode.Index -> listIndex(query)
    }
  }

  private suspend fun listFull(query: BranchListQuery): BranchListOutcome.Full {
    val raw = vaultRepository.listNodesInBranch(query.branch)
    val allowed = raw.filter(::nodeAllowed)
    val filtered = applyFilter(allowed, query.filter) { it.id.value }
    val limited = applyLimit(filtered, query.limit)
    val accounting = BranchListTokenAccounting(
      metadataTokens = limited.sumOf { tokenEstimator.estimateMetadata(it.id.value) },
      bodyTokens = limited.sumOf { tokenEstimator.estimateBody(it.body) },
      prunedBodyTokens = 0,
    )
    return BranchListOutcome.Full(
      mode = BranchListMode.Full,
      nodes = limited,
      estimatedTokens = accounting,
    )
  }

  private suspend fun listIndex(query: BranchListQuery): BranchListOutcome.Index {
    val raw = graphIndexRepository.listEntriesInBranch(query.branch)
    val allowed = raw.filter(::indexEntryAllowed)
    val filtered = applyFilter(allowed, query.filter) { it.id.value }
    val limited = applyLimit(filtered, query.limit)
    val entries = limited.map { toCompactEntry(it, query.includeLinks) }
    val accounting = computeIndexAccounting(limited)
    return BranchListOutcome.Index(
      mode = BranchListMode.Index,
      entries = entries,
      estimatedTokens = accounting,
    )
  }

  private fun toCompactEntry(entry: GraphIndexEntry, includeLinks: Boolean): BranchListEntry = BranchListEntry(
    id = entry.id,
    type = entry.type,
    domain = entry.domain,
    subject = entry.subject ?: entry.topic ?: entry.category,
    snippet = entry.snippet,
    matchFields = emptyList(),
    score = 0,
    links = if (includeLinks) entry.links.filter(::nodeIdAllowed) else emptyList(),
  )

  private fun computeIndexAccounting(entries: List<GraphIndexEntry>): BranchListTokenAccounting {
    val metadata = tokenEstimator.estimateEntries(entries)
    val pruned = entries.sumOf { it.bodyTokenEstimate }
    val metadataOnly = (metadata - pruned).coerceAtLeast(0)
    return BranchListTokenAccounting(
      metadataTokens = metadataOnly,
      bodyTokens = 0,
      prunedBodyTokens = pruned,
    )
  }

  private fun <T> applyFilter(items: List<T>, filter: String?, idOf: (T) -> String): List<T> {
    if (filter == null) return items
    return items.filter { idOf(it).contains(filter) }
  }

  private fun <T> applyLimit(items: List<T>, limit: Int?): List<T> = if (limit != null) items.take(limit) else items

  private fun nodeAllowed(node: VaultNode): Boolean = idAllowed(node.id.value)

  private fun indexEntryAllowed(entry: GraphIndexEntry): Boolean = idAllowed(entry.id.value)

  private fun nodeIdAllowed(id: NodeId): Boolean = idAllowed(id.value)

  private fun idAllowed(raw: String): Boolean = !VaultPolicy.isReadBlocked(raw) && !VaultPolicy.isIndexExcluded(raw)
}
