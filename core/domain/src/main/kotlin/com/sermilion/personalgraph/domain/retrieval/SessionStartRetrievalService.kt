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
  val loadedContext: List<LoadedFullBodyContext> = emptyList(),
  val availableMap: List<CompactMapEntry> = emptyList(),
  val suggestedReads: List<SuggestedRead> = emptyList(),
  val suggestedActions: List<SuggestedAction> = emptyList(),
  val estimatedTokens: SessionStartTokenAccounting = SessionStartTokenAccounting(),
  val skippedBranches: List<SkippedBranch> = emptyList(),
  val audit: List<RetrievalAuditEntry> = emptyList(),
  val loadedBranches: List<RetrievedBranch> = emptyList(),
  val loadedNodes: List<RetrievedNode> = emptyList(),
  val loadedFullBodyContext: List<LoadedFullBodyContext> = loadedContext,
  val compactMapEntries: List<CompactMapEntry> = availableMap,
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
  val type: String? = null,
  val category: String? = null,
  val domain: String? = null,
  val scope: String? = null,
  val scopes: List<String> = emptyList(),
  val updated: String? = null,
  val date: String? = null,
  val summary: String? = null,
  val aliases: List<String> = emptyList(),
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
  val type: String? = null,
  val category: String? = null,
  val domain: String? = null,
  val scope: String? = null,
  val scopes: List<String> = emptyList(),
  val updated: String? = null,
  val date: String? = null,
  val summary: String? = null,
  val aliases: List<String> = emptyList(),
  val linkCount: Int? = null,
  val links: List<String> = emptyList(),
)

enum class CompactMapEntryKind(val value: String) {
  Branch("branch"),
  Node("node"),
}

data class SuggestedRead(
  val id: String,
  val reason: String,
  val priority: SuggestedReadPriority = SuggestedReadPriority.Medium,
)

enum class SuggestedReadPriority(val value: String) {
  High("high"),
  Medium("medium"),
  Low("low"),
}

data class SuggestedAction(
  val tool: String,
  val args: List<SuggestedActionArg> = emptyList(),
  val reason: String,
  val priority: SuggestedActionPriority = SuggestedActionPriority.Medium,
)

data class SuggestedActionArg(
  val key: String,
  val value: SuggestedActionValue,
)

sealed interface SuggestedActionValue {
  data class StringValue(val value: String) : SuggestedActionValue
  data class BooleanValue(val value: Boolean) : SuggestedActionValue
  data class IntValue(val value: Int) : SuggestedActionValue
  data class StringListValue(val value: List<String>) : SuggestedActionValue
}

enum class SuggestedActionPriority(val value: String) {
  High("high"),
  Medium("medium"),
  Low("low"),
}

data class SessionStartTokenAccounting(
  val responseTotal: Int = 0,
  val metadataTokens: Int = 0,
  val bodyTokens: Int = 0,
  val prunedBodyTokens: Int = 0,
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
