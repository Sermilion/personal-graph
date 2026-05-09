package com.sermilion.personalgraph.data.retrieval

import com.sermilion.personalgraph.domain.retrieval.RetrievalClassification
import com.sermilion.personalgraph.domain.retrieval.RetrievalDomain

internal fun classify(message: String): RetrievalClassification {
  val capmoMatches = matchedTerms(message, WORK_CAPMO_TERMS)
  val skillBillMatches = matchedTerms(message, WORK_SKILL_BILL_TERMS)
  val readianMatches = matchedTerms(message, WORK_READIAN_TERMS)
  val contextAppMatches = matchedTerms(message, WORK_CONTEXT_APP_TERMS)
  val personalMatches = matchedTerms(message, PERSONAL_TERMS)
  val creativeMusicMatches = matchedTerms(message, CREATIVE_MUSIC_TERMS)
  val emotionalMatches = matchedTerms(message, EMOTIONAL_TERMS)
  val domainMatches = listOf(
    RetrievalDomain.WorkCapmo to capmoMatches,
    RetrievalDomain.WorkSkillBill to skillBillMatches,
    RetrievalDomain.WorkReadian to readianMatches,
    RetrievalDomain.WorkContextApp to contextAppMatches,
    RetrievalDomain.Personal to personalMatches,
    RetrievalDomain.CreativeMusic to creativeMusicMatches,
  )
  val domain = bestDomain(domainMatches)
  return RetrievalClassification(
    domain = domain,
    matchedTerms = domainMatches.firstOrNull { it.first == domain }?.second.orEmpty(),
    emotionalContextRequested = emotionalMatches.isNotEmpty(),
    emotionalMatchedTerms = emotionalMatches,
  )
}

internal fun classificationReason(classification: RetrievalClassification): String {
  val domainReason = if (classification.matchedTerms.isEmpty()) {
    "no domain-specific terms matched; using durable general context"
  } else {
    "matched terms: ${classification.matchedTerms.joinToString(",")}"
  }
  val emotionalReason = if (classification.emotionalContextRequested) {
    "; emotional context requested by: ${classification.emotionalMatchedTerms.joinToString(",")}"
  } else {
    "; emotional-states skipped because no emotional/self-reflection term matched"
  }
  return domainReason + emotionalReason
}

private fun bestDomain(domainMatches: List<Pair<RetrievalDomain, List<String>>>): RetrievalDomain = domainMatches
  .filter { (_, matches) -> matches.isNotEmpty() }
  .maxByOrNull { it.second.size }
  ?.first
  ?: RetrievalDomain.General

private fun matchedTerms(message: String, terms: List<String>): List<String> = terms
  .filter { term -> containsTerm(message, term) }

private fun containsTerm(message: String, term: String): Boolean {
  val escaped = Regex.escape(term)
  return Regex("""(?i)(?<![a-z0-9_-])$escaped(?![a-z0-9_-])""").containsMatchIn(message)
}

private val WORK_CAPMO_TERMS: List<String> = listOf(
  "capmo",
)

private val WORK_SKILL_BILL_TERMS: List<String> = listOf(
  "skill-bill",
  "skill bill",
  "skillbill",
  "skill",
  "skills",
  "agent workflow",
)

private val WORK_READIAN_TERMS: List<String> = listOf(
  "readian",
  "editorial",
  "assignment desk",
  "article",
  "articles",
  "news",
)

private val WORK_CONTEXT_APP_TERMS: List<String> = listOf(
  "context-app",
  "context app",
  "context",
  "shelf",
  "desktop app",
  "macos app",
)

private val PERSONAL_TERMS: List<String> = listOf(
  "family",
  "health",
  "habit",
  "finances",
  "purchase",
)

private val CREATIVE_MUSIC_TERMS: List<String> = listOf(
  "creative",
  "writing",
  "story",
  "music",
  "art",
  "design",
  "song",
  "audio",
  "recording",
  "mixdown",
  "bass",
  "drums",
  "guitar",
  "track",
  "arrangement",
  "mp3",
  "studio",
  "compose",
  "paint",
  "draw",
  "sketch",
  "band",
  "instrument",
)

private val EMOTIONAL_TERMS: List<String> = listOf(
  "emotion",
  "anxious",
  "anxiety",
  "frustrated",
  "frustration",
  "excited",
  "curiosity",
  "self-reflection",
  "reflection",
  "mood",
  "feeling",
  "feelings",
)
