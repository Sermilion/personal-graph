package com.sermilion.personalgraph.data.consolidation

import com.sermilion.personalgraph.domain.layout.VaultLayout
import com.sermilion.personalgraph.domain.model.EmotionalStateNode
import com.sermilion.personalgraph.domain.model.EpisodeNode
import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.model.PatternNode
import com.sermilion.personalgraph.domain.model.StateNode
import com.sermilion.personalgraph.domain.model.SubjectNode
import com.sermilion.personalgraph.domain.model.VaultNode
import java.security.MessageDigest

internal fun VaultNode.withContradiction(sourceId: NodeId, reason: String): VaultNode {
  val contradictedBy = mergeNodeIds(contradictedByIds(), listOf(sourceId))
  val nextBody = appendContradictionBody(body, sourceId, reason)
  return when (this) {
    is StateNode -> copy(body = nextBody, contradictedBy = contradictedBy)
    is EpisodeNode -> copy(body = nextBody, contradictedBy = contradictedBy)
    is PatternNode -> copy(body = nextBody, contradictedBy = contradictedBy)
    is SubjectNode -> copy(body = nextBody)
    is EmotionalStateNode -> copy(body = nextBody, contradictedBy = contradictedBy)
  }
}

internal fun appendPatternLink(body: String, patternId: NodeId): String {
  val wikilink = "[[${patternId.value}]]"
  if (body.contains(wikilink)) return body
  val trimmed = body.trimEnd()
  return if (trimmed.isEmpty()) "Pattern: $wikilink\n" else "$trimmed\n\nPattern: $wikilink\n"
}

internal fun fingerprint(node: StateNode): Fingerprint = Fingerprint(
  category = node.category,
  claim = normalizeClaim(node.body),
)

internal fun contextKey(node: StateNode): ContextKey = ContextKey(
  fingerprint = fingerprint(node),
  context = domainsFor(node).sorted().joinToString("|").ifBlank { GENERAL_CONTEXT },
)

internal fun domainsFor(node: VaultNode): List<String> {
  val domains = mutableSetOf<String>()
  for (link in node.links + nodeSourceIds(node)) {
    domainFromNodeId(link)?.let { domains.add(it) }
  }
  DOMAIN_HINT_REGEX.findAll(node.body).forEach { match -> domains.add(match.groupValues[1].trim('/')) }
  return domains.ifEmpty { setOf(GENERAL_CONTEXT) }.toList()
}

internal fun slugFor(claim: String): String {
  val base = claim
    .replace(NON_SLUG_TEXT_REGEX, "-")
    .trim('-')
    .take(MAX_SLUG_LENGTH)
    .trim('-')
    .ifEmpty { SLUG_FALLBACK }
  return "$base-${shortHash(claim)}"
}

internal fun mergeNodeIds(left: List<NodeId>, right: List<NodeId>): List<NodeId> {
  val seen = mutableSetOf<String>()
  val result = mutableListOf<NodeId>()
  for (id in left + right) {
    if (seen.add(id.value)) result.add(id)
  }
  return result
}

internal fun contradicts(source: StateNode, candidate: StateNode): Boolean {
  val sourceClaim = contradictionClaim(source)
  val candidateClaim = contradictionClaim(candidate)
  return candidate.category == source.category &&
    candidateClaim.stem == sourceClaim.stem &&
    candidateClaim.polarity != sourceClaim.polarity
}

internal fun contradictionClaim(node: StateNode): ContradictionClaim {
  val polarity = if (NEGATIVE_MARKER_REGEX.containsMatchIn(node.body.lowercase())) {
    ClaimPolarity.Negative
  } else {
    ClaimPolarity.Positive
  }
  val stem = normalizeClaim(node.body)
    .replace(PREFERENCE_STEM_REGEX, "prefer")
    .replace(NEGATION_STEM_REGEX, " ")
    .trim()
    .replace(WHITESPACE_REGEX, " ")
  return ContradictionClaim(stem = stem, polarity = polarity)
}

private fun VaultNode.contradictedByIds(): List<NodeId> = when (this) {
  is StateNode -> contradictedBy
  is EpisodeNode -> contradictedBy
  is PatternNode -> contradictedBy
  is SubjectNode -> emptyList()
  is EmotionalStateNode -> contradictedBy
}

private fun appendContradictionBody(body: String, sourceId: NodeId, reason: String): String {
  val trimmed = body.trimEnd()
  val annotation = "Contradiction noted from [[${sourceId.value}]]: ${reason.trim()}"
  return when {
    trimmed.contains(annotation) -> body
    trimmed.isEmpty() -> "$annotation\n"
    else -> "$trimmed\n\n$annotation\n"
  }
}

private fun nodeSourceIds(node: VaultNode): List<NodeId> = when (node) {
  is StateNode -> node.sourceIds
  is PatternNode -> node.sourceIds
  is SubjectNode -> node.sourceIds
  else -> emptyList()
}

private fun domainFromNodeId(id: NodeId): String? {
  if (!id.value.startsWith("${VaultLayout.BRANCH_DOMAINS}/")) return null
  val withoutPrefix = id.value.removePrefix("${VaultLayout.BRANCH_DOMAINS}/")
  return withoutPrefix
    .substringBefore("/events/")
    .substringBefore("/notes/")
    .substringBefore("/subjects/")
    .takeIf { it.isNotBlank() && it != withoutPrefix }
}

private fun normalizeClaim(body: String): String = body
  .replace(WIKILINK_REGEX) { match -> match.groupValues[1] }
  .lowercase()
  .replace(NON_CLAIM_TEXT_REGEX, " ")
  .trim()
  .replace(WHITESPACE_REGEX, " ")

private fun shortHash(value: String): String {
  val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
  return digest.take(HASH_BYTES).joinToString("") { "%02x".format(it) }
}

internal data class ContradictionClaim(
  val stem: String,
  val polarity: ClaimPolarity,
)

internal enum class ClaimPolarity {
  Positive,
  Negative,
}

internal const val GENERAL_CONTEXT: String = "general"
private const val HASH_BYTES: Int = 4
private const val MAX_SLUG_LENGTH: Int = 56
private const val SLUG_FALLBACK: String = "observation"
private val WIKILINK_REGEX: Regex = Regex("""\[\[([^\]|]+)(?:\|[^\]]+)?]]""")
private val NON_CLAIM_TEXT_REGEX: Regex = Regex("""[^a-z0-9/_ -]+""")
private val NON_SLUG_TEXT_REGEX: Regex = Regex("""[^a-z0-9]+""")
private val WHITESPACE_REGEX: Regex = Regex("""\s+""")
private val DOMAIN_HINT_REGEX: Regex = Regex("""(?i)\bdomain\s*[:=]\s*([a-z0-9/_-]+)""")
private val NEGATIVE_MARKER_REGEX: Regex = Regex(
  """\b(no|not|never|avoid|against|dislike|hate|cannot|can't|does not|do not|don't|won't)\b""",
)
private val NEGATION_STEM_REGEX: Regex = Regex(
  """\b(no|not|never|avoid|against|dislike|hate|cannot|can t|does not|do not|don t|won t)\b""",
)
private val PREFERENCE_STEM_REGEX: Regex = Regex("""\bprefers\b""")
