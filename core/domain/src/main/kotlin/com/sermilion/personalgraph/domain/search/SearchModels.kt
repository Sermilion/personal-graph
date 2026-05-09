package com.sermilion.personalgraph.domain.search

import com.sermilion.personalgraph.domain.model.NodeId

enum class SearchField {
  Id,
  Metadata,
  Body,
}

enum class SearchRankingTier(val score: Int) {
  ExactFullIdOrPath(SCORE_EXACT_FULL_ID_OR_PATH),
  LeafIdOrSlug(SCORE_LEAF_ID_OR_SLUG),
  SubjectTopicAliasHypothesis(SCORE_SUBJECT_TOPIC_ALIAS_HYPOTHESIS),
  DomainOrBranchRelevance(SCORE_DOMAIN_OR_BRANCH_RELEVANCE),
  BodyMention(SCORE_BODY_MENTION),
}

private const val SCORE_EXACT_FULL_ID_OR_PATH = 100
private const val SCORE_LEAF_ID_OR_SLUG = 80
private const val SCORE_SUBJECT_TOPIC_ALIAS_HYPOTHESIS = 60
private const val SCORE_DOMAIN_OR_BRANCH_RELEVANCE = 40
private const val SCORE_BODY_MENTION = 20

object SearchRecency {
  val TRIGGERS: Set<String> = setOf(
    "recent",
    "latest",
    "today",
    "merged",
    "opened",
    "status",
  )

  const val BOOST: Int = 5
}

data class SearchQuery(
  val query: String,
  val branches: List<String> = emptyList(),
  val limit: Int = DEFAULT_LIMIT,
  val searchFields: Set<SearchField> = DEFAULT_FIELDS,
  val bodyFallback: Boolean = true,
  val includeBody: Boolean = false,
) {
  companion object {
    const val DEFAULT_LIMIT: Int = 20
    val DEFAULT_FIELDS: Set<SearchField> = setOf(SearchField.Id, SearchField.Metadata, SearchField.Body)
  }
}

data class SearchPlan(
  val metadataIndexUsed: Boolean,
  val bodyFallbackUsed: Boolean,
  val branchesSearched: List<String>,
)

data class SearchHit(
  val id: NodeId,
  val type: String,
  val domain: String?,
  val subject: String?,
  val matchFields: List<String>,
  val snippet: String,
  val links: List<NodeId>,
  val score: Int,
  val body: String? = null,
)

data class SearchOutcome(
  val hits: List<SearchHit>,
  val plan: SearchPlan,
  val estimatedTokens: Int,
)

enum class TraversalRankBy {
  Relevance,
  Recency,
}

data class TraverseGraphQuery(
  val query: String = "",
  val startIds: List<NodeId> = emptyList(),
  val branches: List<String> = emptyList(),
  val edgeTypes: Set<TraversalEdgeType> = TraversalEdgeType.DEFAULTS,
  val maxDepth: Int = DEFAULT_MAX_DEPTH,
  val maxNodes: Int = DEFAULT_MAX_NODES,
  val budgetTokens: Int = DEFAULT_BUDGET_TOKENS,
  val includeBodies: Boolean = false,
  val rankBy: TraversalRankBy = TraversalRankBy.Relevance,
) {
  companion object {
    const val DEFAULT_MAX_DEPTH: Int = 2
    const val DEFAULT_MAX_NODES: Int = 20
    const val DEFAULT_BUDGET_TOKENS: Int = 4_000
  }
}

enum class TraversalEdgeType {
  Link,
  Backlink,
  SubjectEvidence,
  Timeline,
  State,
  Pattern,
  Contradiction,
  Background,
  ;

  companion object {
    val DEFAULTS: Set<TraversalEdgeType> = entries.toSet() - Backlink
  }
}

enum class TraversalPrunedReason {
  MaxNodes,
  BudgetTokens,
}

data class TraversalEntrypoint(
  val id: NodeId,
  val reason: String,
  val score: Int,
)

data class TraversalNode(
  val id: NodeId,
  val type: String,
  val domain: String?,
  val subject: String?,
  val snippet: String,
  val score: Int,
  val depth: Int,
  val matchFields: List<String>,
  val body: String? = null,
)

data class TraversalEdge(
  val from: NodeId,
  val to: NodeId,
  val type: TraversalEdgeType,
  val label: String,
  val weight: Int,
)

data class TraversalPrunedCandidate(
  val id: NodeId,
  val reason: TraversalPrunedReason,
  val score: Int,
  val estimatedTokens: Int,
  val bodyTokenEstimate: Int = estimatedTokens,
)

data class TraversalSuggestedRead(
  val id: NodeId,
  val reason: String,
  val priority: Int,
)

data class TraverseGraphOutcome(
  val entrypoints: List<TraversalEntrypoint>,
  val nodes: List<TraversalNode>,
  val edges: List<TraversalEdge>,
  val pruned: List<TraversalPrunedCandidate>,
  val suggestedReads: List<TraversalSuggestedRead>,
  val estimatedTokens: Int,
  val tokenAccounting: TraversalTokenAccounting = TraversalTokenAccounting(responseTotal = estimatedTokens),
) {
  init {
    require(estimatedTokens == tokenAccounting.responseTotal) {
      "estimatedTokens must match tokenAccounting.responseTotal"
    }
  }
}

data class TraversalTokenAccounting(
  val responseTotal: Int = 0,
  val metadataTokens: Int = 0,
  val bodyTokens: Int = 0,
  val prunedBodyTokens: Int = 0,
)
