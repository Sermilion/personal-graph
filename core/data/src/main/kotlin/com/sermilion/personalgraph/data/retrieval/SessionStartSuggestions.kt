package com.sermilion.personalgraph.data.retrieval

import com.sermilion.personalgraph.domain.retrieval.CompactMapEntry
import com.sermilion.personalgraph.domain.retrieval.RetrievalAuditEntry
import com.sermilion.personalgraph.domain.retrieval.RetrievalDomain
import com.sermilion.personalgraph.domain.retrieval.SuggestedRead
import com.sermilion.personalgraph.domain.retrieval.SuggestedReadPriority

private const val SCORE_DOMAIN_SUBJECT: Int = 100
private const val SCORE_DOMAIN_SCOPED_STATE: Int = 90
private const val SCORE_MULTI_DOMAIN_SCOPED_STATE: Int = 85
private const val SCORE_GLOBAL_PREFERENCE: Int = 70
private const val SCORE_DOMAIN_EVENT: Int = 50
private const val SCORE_PATTERN: Int = 35
private const val SCORE_STATE: Int = 30
private const val SCORE_DEFAULT: Int = 10
private const val SCORE_MAP_SCOPED_STATE: Int = 95
private const val SCORE_MAP_PATTERN: Int = 60
private const val SCORE_MAP_SUBJECT: Int = 50
private const val SCORE_MAP_STATE: Int = 40
private const val PRIORITY_HIGH_SCORE: Int = 80
private const val PRIORITY_MEDIUM_SCORE: Int = 50
private const val TYPE_SUBJECT: String = "subject"
private const val TYPE_EPISODE: String = "episode"
private const val CATEGORY_PREFERENCE: String = "preference"

internal fun CompactMapEntry.isSuggestedFor(domain: RetrievalDomain): Boolean = listOf(
  isPattern(),
  domainEntry(domain),
  globalPreference(),
  scopedStateFor(domain),
  generalGlobalState(domain),
).any { it }

internal fun CompactMapEntry.suggestionScore(domain: RetrievalDomain): Int = when {
  domainSubject(domain) -> SCORE_DOMAIN_SUBJECT
  isState() && scope == domain.value -> SCORE_DOMAIN_SCOPED_STATE
  isState() && domain.value in scopes -> SCORE_MULTI_DOMAIN_SCOPED_STATE
  globalPreference() -> SCORE_GLOBAL_PREFERENCE
  domainEpisode(domain) -> SCORE_DOMAIN_EVENT
  isPattern() -> SCORE_PATTERN
  isState() -> SCORE_STATE
  else -> SCORE_DEFAULT
}

internal fun CompactMapEntry.mapBudgetScore(domain: RetrievalDomain): Int = when {
  domainSubject(domain) -> SCORE_DOMAIN_SUBJECT
  isState() && scope == domain.value -> SCORE_MAP_SCOPED_STATE
  isState() && domain.value in scopes -> SCORE_DOMAIN_SCOPED_STATE
  domainEpisode(domain) -> SCORE_MULTI_DOMAIN_SCOPED_STATE
  globalPreference() -> SCORE_GLOBAL_PREFERENCE
  isPattern() -> SCORE_MAP_PATTERN
  type == "subject" -> SCORE_MAP_SUBJECT
  isState() -> SCORE_MAP_STATE
  else -> SCORE_DEFAULT
}

internal fun CompactMapEntry.toSuggestedRead(
  domain: RetrievalDomain,
  audit: MutableList<RetrievalAuditEntry>,
): SuggestedRead {
  val reason = suggestionReason(domain)
  audit.add(RetrievalAuditEntry(action = "suggested_read", subject = id, reason = reason))
  return SuggestedRead(id = id, reason = reason, priority = suggestedReadPriority(domain))
}

internal fun MutableList<RetrievalAuditEntry>.addNoSuggestedRead(domain: RetrievalDomain) {
  add(
    RetrievalAuditEntry(
      action = "suggested_read",
      subject = domain.value,
      reason = "no eligible map entries found for the classified domain",
    ),
  )
}

private fun CompactMapEntry.suggestedReadPriority(domain: RetrievalDomain): SuggestedReadPriority = when {
  suggestionScore(domain) >= PRIORITY_HIGH_SCORE -> SuggestedReadPriority.High
  suggestionScore(domain) >= PRIORITY_MEDIUM_SCORE -> SuggestedReadPriority.Medium
  else -> SuggestedReadPriority.Low
}

private fun CompactMapEntry.isState(): Boolean = type == "state"

private fun CompactMapEntry.isPattern(): Boolean = type == "pattern"

private fun CompactMapEntry.domainEntry(domain: RetrievalDomain) = active(domain) && this.domain == domain.value

private fun CompactMapEntry.domainSubject(domain: RetrievalDomain) = type == TYPE_SUBJECT && this.domain == domain.value

private fun CompactMapEntry.domainEpisode(domain: RetrievalDomain) = type == TYPE_EPISODE && this.domain == domain.value

private fun CompactMapEntry.globalPreference() = isState() && category == CATEGORY_PREFERENCE && unscoped()

private fun CompactMapEntry.scopedStateFor(domain: RetrievalDomain) = isState() && active(domain) && scopedTo(domain)

private fun CompactMapEntry.generalGlobalState(domain: RetrievalDomain) = domain.isGeneral() && isState() && unscoped()

private fun active(domain: RetrievalDomain): Boolean = domain != RetrievalDomain.General

private fun RetrievalDomain.isGeneral(): Boolean = this == RetrievalDomain.General

private fun CompactMapEntry.unscoped(): Boolean = scope == null && scopes.isEmpty()

private fun CompactMapEntry.scopedTo(domain: RetrievalDomain): Boolean = scope == domain.value || domain.value in scopes

private fun CompactMapEntry.suggestionReason(domain: RetrievalDomain): String = when {
  type == "subject" && this.domain == domain.value ->
    "classified ${domain.value}; subject hubs are preferred follow-up reads"
  type == "state" && (scope == domain.value || domain.value in scopes) ->
    "classified ${domain.value}; scoped state matches the active domain"
  globalPreference() ->
    "global preference is visible for every session-start domain"
  type == "episode" && this.domain == domain.value ->
    "classified ${domain.value}; event evidence may be useful after map review"
  type == "pattern" ->
    "pattern hub was linked from available retrieval context"
  else ->
    "eligible map entry for ${domain.value}"
}
