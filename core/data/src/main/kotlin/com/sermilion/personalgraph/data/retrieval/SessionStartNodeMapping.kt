package com.sermilion.personalgraph.data.retrieval

import com.sermilion.personalgraph.domain.model.EmotionalStateNode
import com.sermilion.personalgraph.domain.model.EpisodeNode
import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.model.PatternNode
import com.sermilion.personalgraph.domain.model.StateNode
import com.sermilion.personalgraph.domain.model.SubjectNode
import com.sermilion.personalgraph.domain.model.VaultNode
import com.sermilion.personalgraph.domain.retrieval.RetrievalAuditEntry
import com.sermilion.personalgraph.domain.retrieval.SessionStartRetrievalMode
import kotlinx.datetime.Instant

internal const val MAX_LOADED_CONTEXT_WORDS: Int = 1200
internal const val SUMMARY_LIMIT: Int = 180

internal fun VaultNode.mapType(): String = when (this) {
  is StateNode -> "state"
  is EpisodeNode -> "episode"
  is PatternNode -> "pattern"
  is SubjectNode -> "subject"
  is EmotionalStateNode -> "emotional-state"
}

internal fun VaultNode.mapCategory(): String? = when (this) {
  is StateNode -> category.name.lowercase()
  else -> null
}

internal fun VaultNode.mapDomain(): String? = when (this) {
  is EpisodeNode -> domain
  is SubjectNode -> domain
  is PatternNode -> domainsSeenIn.joinToString(",").ifBlank { null }
  else -> null
}

internal fun VaultNode.mapScope(): String? = (this as? StateNode)?.scope

internal fun VaultNode.mapScopes(): List<String> = (this as? StateNode)?.scopes.orEmpty()

internal fun VaultNode.mapDate(): String? = when (this) {
  is EpisodeNode -> date.toDateString()
  is EmotionalStateNode -> date.toDateString()
  is PatternNode -> lastObserved.toDateString()
  else -> null
}

internal fun VaultNode.mapAliases(): List<String> = (this as? SubjectNode)?.aliases.orEmpty()

internal fun VaultNode.mapSummary(): String = when (this) {
  is SubjectNode -> body.subjectSummary() ?: body.firstMeaningfulBodyLine()
  is EpisodeNode -> listOf(topic, body.firstMeaningfulBodyLine())
    .filter { it.isNotBlank() }
    .joinToString(": ")
  is PatternNode -> hypothesis
  else -> body.firstMeaningfulBodyLine()
}.limitChars(SUMMARY_LIMIT)

internal fun VaultNode.directPatternLinks(): List<NodeId> = when (this) {
  is StateNode -> patternLinks
  is EpisodeNode -> patternLinks
  is PatternNode -> patternLinks
  is SubjectNode -> patternLinks
  is EmotionalStateNode -> patternLinks
}.distinctBy { it.value }

internal fun retrievalModeAudit(retrievalMode: SessionStartRetrievalMode): RetrievalAuditEntry = when (retrievalMode) {
  SessionStartRetrievalMode.MapFirst -> RetrievalAuditEntry(
    action = "map_first_default",
    subject = "loaded_context",
    reason = "default map-first loads root only; use read_node/list_branch or full-loading for bodies",
  )
  SessionStartRetrievalMode.FullLoading -> RetrievalAuditEntry(
    action = "full_loading",
    subject = "loaded_context",
    reason = "explicit full-loading retrieval mode requested; loaded_context includes node bodies",
  )
}

internal fun String.subjectSummary(): String? {
  val lines = lines()
  val summaryIndex = lines.indexOfFirst { it.trim() == "## Summary" }
  if (summaryIndex == -1) return null
  return lines.drop(summaryIndex + 1)
    .firstOrNull { it.isNotBlank() && !it.trim().startsWith("#") }
    ?.trim()
}

internal fun String.firstMeaningfulBodyLine(): String = lineSequence()
  .filter { it.isNotBlank() }
  .dropWhile { it.trim() == "---" || it.contains(":") && !it.startsWith("#") }
  .firstOrNull { !it.trim().startsWith("---") }
  ?.trim()
  .orEmpty()

internal fun String.limitChars(limit: Int): String = if (length <= limit) this else take(limit).trimEnd() + "..."

internal fun String.limitWords(limit: Int): String {
  val words = trim().split(Regex("\\s+")).filter { it.isNotBlank() }
  if (words.size <= limit) return this
  return words.take(limit).joinToString(" ") + "\n..."
}

internal fun Instant.toDateString(): String = toString().substringBefore("T")
