package com.sermilion.personalgraph.domain.search

import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.model.VaultNode

enum class BranchListMode {
  Full,
  Index,
}

data class BranchListQuery(
  val branch: String,
  val mode: BranchListMode,
  val filter: String?,
  val limit: Int?,
  val includeLinks: Boolean,
  val includeBody: Boolean,
)

data class BranchListEntry(
  val id: NodeId,
  val type: String,
  val domain: String?,
  val subject: String?,
  val snippet: String,
  val matchFields: List<String>,
  val score: Int,
  val links: List<NodeId>,
)

data class BranchListTokenAccounting(
  val metadataTokens: Int,
  val bodyTokens: Int,
  val prunedBodyTokens: Int,
)

sealed interface BranchListOutcome {
  val mode: BranchListMode
  val estimatedTokens: BranchListTokenAccounting

  data class Full(
    override val mode: BranchListMode,
    val nodes: List<VaultNode>,
    override val estimatedTokens: BranchListTokenAccounting,
  ) : BranchListOutcome

  data class Index(
    override val mode: BranchListMode,
    val entries: List<BranchListEntry>,
    override val estimatedTokens: BranchListTokenAccounting,
  ) : BranchListOutcome
}

interface BranchListingService {
  suspend fun list(query: BranchListQuery): BranchListOutcome
}
