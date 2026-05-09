package com.sermilion.personalgraph.data.retrieval

import com.sermilion.personalgraph.domain.layout.VaultLayout
import com.sermilion.personalgraph.domain.model.StateNode
import com.sermilion.personalgraph.domain.model.VaultNode
import com.sermilion.personalgraph.domain.retrieval.CompactMapEntry
import com.sermilion.personalgraph.domain.retrieval.CompactMapEntryKind
import com.sermilion.personalgraph.domain.retrieval.FullBodyContextSource
import com.sermilion.personalgraph.domain.retrieval.LoadedFullBodyContext
import com.sermilion.personalgraph.domain.retrieval.RetrievalAuditEntry
import com.sermilion.personalgraph.domain.retrieval.RetrievalClassification
import com.sermilion.personalgraph.domain.retrieval.RetrievalDomain
import com.sermilion.personalgraph.domain.retrieval.RetrievedBranch
import com.sermilion.personalgraph.domain.retrieval.RetrievedNode
import com.sermilion.personalgraph.domain.retrieval.RetrievedRootDocument
import com.sermilion.personalgraph.domain.retrieval.SessionStartRetrievalMode
import com.sermilion.personalgraph.domain.retrieval.SuggestedRead

private const val MAX_AVAILABLE_MAP_ENTRIES: Int = 80
private const val MAX_SUGGESTED_READS: Int = 8

internal fun VaultNode.isVisibleInStateBranch(
  branch: String,
  domain: RetrievalDomain,
): Boolean = !branch.startsWith("${VaultLayout.BRANCH_STATE}/") ||
  (this as? StateNode)?.isVisibleForRetrievalDomain(domain) != false

internal fun loadedContext(
  rootDocument: RetrievedRootDocument?,
  loadedNodes: List<RetrievedNode>,
  retrievalMode: SessionStartRetrievalMode,
  audit: MutableList<RetrievalAuditEntry>,
): List<LoadedFullBodyContext> = buildList {
  if (rootDocument != null) add(rootDocument.toLoadedContext())
  if (retrievalMode != SessionStartRetrievalMode.FullLoading) return@buildList
  for (node in loadedNodes) {
    audit.add(
      RetrievalAuditEntry(
        action = "loaded_full_body",
        subject = node.id,
        reason = "explicit full-loading retrieval mode requested",
      ),
    )
    add(node.toLoadedContext())
  }
}

internal fun availableMap(
  loadedBranches: List<RetrievedBranch>,
  loadedNodes: List<RetrievedNode>,
  classification: RetrievalClassification,
  relevanceTerms: Set<String>,
): List<CompactMapEntry> = buildList {
  loadedBranches.mapTo(this) { it.toMapEntry() }
  loadedNodes
    .map { it.toMapEntry() }
    .sortedWith(
      compareByDescending<CompactMapEntry> { it.mapBudgetScore(classification.domain, relevanceTerms) }
        .thenBy { it.id },
    )
    .take(maxOf(0, MAX_AVAILABLE_MAP_ENTRIES - loadedBranches.size))
    .forEach(::add)
}

internal fun suggestedReads(
  availableMap: List<CompactMapEntry>,
  classification: RetrievalClassification,
  relevanceTerms: Set<String>,
  audit: MutableList<RetrievalAuditEntry>,
): List<SuggestedRead> {
  val sortedEligible = availableMap
    .filter { it.kind == CompactMapEntryKind.Node }
    .filter { it.isSuggestedFor(classification.domain) }
    .sortedWith(
      compareByDescending<CompactMapEntry> { it.suggestionScore(classification.domain, relevanceTerms) }
        .thenBy { it.id },
    )
  val pinned = sortedEligible
    .firstOrNull { it.type == "subject" && it.domain == classification.domain.value }
    .plusIfPresent(
      sortedEligible.firstOrNull {
        it.type == "state" &&
          (it.scope == classification.domain.value || classification.domain.value in it.scopes)
      },
    )
  val pinnedIds = pinned.mapTo(mutableSetOf()) { it.id }
  val reads = (pinned + sortedEligible.filterNot { it.id in pinnedIds })
    .take(MAX_SUGGESTED_READS)
    .map { entry -> entry.toSuggestedRead(classification.domain, audit) }
  if (reads.isEmpty()) audit.addNoSuggestedRead(classification.domain)
  return reads
}

private fun CompactMapEntry?.plusIfPresent(other: CompactMapEntry?): List<CompactMapEntry> = listOfNotNull(this, other)

private fun StateNode.isVisibleForRetrievalDomain(domain: RetrievalDomain): Boolean {
  val hasNoScope = scope == null && scopes.isEmpty()
  val matchesDomain = domain != RetrievalDomain.General &&
    (scope == domain.value || domain.value in scopes)
  return hasNoScope || matchesDomain
}

private fun RetrievedRootDocument.toLoadedContext(): LoadedFullBodyContext = LoadedFullBodyContext(
  id = path,
  body = body,
  source = FullBodyContextSource.Root,
  loadOrder = loadOrder,
  reason = reason,
)

private fun RetrievedNode.toLoadedContext(): LoadedFullBodyContext = LoadedFullBodyContext(
  id = id,
  body = body,
  source = FullBodyContextSource.Node,
  loadOrder = loadOrder,
  reason = reason,
)

internal fun RetrievedBranch.toMapEntry(): CompactMapEntry = CompactMapEntry(
  id = branch,
  kind = CompactMapEntryKind.Branch,
  reason = reason,
  nodeCount = nodeCount,
  type = "branch",
  summary = "Branch available for follow-up list_branch reads.",
)

private fun RetrievedNode.toMapEntry(): CompactMapEntry {
  val safeLinks = safeMapLinks()
  return CompactMapEntry(
    id = id,
    kind = CompactMapEntryKind.Node,
    reason = reason,
    type = type,
    category = category,
    domain = domain,
    scope = scope,
    scopes = scopes,
    updated = updated,
    date = date,
    summary = summary,
    aliases = aliases,
    linkCount = safeLinks.size,
    links = safeLinks,
  )
}

private fun RetrievedNode.safeMapLinks(): List<String> = (links + patternLinks)
  .distinct()
  .filterNot { it.isDefaultBlockedMapLink() }
