package com.sermilion.personalgraph.domain.repository

import com.sermilion.personalgraph.domain.graph.GraphIndexEntry
import com.sermilion.personalgraph.domain.model.NodeId

/**
 * Compact, lazily-built graph index over the vault. Implementations are expected
 * to populate per-branch state on demand: [findEntryByAlias], [findEntryByTitle],
 * and [findEntryByPath] resolve against side maps that are warmed when entries
 * are built via [listEntriesInBranch] or [findEntry]. Cold lookups for entries
 * that have not yet been seen for their branch may return null even when the
 * underlying file exists; callers that need exhaustive coverage should call
 * [listEntriesInBranch] for the relevant branch first.
 *
 * All methods return null or empty collections on miss. Implementations must
 * never surface entries from index-excluded branches (people/, staging/sensitive/).
 */
interface GraphIndexRepository {
  suspend fun listEntriesInBranch(branchPath: String): List<GraphIndexEntry>

  suspend fun findEntry(id: NodeId): GraphIndexEntry?

  suspend fun findEntryByAlias(alias: String): GraphIndexEntry?

  suspend fun findEntryByTitle(title: String): GraphIndexEntry?

  suspend fun findEntryByPath(path: String): GraphIndexEntry?
}
