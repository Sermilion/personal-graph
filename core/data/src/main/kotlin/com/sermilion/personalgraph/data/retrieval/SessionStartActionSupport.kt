package com.sermilion.personalgraph.data.retrieval

import com.sermilion.personalgraph.domain.layout.VaultLayout
import com.sermilion.personalgraph.domain.layout.VaultPolicy
import com.sermilion.personalgraph.domain.retrieval.RetrievalAuditEntry
import com.sermilion.personalgraph.domain.retrieval.RetrievalClassification
import com.sermilion.personalgraph.domain.retrieval.RetrievalDomain
import com.sermilion.personalgraph.domain.retrieval.RetrievedBranch
import com.sermilion.personalgraph.domain.retrieval.SessionStartRetrievalReport
import com.sermilion.personalgraph.domain.retrieval.SessionStartTokenAccounting
import com.sermilion.personalgraph.domain.retrieval.SuggestedAction
import com.sermilion.personalgraph.domain.retrieval.SuggestedActionArg
import com.sermilion.personalgraph.domain.retrieval.SuggestedActionPriority
import com.sermilion.personalgraph.domain.retrieval.SuggestedActionValue
import com.sermilion.personalgraph.domain.tokens.TokenEstimator

private const val DEFAULT_SUGGESTED_SEARCH_LIMIT: Int = 20
private const val DEFAULT_SUGGESTED_BRANCH_LIMIT: Int = 20

internal fun suggestedActions(
  message: String,
  classification: RetrievalClassification,
  loadedBranches: List<RetrievedBranch>,
  audit: MutableList<RetrievalAuditEntry>,
): List<SuggestedAction> {
  val trimmed = message.trim()
  if (trimmed.isEmpty()) {
    audit.addNoSuggestedAction(classification.domain)
    return emptyList()
  }

  val identifierLikeQuery = detectIdentifierLikeQuery(trimmed)
  val searchQuery = identifierLikeQuery ?: sanitizedSearchQuery(trimmed, classification)
  val branches = if (identifierLikeQuery != null) {
    defaultSearchBranches()
  } else {
    loadedBranches.map { it.branch }.distinct().ifEmpty { defaultSearchBranches() }
  }

  val actions = buildList {
    add(
      buildSearchAction(
        query = searchQuery,
        branches = branches,
        identifierLikeQuery = identifierLikeQuery,
      ),
    )
    if (identifierLikeQuery == null) {
      val branch = branches.firstOrNull()
      if (branch != null) {
        add(buildListBranchAction(branch))
      }
    }
  }

  if (actions.isEmpty()) {
    audit.addNoSuggestedAction(classification.domain)
  } else {
    actions.forEach { action ->
      audit.add(
        RetrievalAuditEntry(
          action = "suggested_action",
          subject = action.tool,
          reason = action.reason,
        ),
      )
    }
  }
  return actions
}

internal fun estimatedTokens(report: SessionStartRetrievalReport): SessionStartTokenAccounting {
  val metadataTokens = TokenEstimator.estimateString(buildSessionStartMetadataBlock(report))
  val bodyTokens = report.loadedContext.sumOf { TokenEstimator.estimateBody(it.body) }
  val prunedBodyTokens = 0
  return SessionStartTokenAccounting(
    responseTotal = metadataTokens + bodyTokens + prunedBodyTokens,
    metadataTokens = metadataTokens,
    bodyTokens = bodyTokens,
    prunedBodyTokens = prunedBodyTokens,
  )
}

private fun buildSearchAction(
  query: String,
  branches: List<String>,
  identifierLikeQuery: String?,
): SuggestedAction = SuggestedAction(
  tool = "search_nodes",
  args = listOf(
    SuggestedActionArg("query", SuggestedActionValue.StringValue(query)),
    SuggestedActionArg("branches", SuggestedActionValue.StringListValue(branches)),
    SuggestedActionArg("limit", SuggestedActionValue.IntValue(DEFAULT_SUGGESTED_SEARCH_LIMIT)),
    SuggestedActionArg(
      "search_fields",
      SuggestedActionValue.StringListValue(
        if (identifierLikeQuery != null) {
          listOf("id", "metadata")
        } else {
          listOf("id", "metadata", "body")
        },
      ),
    ),
    SuggestedActionArg("body_fallback", SuggestedActionValue.BooleanValue(identifierLikeQuery == null)),
    SuggestedActionArg("include_body", SuggestedActionValue.BooleanValue(false)),
  ),
  reason = if (identifierLikeQuery != null) {
    "identifier-like token $identifierLikeQuery detected; search ids and metadata before reading bodies"
  } else {
    "search the loaded branches before reading full bodies"
  },
  priority = SuggestedActionPriority.High,
)

private fun buildListBranchAction(branch: String): SuggestedAction = SuggestedAction(
  tool = "list_branch",
  args = listOf(
    SuggestedActionArg("branch", SuggestedActionValue.StringValue(branch)),
    SuggestedActionArg("mode", SuggestedActionValue.StringValue("index")),
    SuggestedActionArg("include_links", SuggestedActionValue.BooleanValue(true)),
    SuggestedActionArg("include_body", SuggestedActionValue.BooleanValue(false)),
    SuggestedActionArg("limit", SuggestedActionValue.IntValue(DEFAULT_SUGGESTED_BRANCH_LIMIT)),
  ),
  reason = "branch-constrained metadata inspection before any full-body branch read",
  priority = SuggestedActionPriority.Medium,
)

private fun MutableList<RetrievalAuditEntry>.addNoSuggestedAction(domain: RetrievalDomain) {
  add(
    RetrievalAuditEntry(
      action = "suggested_action",
      subject = domain.value,
      reason = "no identifier-like query detected; search the loaded branches before reading full bodies",
    ),
  )
}

private fun buildSessionStartMetadataBlock(report: SessionStartRetrievalReport): String = buildString {
  appendLine("classification:${report.classification.domain.value}")
  appendLine("matched_terms:${report.classification.matchedTerms.joinToString(",")}")
  appendLine("emotional_context:${report.classification.emotionalContextRequested}")
  appendLine("emotional_terms:${report.classification.emotionalMatchedTerms.joinToString(",")}")
  report.rootDocument?.let { root ->
    appendLine("root:${root.path}|${root.loadOrder}|${root.reason}")
  }
  for (branch in report.loadedBranches) {
    appendLine("branch:${branch.branch}|${branch.nodeCount}|${branch.reason}")
  }
  for (entry in report.availableMap) {
    appendLine(
      buildString {
        append("map:").append(entry.id).append('|')
        append(entry.kind.value).append('|')
        append(entry.type.orEmpty()).append('|')
        append(entry.category.orEmpty()).append('|')
        append(entry.domain.orEmpty()).append('|')
        append(entry.scope.orEmpty()).append('|')
        append(entry.scopes.joinToString(",")).append('|')
        append(entry.updated.orEmpty()).append('|')
        append(entry.date.orEmpty()).append('|')
        append(entry.summary.orEmpty()).append('|')
        append(entry.aliases.joinToString(",")).append('|')
        append(entry.linkCount?.toString().orEmpty()).append('|')
        append(entry.links.joinToString(","))
      },
    )
  }
  for (read in report.suggestedReads) {
    appendLine("read:${read.id}|${read.priority.value}|${read.reason}")
  }
  for (action in report.suggestedActions) {
    appendLine("action:${action.tool}|${action.priority.value}|${action.reason}")
    for (arg in action.args) {
      appendLine("arg:${arg.key}|${renderActionValue(arg.value)}")
    }
  }
  for (skip in report.skippedBranches) {
    appendLine("skip:${skip.branch}|${skip.reason}")
  }
  for (audit in report.audit) {
    appendLine("audit:${audit.action}|${audit.subject}|${audit.reason}")
  }
  for (context in report.loadedContext) {
    appendLine("context:${context.id}|${context.source.value}|${context.loadOrder}|${context.reason}")
  }
}

private fun renderActionValue(value: SuggestedActionValue): String = when (value) {
  is SuggestedActionValue.StringValue -> value.value
  is SuggestedActionValue.BooleanValue -> value.value.toString()
  is SuggestedActionValue.IntValue -> value.value.toString()
  is SuggestedActionValue.StringListValue -> value.value.joinToString(",")
}

private fun detectIdentifierLikeQuery(message: String): String? {
  for (pattern in IDENTIFIER_PATTERNS) {
    for (match in pattern.findAll(message)) {
      val candidate = normalizeIdentifierMatch(match.value)
      if (candidate.isVisibleIdentifier()) return candidate
    }
  }
  return null
}

private fun sanitizedSearchQuery(message: String, classification: RetrievalClassification): String {
  val ranges = blockedIdentifierRanges(message)
  if (ranges.isEmpty()) return message
  val builder = StringBuilder(message)
  for (range in ranges.sortedByDescending { it.first }) {
    builder.replace(range.first, range.last + 1, " ")
  }
  return builder.toString()
    .trim()
    .split(Regex("\\s+"))
    .filter { it.isNotBlank() }
    .joinToString(" ")
    .ifBlank { classification.domain.value }
}

private fun blockedIdentifierRanges(message: String): List<IntRange> = IDENTIFIER_PATTERNS
  .flatMap { pattern -> pattern.findAll(message).map { it.range } }
  .distinctBy { it.first to it.last }
  .filter { range ->
    val candidate = normalizeIdentifierMatch(message.substring(range))
    !candidate.isVisibleIdentifier()
  }
  .mergeOverlaps()

private fun List<IntRange>.mergeOverlaps(): List<IntRange> {
  val merged = mutableListOf<IntRange>()
  for (range in sortedBy { it.first }) {
    val previous = merged.lastOrNull()
    if (previous != null && range.first <= previous.last + 1) {
      merged[merged.lastIndex] = previous.first..maxOf(previous.last, range.last)
    } else {
      merged += range
    }
  }
  return merged
}

private fun normalizeIdentifierMatch(raw: String): String = raw
  .trim()
  .trimStart('(', '[', '{')
  .trimEnd('.', ',', ';', ':', ')', ']', '}')
  .removeSuffix(".md")

private fun String.isVisibleIdentifier(): Boolean = !isReadBlockedIdentifier() &&
  !isIndexExcludedIdentifier() &&
  !containsBlockedPathSuffix()

private fun String.containsBlockedPathSuffix(): Boolean {
  val segments = trim('/').split('/').filter { it.isNotBlank() }
  return segments.indices.any { index ->
    val suffix = segments.drop(index).joinToString("/")
    suffix.isReadBlockedIdentifier() || suffix.isIndexExcludedIdentifier()
  }
}

private fun String.isReadBlockedIdentifier(): Boolean = VaultPolicy.isReadBlocked(policyComparableIdentifier())

private fun String.isIndexExcludedIdentifier(): Boolean = VaultPolicy.isIndexExcluded(policyComparableIdentifier())

private fun String.policyComparableIdentifier(): String = lowercase()

private val IDENTIFIER_PATTERNS: List<Regex> = listOf(
  Regex("""\b[A-Z][A-Z0-9]+-\d+\b"""),
  Regex("""\bPR\s*#\d+\b""", RegexOption.IGNORE_CASE),
  Regex(
    """\b(?:state|domains|patterns|emotional-states|timeline|staging|people)(?:/[A-Za-z0-9._-]+)+(?:\.md)?\b""",
  ),
  Regex("""\b[A-Za-z][A-Za-z0-9._-]*(?:/[A-Za-z0-9][A-Za-z0-9._-]*)+\b"""),
)

private fun defaultSearchBranches(): List<String> = listOf(
  VaultLayout.BRANCH_STATE,
  VaultLayout.BRANCH_DOMAINS,
  VaultLayout.BRANCH_PATTERNS,
  VaultLayout.BRANCH_EMOTIONAL_STATES,
  VaultLayout.BRANCH_TIMELINE,
  VaultLayout.BRANCH_STAGING_OBSERVATIONS,
  VaultLayout.BRANCH_OUTDATED,
)
