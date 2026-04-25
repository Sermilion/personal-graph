package com.sermilion.personalgraph.domain.retrieval

interface SessionStartRetrievalService {
  suspend fun retrieve(request: SessionStartRetrievalRequest): SessionStartRetrievalReport
}

data class SessionStartRetrievalRequest(
  val firstSubstantiveMessage: String,
)

data class SessionStartRetrievalReport(
  val rootDocument: RetrievedRootDocument?,
  val classification: RetrievalClassification,
  val loadedBranches: List<RetrievedBranch>,
  val loadedNodes: List<RetrievedNode>,
  val skippedBranches: List<SkippedBranch>,
  val audit: List<RetrievalAuditEntry>,
)

data class RetrievalClassification(
  val domain: RetrievalDomain,
  val matchedTerms: List<String>,
  val emotionalContextRequested: Boolean,
  val emotionalMatchedTerms: List<String>,
)

enum class RetrievalDomain(val value: String) {
  WorkCapmo("work/capmo"),
  Personal("personal"),
  Creative("creative"),
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

data class SkippedBranch(
  val branch: String,
  val reason: String,
)

data class RetrievalAuditEntry(
  val action: String,
  val subject: String,
  val reason: String,
)
