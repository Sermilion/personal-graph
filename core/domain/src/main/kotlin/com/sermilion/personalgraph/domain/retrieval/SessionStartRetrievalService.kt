package com.sermilion.personalgraph.domain.retrieval

interface SessionStartRetrievalService {
  suspend fun retrieve(request: SessionStartRetrievalRequest): SessionStartRetrievalReport
}

data class SessionStartRetrievalRequest(
  val firstSubstantiveMessage: String,
  val retrievalMode: SessionStartRetrievalMode = SessionStartRetrievalMode.MapFirst,
)

enum class SessionStartRetrievalMode(val value: String) {
  MapFirst("map-first"),
  FullLoading("full-loading"),
}

data class SessionStartRetrievalReport(
  val rootDocument: RetrievedRootDocument?,
  val classification: RetrievalClassification,
  val loadedBranches: List<RetrievedBranch>,
  val loadedNodes: List<RetrievedNode>,
  val skippedBranches: List<SkippedBranch>,
  val audit: List<RetrievalAuditEntry>,
  val loadedFullBodyContext: List<LoadedFullBodyContext> = emptyList(),
  val compactMapEntries: List<CompactMapEntry> = emptyList(),
  val suggestedReads: List<SuggestedRead> = emptyList(),
  val auditEntries: List<RetrievalAuditEntry> = audit,
)

data class RetrievalClassification(
  val domain: RetrievalDomain,
  val matchedTerms: List<String>,
  val emotionalContextRequested: Boolean,
  val emotionalMatchedTerms: List<String>,
)

enum class RetrievalDomain(val value: String) {
  WorkCapmo("work/capmo"),
  WorkSkillBill("work/skill-bill"),
  WorkReadian("work/readian"),
  WorkContextApp("work/context-app"),
  CreativeMusic("creative/music"),
  Personal("personal"),
  General("general"),
}

data class RetrievedRootDocument(
  val path: String,
  val body: String,
  val loadOrder: Int,
  val reason: String,
)

data class RetrievedBranch(
  val branch: String,
  val reason: String,
  val nodeCount: Int,
)

data class RetrievedNode(
  val id: String,
  val body: String,
  val links: List<String>,
  val patternLinks: List<String>,
  val loadOrder: Int,
  val reason: String,
)

data class LoadedFullBodyContext(
  val id: String,
  val body: String,
  val source: FullBodyContextSource,
  val loadOrder: Int,
  val reason: String,
)

enum class FullBodyContextSource(val value: String) {
  Root("root"),
  Node("node"),
}

data class CompactMapEntry(
  val id: String,
  val kind: CompactMapEntryKind,
  val reason: String,
  val nodeCount: Int? = null,
)

enum class CompactMapEntryKind(val value: String) {
  Branch("branch"),
  Node("node"),
}

data class SuggestedRead(
  val id: String,
  val reason: String,
)

data class SkippedBranch(
  val branch: String,
  val reason: String,
)

data class RetrievalAuditEntry(
  val action: String,
  val subject: String,
  val reason: String,
)
