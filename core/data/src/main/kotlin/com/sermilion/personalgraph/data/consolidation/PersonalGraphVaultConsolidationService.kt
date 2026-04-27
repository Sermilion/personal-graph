package com.sermilion.personalgraph.data.consolidation

import com.sermilion.personalgraph.common.di.AppScope
import com.sermilion.personalgraph.common.dispatcher.DispatcherProvider
import com.sermilion.personalgraph.data.repository.canonicalSubjectKey
import com.sermilion.personalgraph.domain.layout.VaultLayout
import com.sermilion.personalgraph.domain.model.EpisodeNode
import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.model.PatternNode
import com.sermilion.personalgraph.domain.model.StateNode
import com.sermilion.personalgraph.domain.model.SubjectNode
import com.sermilion.personalgraph.domain.model.VaultNode
import com.sermilion.personalgraph.domain.repository.AnnotatedContradiction
import com.sermilion.personalgraph.domain.repository.ConsolidationReport
import com.sermilion.personalgraph.domain.repository.ConsolidationRequest
import com.sermilion.personalgraph.domain.repository.ConsolidationService
import com.sermilion.personalgraph.domain.repository.GraduatedObservation
import com.sermilion.personalgraph.domain.repository.MergedDuplicate
import com.sermilion.personalgraph.domain.repository.MigratedLegacyNote
import com.sermilion.personalgraph.domain.repository.PromotedPattern
import com.sermilion.personalgraph.domain.repository.VaultRepository
import com.sermilion.personalgraph.domain.repository.WriteOutcome
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.tatarka.inject.annotations.Inject

@AppScope
@Inject
class PersonalGraphVaultConsolidationService(
  private val repository: VaultRepository,
  private val dispatcherProvider: DispatcherProvider,
  private val clock: Clock,
) : ConsolidationService {

  private val logger = KotlinLogging.logger {}

  override suspend fun consolidate(
    request: ConsolidationRequest,
  ): ConsolidationReport = withContext(dispatcherProvider.io) {
    if (request.includeSensitiveStaging || request.includePeople) {
      logger.warn { "Consolidation ignores sensitive staging and people branches by default" }
    }
    val initialDurable = listConsolidationCandidates()
    val staged = repository.listStagedObservations()
    val contradictionScan = annotateContradictions(staged, initialDurable)
    val durable = if (contradictionScan.changed.isEmpty()) initialDurable else listConsolidationCandidates()
    val promotion = promoteRepeatedSightings(
      staged = staged.filterNot { contradictionScan.blockedSourceIds.contains(it.id.value) },
      durable = durable,
    )
    val patterns = promotePatterns(mergeDurableNodes(durable, promotion.promotedNodes))
    val migrations = migrateLegacyDomainNotes(durable)
    ConsolidationReport(
      graduated = promotion.graduated,
      mergedDuplicates = promotion.mergedDuplicates,
      promotedPatterns = patterns,
      annotatedContradictions = contradictionScan.changed,
      migratedLegacyNotes = migrations,
    )
  }

  private suspend fun listConsolidationCandidates(): List<VaultNode> = CONSOLIDATION_DURABLE_BRANCHES
    .flatMap { repository.listNodesInBranch(it) }
    .take(MAX_CONSOLIDATION_RESULTS)

  private suspend fun promoteRepeatedSightings(
    staged: List<StateNode>,
    durable: List<VaultNode>,
  ): PromotionResult {
    val observations = staged.map { observationFor(it) }
    val accumulator = PromotionAccumulator()
    val durableStates = durable.filterIsInstance<StateNode>()
    promoteContextGroups(observations, durableStates, accumulator)
    promoteFingerprintThresholdGroups(observations, durableStates, accumulator)
    return accumulator.toResult()
  }

  private suspend fun promoteContextGroups(
    observations: List<Observation>,
    durableStates: List<StateNode>,
    accumulator: PromotionAccumulator,
  ) {
    promoteEligibleGroups(
      groups = observations.groupBy { it.contextKey }.values,
      durableStates = durableStates,
      accumulator = accumulator,
      durableMatch = ::matchingDurableInContext,
      eligible = { group, match -> group.size + (match?.occurrenceCount ?: 0) >= REQUIRED_REPEATED_SIGHTINGS },
    )
  }

  private suspend fun promoteFingerprintThresholdGroups(
    observations: List<Observation>,
    durableStates: List<StateNode>,
    accumulator: PromotionAccumulator,
  ) {
    promoteEligibleGroups(
      groups = observations.groupBy { it.fingerprint }.values,
      durableStates = durableStates,
      accumulator = accumulator,
      durableMatch = ::matchingDurableByFingerprint,
      eligible = ::meetsPatternPromotionThreshold,
    )
  }

  private suspend fun promoteEligibleGroups(
    groups: Iterable<List<Observation>>,
    durableStates: List<StateNode>,
    accumulator: PromotionAccumulator,
    durableMatch: (List<Observation>, List<StateNode>) -> StateNode?,
    eligible: (List<Observation>, StateNode?) -> Boolean,
  ) {
    for (group in groups) {
      val unprocessed = group.filterNot { accumulator.processedSourceIds.contains(it.node.id.value) }
      if (unprocessed.isNotEmpty()) {
        val match = durableMatch(unprocessed, durableStates)
        if (eligible(unprocessed, match)) promoteGroup(unprocessed, match, accumulator)
      }
    }
  }

  private suspend fun promoteGroup(
    observations: List<Observation>,
    durableMatch: StateNode?,
    accumulator: PromotionAccumulator,
  ) {
    val target = mergeIntoDurableState(observations, durableMatch)
    if (repository.writeNode(target) != WriteOutcome.Applied) return
    val sourceIds = observations.map { it.node.id }
    accumulator.promotedNodes.add(target)
    accumulator.graduated.add(
      GraduatedObservation(
        nodeId = target.id,
        sourceIds = sourceIds,
        occurrenceCount = target.occurrenceCount,
      ),
    )
    if (sourceIds.size > 1 || durableMatch != null) {
      accumulator.mergedDuplicates.add(
        MergedDuplicate(
          nodeId = target.id,
          sourceIds = sourceIds,
          mergedInto = target.id,
        ),
      )
    }
    deleteStagedSources(sourceIds)
    accumulator.processedSourceIds.addAll(sourceIds.map { it.value })
  }

  private fun mergeIntoDurableState(observations: List<Observation>, durableMatch: StateNode?): StateNode {
    val now = clock.now()
    val sourceNodes = observations.map { it.node }
    val sourceIds = mergeNodeIds(durableMatch?.sourceIds.orEmpty(), sourceNodes.map { it.id })
    val links = mergeNodeIds(durableMatch?.links.orEmpty(), sourceNodes.flatMap { it.links })
    val occurrenceCount = occurrenceCountAfterMerge(durableMatch, sourceNodes)
    return durableMatch?.copy(
      updatedAt = now,
      links = links,
      occurrenceCount = occurrenceCount,
      sourceIds = sourceIds,
      patternLinks = mergeNodeIds(durableMatch.patternLinks, sourceNodes.flatMap { it.patternLinks }),
    ) ?: newDurableState(observations, sourceNodes, links, sourceIds, occurrenceCount)
  }

  private fun newDurableState(
    observations: List<Observation>,
    sourceNodes: List<StateNode>,
    links: List<NodeId>,
    sourceIds: List<NodeId>,
    occurrenceCount: Int,
  ): StateNode = StateNode(
    id = targetStateId(observations.first().node),
    createdAt = sourceNodes.minOf { it.createdAt },
    updatedAt = clock.now(),
    body = observations.first().node.body,
    links = links,
    category = observations.first().node.category,
    confidence = strongestConfidence(sourceNodes.map { it.confidence }),
    occurrenceCount = occurrenceCount,
    sourceIds = sourceIds,
    patternLinks = sourceNodes.flatMap { it.patternLinks }.distinctBy { it.value },
  )

  private fun occurrenceCountAfterMerge(durableMatch: StateNode?, sourceNodes: List<StateNode>): Int {
    val existingSourceIds = durableMatch?.sourceIds.orEmpty().map { it.value }.toSet()
    val existingCount = durableMatch?.let { match ->
      maxOf(match.occurrenceCount, match.sourceIds.size, 1)
    } ?: 0
    val newSourceCount = sourceNodes.count { existingSourceIds.contains(it.id.value).not() }
    return existingCount + newSourceCount
  }

  private suspend fun annotateContradictions(
    staged: List<StateNode>,
    durable: List<VaultNode>,
  ): ContradictionScan {
    val durableStates = durable.filterIsInstance<StateNode>()
    val changed = mutableListOf<AnnotatedContradiction>()
    val blocked = mutableSetOf<String>()
    for (source in staged) {
      val target = durableStates.firstOrNull { candidate -> contradicts(source, candidate) }
      if (target != null) {
        blocked.add(source.id.value)
        annotateContradiction(source, target)?.let { changed.add(it) }
      }
    }
    return ContradictionScan(changed = changed, blockedSourceIds = blocked)
  }

  private suspend fun annotateContradiction(
    source: StateNode,
    target: StateNode,
  ): AnnotatedContradiction? {
    if (target.contradictedBy.any { it.value == source.id.value }) return null
    val reason = "opposite polarity for '${contradictionClaim(source).stem}'"
    val outcome = repository.writeNode(target.withContradiction(source.id, reason))
    if (outcome != WriteOutcome.Applied) {
      logger.warn {
        "Failed to annotate contradiction target=${target.id.value} source=${source.id.value} outcome=$outcome"
      }
      return null
    }
    return AnnotatedContradiction(
      nodeId = target.id,
      sourceIds = listOf(source.id),
      contradictedNodeId = target.id,
      reason = reason,
    )
  }

  private data class ContradictionScan(
    val changed: List<AnnotatedContradiction>,
    val blockedSourceIds: Set<String>,
  )

  private suspend fun promotePatterns(durable: List<VaultNode>): List<PromotedPattern> {
    val existingPatterns = durable.filterIsInstance<PatternNode>().associateBy { it.id.value }
    val promoted = mutableListOf<PromotedPattern>()
    for ((fingerprint, observations) in durableObservationsByFingerprint(durable)) {
      val patternId = targetPatternId(fingerprint)
      val patternExisted = existingPatterns.containsKey(patternId.value)
      val promotedPattern = promotePatternIfNeeded(fingerprint, observations, existingPatterns)
      promotedPattern?.let { promoted.add(it) }
      if (patternExisted || promotedPattern != null) {
        linkEvidenceToPattern(patternId, observations.map { it.node })
      }
    }
    return promoted
  }

  private suspend fun promotePatternIfNeeded(
    fingerprint: Fingerprint,
    observations: List<Observation>,
    existingPatterns: Map<String, PatternNode>,
  ): PromotedPattern? {
    val totalOccurrences = observations.sumOf { occurrenceCountFor(it.node) }
    val domainsSeen = observations.domainsSeen()
    if (domainsSeen.size < REQUIRED_PATTERN_DOMAINS && totalOccurrences < REQUIRED_PATTERN_OCCURRENCES) return null
    val patternId = targetPatternId(fingerprint)
    val sourceIds = mergeNodeIds(observations.map { it.node.id }, observations.flatMap { it.node.sourceIds })
    val existing = existingPatterns[patternId.value] ?: repository.findNode(patternId) as? PatternNode
    val nextPattern = nextPattern(patternId, observations, totalOccurrences, domainsSeen, sourceIds, existing)
    if (!patternChanged(existing, nextPattern) || repository.writeNode(nextPattern) != WriteOutcome.Applied) return null
    return PromotedPattern(
      nodeId = patternId,
      sourceIds = sourceIds,
      evidenceCount = totalOccurrences,
      domainsSeenIn = domainsSeen,
    )
  }

  private fun nextPattern(
    patternId: NodeId,
    observations: List<Observation>,
    totalOccurrences: Int,
    domainsSeen: List<String>,
    sourceIds: List<NodeId>,
    existing: PatternNode?,
  ): PatternNode = existing?.copy(
    updatedAt = clock.now(),
    links = mergeNodeIds(existing.links, observations.map { it.node.id }),
    evidenceCount = totalOccurrences,
    lastObserved = clock.now(),
    domainsSeenIn = domainsSeen,
    sourceIds = mergeNodeIds(existing.sourceIds, sourceIds),
  ) ?: PatternNode(
    id = patternId,
    createdAt = clock.now(),
    updatedAt = clock.now(),
    body = "Evidence is maintained by backlinks from durable observations.\n",
    links = observations.map { it.node.id },
    hypothesis = hypothesisFor(observations.first().node),
    evidenceCount = totalOccurrences,
    lastObserved = clock.now(),
    domainsSeenIn = domainsSeen,
    contradictedBy = emptyList(),
    sourceIds = sourceIds,
  )

  private suspend fun linkEvidenceToPattern(patternId: NodeId, nodes: List<StateNode>) {
    for (node in nodes) {
      val linked = node.withPatternLink(patternId) ?: continue
      val outcome = repository.writeNode(linked)
      if (outcome != WriteOutcome.Applied) {
        logger.warn { "Failed to link evidence=${node.id.value} to pattern=${patternId.value} outcome=$outcome" }
      }
    }
  }

  private suspend fun deleteStagedSources(sourceIds: List<NodeId>) {
    for (sourceId in sourceIds) {
      val outcome = repository.deleteNode(sourceId)
      if (outcome != WriteOutcome.Applied && outcome != WriteOutcome.NotFound) {
        logger.warn { "Failed to delete staged source=${sourceId.value} outcome=$outcome" }
      }
    }
  }

  private suspend fun migrateLegacyDomainNotes(durable: List<VaultNode>): List<MigratedLegacyNote> {
    val subjectsById = durable.filterIsInstance<SubjectNode>().associateBy { it.id.value }.toMutableMap()
    val migrated = mutableListOf<MigratedLegacyNote>()
    val legacyNotes = durable.filterIsInstance<EpisodeNode>().filter(::isLegacyDomainNote)
    for (legacy in legacyNotes) {
      migrateLegacyDomainNote(
        legacy = legacy,
        repository = repository,
        clock = clock,
        subjectsById = subjectsById,
      )?.let(migrated::add)
    }
    return migrated
  }
}

private suspend fun migrateLegacyDomainNote(
  legacy: EpisodeNode,
  repository: VaultRepository,
  clock: Clock,
  subjectsById: MutableMap<String, SubjectNode>,
): MigratedLegacyNote? {
  val targetId = NodeId(VaultLayout.subjectHub(legacy.domain, canonicalSubjectKey(legacy.topic)))
  val existing = subjectsById[targetId.value] ?: repository.findNode(targetId) as? SubjectNode
  val next = existing?.mergeLegacyNote(legacy, clock) ?: newSubjectHubFromLegacyNote(legacy, targetId, clock)
  if (repository.writeNode(next) != WriteOutcome.Applied) return null
  val deleteOutcome = repository.deleteNode(legacy.id)
  if (deleteOutcome != WriteOutcome.Applied && deleteOutcome != WriteOutcome.NotFound) return null
  subjectsById[next.id.value] = next
  return MigratedLegacyNote(
    nodeId = next.id,
    sourceIds = listOf(legacy.id),
    migratedFrom = legacy.id,
  )
}

private fun newSubjectHubFromLegacyNote(
  legacy: EpisodeNode,
  targetId: NodeId,
  clock: Clock,
): SubjectNode = SubjectNode(
  id = targetId,
  createdAt = legacy.createdAt,
  updatedAt = clock.now(),
  body = buildString {
    appendLine("## Summary")
    appendLine(firstMeaningfulLine(legacy.body) ?: "Migrated legacy note for ${legacy.topic}.")
    appendLine()
    appendLine("## Evidence")
    append(legacyEvidenceEntry(legacy))
    val trimmedBody = legacy.body.trim()
    if (trimmedBody.isNotEmpty()) {
      appendLine()
      appendLine("## Imported context")
      appendLine(trimmedBody)
    }
  },
  links = mergeNodeIds(legacy.links, listOf(legacy.id)),
  domain = legacy.domain,
  subject = canonicalSubjectKey(legacy.topic),
  aliases = listOfNotNull(legacy.topic.takeUnless { canonicalSubjectKey(it) == canonicalSubjectKey(legacy.topic) }),
  evidenceCount = 1,
  sourceIds = listOf(legacy.id),
)

private fun SubjectNode.mergeLegacyNote(legacy: EpisodeNode, clock: Clock): SubjectNode {
  val nextBody = appendLegacyEvidence(body, legacy)
  val nextAliases = (aliases + legacy.topic)
    .map(String::trim)
    .filter { it.isNotEmpty() && canonicalSubjectKey(it) != subject }
    .distinct()
  return copy(
    updatedAt = clock.now(),
    body = nextBody,
    links = mergeNodeIds(links, legacy.links + legacy.id),
    aliases = nextAliases,
    evidenceCount = evidenceCount + sourceIds.count { it.value == legacy.id.value }.let { if (it > 0) 0 else 1 },
    sourceIds = mergeNodeIds(sourceIds, listOf(legacy.id)),
  )
}

private fun appendLegacyEvidence(body: String, legacy: EpisodeNode): String {
  val entry = legacyEvidenceEntry(legacy).trimEnd()
  val trimmed = body.trimEnd()
  if (trimmed.contains(entry)) return body
  val evidenceHeader = "## Evidence"
  val contextHeader = "## Imported context"
  val importedContext = legacy.body.trim()
  val withEvidence = when {
    trimmed.isEmpty() -> "$evidenceHeader\n$entry\n"
    trimmed.contains(evidenceHeader) -> "$trimmed\n$entry\n"
    else -> "$trimmed\n\n$evidenceHeader\n$entry\n"
  }
  return if (importedContext.isEmpty()) {
    withEvidence
  } else if (withEvidence.contains(importedContext)) {
    withEvidence
  } else {
    "${withEvidence.trimEnd()}\n\n$contextHeader\n$importedContext\n"
  }
}

private fun legacyEvidenceEntry(legacy: EpisodeNode): String {
  val date = legacy.date.toLocalDateTime(TimeZone.UTC).date.toString()
  val summary = firstMeaningfulLine(legacy.body)?.let { " — $it" }.orEmpty()
  return "- $date: migrated from `${legacy.id.value}`$summary\n"
}

private fun firstMeaningfulLine(body: String): String? = body.lineSequence()
  .map(String::trim)
  .firstOrNull { it.isNotEmpty() }
