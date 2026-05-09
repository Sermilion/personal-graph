package com.sermilion.personalgraph.data.retrieval

import com.sermilion.personalgraph.domain.graph.GraphIndexEntry
import com.sermilion.personalgraph.domain.layout.VaultLayout
import com.sermilion.personalgraph.domain.retrieval.CompactMapEntry
import com.sermilion.personalgraph.domain.retrieval.CompactMapEntryKind
import com.sermilion.personalgraph.domain.retrieval.RetrievalClassification
import com.sermilion.personalgraph.domain.retrieval.RetrievalDomain
import com.sermilion.personalgraph.domain.retrieval.RetrievedBranch

private const val MAX_AVAILABLE_INDEX_MAP_ENTRIES: Int = 80
private const val MAX_AVAILABLE_MAP_ENTRIES_PER_BRANCH: Int = 20
private const val MAX_RESERVED_GLOBAL_PREFERENCES_PER_BRANCH: Int = 4
private const val TYPE_STATE: String = "state"
private const val CATEGORY_PREFERENCE: String = "preference"

internal data class SessionStartIndexMapEntry(
  val entry: GraphIndexEntry,
  val reason: String,
  val plannedBranch: String,
)

internal fun GraphIndexEntry.isVisibleInStateBranch(
  branch: String,
  domain: RetrievalDomain,
): Boolean = !branch.startsWith("${VaultLayout.BRANCH_STATE}/") ||
  isVisibleForRetrievalDomain(domain)

internal fun availableMapFromIndex(
  loadedBranches: List<RetrievedBranch>,
  indexedNodes: List<SessionStartIndexMapEntry>,
  classification: RetrievalClassification,
  relevanceTerms: Set<String>,
): List<CompactMapEntry> = buildList {
  loadedBranches.mapTo(this) { it.toMapEntry() }
  indexedNodes
    .map { it.toMapEntry() }
    .topEntriesPerBranch(classification, relevanceTerms)
    .sortedForMapBudget(classification, relevanceTerms)
    .take(maxOf(0, MAX_AVAILABLE_INDEX_MAP_ENTRIES - loadedBranches.size))
    .map { it.mapEntry }
    .forEach(::add)
}

internal fun String.isDefaultBlockedMapLink(): Boolean = startsWith("${VaultLayout.BRANCH_PEOPLE}/") ||
  startsWith("${VaultLayout.BRANCH_STAGING}/")

private data class RankedCompactMapEntry(
  val mapEntry: CompactMapEntry,
  val plannedBranch: String,
)

private fun GraphIndexEntry.isVisibleForRetrievalDomain(domain: RetrievalDomain): Boolean {
  val hasNoScope = scope == null && scopes.isEmpty()
  val matchesDomain = domain != RetrievalDomain.General &&
    (scope == domain.value || domain.value in scopes)
  return hasNoScope || matchesDomain
}

private fun SessionStartIndexMapEntry.toMapEntry(): RankedCompactMapEntry {
  val safeLinks = entry.safeMapLinks()
  return RankedCompactMapEntry(
    mapEntry = CompactMapEntry(
      id = entry.id.value,
      kind = CompactMapEntryKind.Node,
      reason = reason,
      type = entry.type,
      category = entry.category,
      domain = entry.domain,
      scope = entry.scope,
      scopes = entry.scopes,
      updated = entry.updated.toDateString(),
      date = entry.date?.toDateString(),
      summary = entry.summary(),
      aliases = entry.aliases,
      linkCount = safeLinks.size,
      links = safeLinks,
    ),
    plannedBranch = plannedBranch,
  )
}

private fun List<RankedCompactMapEntry>.topEntriesPerBranch(
  classification: RetrievalClassification,
  relevanceTerms: Set<String>,
): List<RankedCompactMapEntry> = groupBy { it.plannedBranch }
  .values
  .flatMap { entries ->
    val reservedGlobalPreferences = entries
      .filter { it.mapEntry.isGlobalPreference() }
      .sortedForMapBudget(classification, relevanceTerms)
      .take(MAX_RESERVED_GLOBAL_PREFERENCES_PER_BRANCH)
    val reservedIds = reservedGlobalPreferences.mapTo(mutableSetOf()) { it.mapEntry.id }
    reservedGlobalPreferences + entries
      .filterNot { it.mapEntry.id in reservedIds }
      .sortedForMapBudget(classification, relevanceTerms)
      .take(MAX_AVAILABLE_MAP_ENTRIES_PER_BRANCH - reservedGlobalPreferences.size)
  }

private fun List<RankedCompactMapEntry>.sortedForMapBudget(
  classification: RetrievalClassification,
  relevanceTerms: Set<String>,
): List<RankedCompactMapEntry> = sortedWith(
  compareByDescending<RankedCompactMapEntry> { it.mapEntry.mapBudgetScore(classification.domain, relevanceTerms) }
    .thenByDescending { if (it.mapEntry.summary.hasMeaningfulSummary()) 1 else 0 }
    .thenBy { it.mapEntry.id },
)

private fun GraphIndexEntry.safeMapLinks(): List<String> = links
  .map { it.value }
  .distinct()
  .filterNot { it.isDefaultBlockedMapLink() }

private fun GraphIndexEntry.summary(): String = when (type) {
  "subject" -> snippet.takeUnless { it.isHeadingOnly() } ?: subject.orEmpty()
  "episode" -> listOf(topic, snippet.takeUnless { it.isHeadingOnly() })
    .filterNotNull()
    .filter { it.isNotBlank() }
    .joinToString(": ")
  "pattern" -> hypothesis ?: snippet
  else -> snippet
}.limitChars(SUMMARY_LIMIT)

private fun String.isHeadingOnly(): Boolean = trimStart().startsWith("#")

private fun String?.hasMeaningfulSummary(): Boolean = !isNullOrBlank() && !isHeadingOnly()

private fun CompactMapEntry.isGlobalPreference(): Boolean = type == TYPE_STATE &&
  category == CATEGORY_PREFERENCE &&
  scope == null &&
  scopes.isEmpty()
