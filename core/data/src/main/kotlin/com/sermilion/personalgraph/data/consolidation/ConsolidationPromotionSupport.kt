package com.sermilion.personalgraph.data.consolidation

import com.sermilion.personalgraph.domain.layout.VaultLayout
import com.sermilion.personalgraph.domain.model.Confidence
import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.model.PatternNode
import com.sermilion.personalgraph.domain.model.StateCategory
import com.sermilion.personalgraph.domain.model.StateNode
import com.sermilion.personalgraph.domain.model.VaultNode
import com.sermilion.personalgraph.domain.repository.GraduatedObservation
import com.sermilion.personalgraph.domain.repository.MergedDuplicate

internal fun matchingDurableInContext(
  group: List<Observation>,
  durableStates: List<StateNode>,
): StateNode? {
  val reference = group.first()
  return durableStates.firstOrNull { candidate ->
    fingerprint(candidate) == reference.fingerprint && contextKey(candidate) == reference.contextKey
  }
}

internal fun matchingDurableByFingerprint(
  group: List<Observation>,
  durableStates: List<StateNode>,
): StateNode? {
  val reference = group.first()
  return durableStates.firstOrNull { candidate -> fingerprint(candidate) == reference.fingerprint }
}

internal fun meetsPatternPromotionThreshold(group: List<Observation>, match: StateNode?): Boolean {
  val domainsSeen = (group.flatMap { it.domains } + match?.let(::domainsFor).orEmpty())
    .filterNot { it == GENERAL_CONTEXT }
    .distinct()
  return domainsSeen.size >= REQUIRED_PATTERN_DOMAINS ||
    group.size + (match?.occurrenceCount ?: 0) >= REQUIRED_PATTERN_OCCURRENCES
}

internal fun durableObservationsByFingerprint(durable: List<VaultNode>): Map<Fingerprint, List<Observation>> = durable
  .filterIsInstance<StateNode>()
  .map { observationFor(it) }
  .groupBy { it.fingerprint }

internal fun mergeDurableNodes(durable: List<VaultNode>, promotedNodes: List<StateNode>): List<VaultNode> {
  val promotedById = promotedNodes.associateBy { it.id.value }
  return durable.filterNot { promotedById.containsKey(it.id.value) } + promotedNodes
}

internal fun patternChanged(existing: PatternNode?, next: PatternNode): Boolean = existing == null ||
  existing.evidenceCount != next.evidenceCount ||
  existing.domainsSeenIn != next.domainsSeenIn ||
  existing.sourceIds.map { it.value }.toSet() != next.sourceIds.map { it.value }.toSet()

internal fun List<Observation>.domainsSeen(): List<String> = flatMap { it.domains }
  .filterNot { it == GENERAL_CONTEXT }
  .distinct()
  .sorted()

internal fun StateNode.withPatternLink(patternId: NodeId): StateNode? {
  val wikilink = "[[${patternId.value}]]"
  if (patternLinks.any { it.value == patternId.value } && body.contains(wikilink)) return null
  return copy(
    body = appendPatternLink(body, patternId),
    links = mergeNodeIds(links, listOf(patternId)),
    patternLinks = mergeNodeIds(patternLinks, listOf(patternId)),
  )
}

internal fun observationFor(node: StateNode): Observation = Observation(
  node = node,
  fingerprint = fingerprint(node),
  contextKey = contextKey(node),
  domains = domainsFor(node),
)

internal fun targetPatternId(fingerprint: Fingerprint): NodeId = NodeId(
  "${VaultLayout.BRANCH_PATTERNS}/${slugFor(fingerprint.claim)}",
)

internal fun occurrenceCountFor(node: StateNode): Int = maxOf(node.occurrenceCount, node.sourceIds.size, 1)

internal fun hypothesisFor(node: StateNode): String = node.body
  .lineSequence()
  .firstOrNull { it.isNotBlank() }
  ?.trim()
  ?: fingerprint(node).claim

internal fun targetStateId(node: StateNode): NodeId {
  val branch = when (node.category) {
    StateCategory.Preference -> VaultLayout.BRANCH_STATE_PREFERENCES
    StateCategory.Role -> VaultLayout.BRANCH_STATE_ROLES
    StateCategory.Knowledge -> VaultLayout.BRANCH_STATE_KNOWLEDGE
    StateCategory.Fact -> VaultLayout.BRANCH_STATE_KNOWLEDGE
  }
  return NodeId("$branch/${slugFor(fingerprint(node).claim)}")
}

internal fun strongestConfidence(values: List<Confidence>): Confidence = when {
  values.contains(Confidence.High) -> Confidence.High
  values.contains(Confidence.Medium) -> Confidence.Medium
  else -> Confidence.Low
}

internal data class Fingerprint(
  val category: StateCategory,
  val claim: String,
)

internal data class ContextKey(
  val fingerprint: Fingerprint,
  val context: String,
)

internal data class Observation(
  val node: StateNode,
  val fingerprint: Fingerprint,
  val contextKey: ContextKey,
  val domains: List<String>,
)

internal class PromotionAccumulator {
  val graduated: MutableList<GraduatedObservation> = mutableListOf()
  val mergedDuplicates: MutableList<MergedDuplicate> = mutableListOf()
  val promotedNodes: MutableList<StateNode> = mutableListOf()
  val processedSourceIds: MutableSet<String> = mutableSetOf()

  fun toResult(): PromotionResult = PromotionResult(
    graduated = graduated,
    mergedDuplicates = mergedDuplicates,
    promotedNodes = promotedNodes,
  )
}

internal data class PromotionResult(
  val graduated: List<GraduatedObservation>,
  val mergedDuplicates: List<MergedDuplicate>,
  val promotedNodes: List<StateNode>,
)

internal const val REQUIRED_REPEATED_SIGHTINGS: Int = 2
internal const val REQUIRED_PATTERN_DOMAINS: Int = 2
internal const val REQUIRED_PATTERN_OCCURRENCES: Int = 3
internal const val MAX_CONSOLIDATION_RESULTS: Int = 2000
internal val CONSOLIDATION_DURABLE_BRANCHES: List<String> = listOf(
  VaultLayout.BRANCH_STATE,
  VaultLayout.BRANCH_DOMAINS,
  VaultLayout.BRANCH_PATTERNS,
  VaultLayout.BRANCH_EMOTIONAL_STATES,
)
