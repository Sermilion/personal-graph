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
