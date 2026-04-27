package com.sermilion.personalgraph.data.capture

import com.sermilion.personalgraph.common.di.AppScope
import com.sermilion.personalgraph.domain.capture.BacklinkStatus
import com.sermilion.personalgraph.domain.capture.CaptureResult
import com.sermilion.personalgraph.domain.capture.FlagSensitiveArgs
import com.sermilion.personalgraph.domain.capture.PayloadKind
import com.sermilion.personalgraph.domain.capture.SubjectHubStatus
import com.sermilion.personalgraph.domain.capture.VaultCaptureService
import com.sermilion.personalgraph.domain.capture.WriteEpisodeArgs
import com.sermilion.personalgraph.domain.capture.WriteStateArgs
import com.sermilion.personalgraph.domain.capture.WriteToStagingArgs
import com.sermilion.personalgraph.domain.layout.VaultLayout
import com.sermilion.personalgraph.domain.layout.VaultPolicy
import com.sermilion.personalgraph.domain.model.EmotionalStateNode
import com.sermilion.personalgraph.domain.model.EpisodeNode
import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.model.PatternNode
import com.sermilion.personalgraph.domain.model.StateCategory
import com.sermilion.personalgraph.domain.model.StateNode
import com.sermilion.personalgraph.domain.model.SubjectNode
import com.sermilion.personalgraph.domain.model.VaultNode
import com.sermilion.personalgraph.domain.repository.VaultRepository
import com.sermilion.personalgraph.domain.repository.WriteOutcome
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.tatarka.inject.annotations.Inject

@AppScope
@Inject
class PersonalGraphVaultCaptureService(
  private val repository: VaultRepository,
  private val clock: Clock,
) : VaultCaptureService {

  private val logger = KotlinLogging.logger {}

  override suspend fun writeStateObservation(args: WriteStateArgs): CaptureResult {
    val now = clock.now()
    val singularRejection = rejectSingularStatePrefix(args.id)
    if (singularRejection != null) return singularRejection
    val targetId = if (args.sensitive) {
      buildSensitiveTargetId(args.id)
    } else {
      buildStateTargetId(args.id, args.category)
    }
    val nodeId = parseNodeId(targetId)
      ?: return CaptureResult.InvalidInput(FIELD_ID, "computed target id is invalid: $targetId")
    val node = StateNode(
      id = nodeId,
      createdAt = now,
      updatedAt = now,
      body = args.body,
      links = args.links,
      category = args.category,
      confidence = args.confidence,
    )
    return persistPrimary(node)
  }

  override suspend fun writeEpisode(args: WriteEpisodeArgs): CaptureResult {
    val targetId = if (args.sensitive) {
      buildSensitiveTargetId(args.id)
    } else {
      buildEpisodeTargetId(args.domain, args.id)
    }
    val nodeId = parseNodeId(targetId)
      ?: return CaptureResult.InvalidInput(FIELD_ID, "computed target id is invalid: $targetId")
    val node = EpisodeNode(
      id = nodeId,
      createdAt = args.date,
      updatedAt = args.date,
      body = args.body,
      links = args.linked,
      date = args.date,
      episodeType = args.episodeType,
      domain = args.domain,
      topic = args.topic,
      intensity = args.intensity,
    )
    return persistEpisodeWithIndexes(node, args.sensitive)
  }

  private suspend fun persistEpisodeWithIndexes(node: EpisodeNode, sensitive: Boolean): CaptureResult {
    val primary = persistPrimary(node)
    if (primary !is CaptureResult.Created) return primary
    if (sensitive) {
      return primary.copy(
        backlinkStatus = BacklinkStatus.Skipped,
        subjectHubStatus = SubjectHubStatus.Skipped,
      )
    }
    val subjectHubResult = upsertSubjectHub(node)
    val backlinkResult = writeTimelineBacklink(node, subjectHubResult.first)
    return primary.copy(
      backlinkId = backlinkResult.first,
      backlinkStatus = backlinkResult.second,
      subjectHubId = subjectHubResult.first,
      subjectHubStatus = subjectHubResult.second,
    )
  }

  override suspend fun writeToStaging(args: WriteToStagingArgs): CaptureResult {
    val targetId = "${VaultLayout.BRANCH_STAGING_OBSERVATIONS}/${slugify(args.id)}"
    val nodeId = parseNodeId(targetId)
      ?: return CaptureResult.InvalidInput(FIELD_ID, "computed target id is invalid: $targetId")
    val now = clock.now()
    val node = StateNode(
      id = nodeId,
      createdAt = now,
      updatedAt = now,
      body = args.body,
      links = args.links,
      category = args.category,
      confidence = args.confidence,
    )
    return persistPrimary(node)
  }

  override suspend fun flagSensitive(args: FlagSensitiveArgs): CaptureResult {
    val validation = validateFlagSensitive(args)
    if (validation is FlagSensitiveValidation.Invalid) return validation.result
    val ok = validation as FlagSensitiveValidation.Ok
    val moveOutcome = repository.moveNode(ok.sourceId, VaultLayout.BRANCH_STAGING_SENSITIVE)
    return mapMoveOutcome(moveOutcome, ok.sourceId, ok.fallbackId, args.targetPath)
  }

  private suspend fun validateFlagSensitive(args: FlagSensitiveArgs): FlagSensitiveValidation {
    val precheck = precheckFlagSensitive(args.targetPath) ?: return resolveFlagSensitive(args)
    return precheck
  }

  private suspend fun resolveFlagSensitive(args: FlagSensitiveArgs): FlagSensitiveValidation {
    val sourceId = parseNodeId(args.targetPath)
      ?: return FlagSensitiveValidation.Invalid(
        CaptureResult.InvalidInput(FIELD_TARGET_PATH, "invalid target_path"),
      )
    val existing = repository.findNode(sourceId)
      ?: return FlagSensitiveValidation.Invalid(CaptureResult.NotFound(args.targetPath))
    return validateExistingForFlag(existing, args, sourceId)
  }

  private suspend fun persistPrimary(node: VaultNode): CaptureResult {
    val outcome = repository.writeNode(node)
    return when (outcome) {
      WriteOutcome.Applied -> CaptureResult.Created(id = node.id)
      WriteOutcome.NotFound -> CaptureResult.Failed("not_found")
      WriteOutcome.Conflict -> CaptureResult.Failed("conflict")
      is WriteOutcome.Failed -> CaptureResult.Failed(outcome.reason)
    }
  }

  private suspend fun writeTimelineBacklink(
    episode: EpisodeNode,
    subjectHubId: NodeId?,
  ): Pair<NodeId?, BacklinkStatus> {
    val date = episode.date.toLocalDateTime(TimeZone.UTC)
    val yearMonth = "%04d-%02d".format(date.year, date.monthNumber)
    val datePrefix = "%04d-%02d-%02d".format(date.year, date.monthNumber, date.dayOfMonth)
    val backlinkPath = "${VaultLayout.timeline(yearMonth)}/$datePrefix-${slugify(episode.topic)}"
    val backlinkId = parseNodeId(backlinkPath)
      ?: return null to BacklinkStatus.Failed
    val timelineLinks = listOfNotNull(episode.id, subjectHubId)
    val backlinkBody = buildString {
      appendLine("[[${episode.id.value}]]")
      subjectHubId?.let { appendLine("[[${it.value}]]") }
    }
    val backlinkNode = EpisodeNode(
      id = backlinkId,
      createdAt = episode.date,
      updatedAt = episode.date,
      body = backlinkBody,
      links = timelineLinks,
      date = episode.date,
      episodeType = episode.episodeType,
      domain = episode.domain,
      topic = episode.topic,
      intensity = episode.intensity,
    )
    val outcome = repository.writeNode(backlinkNode)
    return when (outcome) {
      WriteOutcome.Applied -> backlinkId to BacklinkStatus.Ok
      else -> {
        logger.warn { "timeline backlink write failed for episode=${episode.id.value} outcome=$outcome" }
        backlinkId to BacklinkStatus.Failed
      }
    }
  }

  private suspend fun upsertSubjectHub(episode: EpisodeNode): Pair<NodeId?, SubjectHubStatus> {
    val existing = resolveExistingSubjectHub(episode)
    val target = existing?.appendEpisodeEvidence(episode) ?: newSubjectHub(episode)
    val status = when (repository.writeNode(target)) {
      WriteOutcome.Applied -> if (existing == null) SubjectHubStatus.Created else SubjectHubStatus.Updated
      else -> SubjectHubStatus.Failed
    }
    return target.id to status
  }

  private suspend fun resolveExistingSubjectHub(episode: EpisodeNode): SubjectNode? {
    for (link in episode.links) {
      val linkedNode = repository.findNode(link) as? SubjectNode ?: continue
      if (linkedNode.domain == episode.domain) return linkedNode
    }
    return repository.findSubjectHub(
      domain = episode.domain,
      subjectKey = episode.topic,
      aliases = listOf(episode.id.value.substringAfterLast('/')),
    )
  }
}

private fun rejectSingularStatePrefix(id: String): CaptureResult? {
  val normalizedId = id.trim().lowercase()
  for ((singularPrefix, canonicalPrefix) in SINGULAR_STATE_PREFIX_REJECTIONS) {
    if (!normalizedId.startsWith(singularPrefix)) continue
    val leaf = normalizedId.removePrefix(singularPrefix)
    if (leaf.isBlank()) {
      return CaptureResult.InvalidInput(
        field = FIELD_ID,
        reason = "leaf is required after canonical state prefix",
        expected = "$canonicalPrefix<leaf>",
      )
    }
    return CaptureResult.InvalidInput(
      field = FIELD_ID,
      reason = "singular state prefix is not allowed; use canonical plural form",
      expected = "$canonicalPrefix$leaf",
    )
  }
  return null
}

private fun precheckFlagSensitive(targetPath: String): FlagSensitiveValidation? = when {
  VaultPolicy.isReadBlocked(targetPath) ->
    FlagSensitiveValidation.Invalid(
      CaptureResult.PermissionDenied(REASON_PEOPLE_BLOCKED),
    )
  parseNodeId(targetPath) == null ->
    FlagSensitiveValidation.Invalid(
      CaptureResult.InvalidInput(FIELD_TARGET_PATH, "invalid target_path"),
    )
  else -> null
}

private fun validateExistingForFlag(
  existing: VaultNode,
  args: FlagSensitiveArgs,
  sourceId: NodeId,
): FlagSensitiveValidation {
  val expectedKind = expectedPayloadKind(existing)
  if (expectedKind != args.payloadKind) {
    return FlagSensitiveValidation.Invalid(
      CaptureResult.InvalidInput(
        field = FIELD_PAYLOAD_KIND,
        reason = "payload_kind does not match node type",
        expected = expectedKind.name,
      ),
    )
  }
  val newId = parseNodeId(buildSensitiveTargetId(existing.id.value))
    ?: return FlagSensitiveValidation.Invalid(
      CaptureResult.InvalidInput(FIELD_PAYLOAD_KIND, "computed sensitive id is invalid"),
    )
  return FlagSensitiveValidation.Ok(
    sourceId = sourceId,
    fallbackId = newId,
  )
}

private fun mapMoveOutcome(
  moveOutcome: WriteOutcome,
  sourceId: NodeId,
  fallbackId: NodeId,
  targetPath: String,
): CaptureResult = when (moveOutcome) {
  WriteOutcome.Applied -> {
    val movedTail = sourceId.value.substringAfterLast('/')
    val movedId = parseNodeId("${VaultLayout.BRANCH_STAGING_SENSITIVE}/$movedTail") ?: fallbackId
    CaptureResult.Created(id = movedId)
  }
  WriteOutcome.NotFound -> CaptureResult.NotFound(targetPath)
  WriteOutcome.Conflict -> CaptureResult.Failed("conflict")
  is WriteOutcome.Failed -> CaptureResult.Failed(moveOutcome.reason)
}

private fun expectedPayloadKind(node: VaultNode): PayloadKind = when (node) {
  is StateNode -> PayloadKind.State
  is EpisodeNode -> PayloadKind.Episode
  is PatternNode -> PayloadKind.Pattern
  is SubjectNode -> PayloadKind.Subject
  is EmotionalStateNode -> PayloadKind.EmotionalState
}

private fun buildStateTargetId(id: String, category: StateCategory): String {
  if (id.startsWith("${VaultLayout.BRANCH_STATE_PREFERENCES}/") ||
    id.startsWith("${VaultLayout.BRANCH_STATE_ROLES}/") ||
    id.startsWith("${VaultLayout.BRANCH_STATE_KNOWLEDGE}/")
  ) {
    return id
  }
  val branch = when (category) {
    StateCategory.Preference -> VaultLayout.BRANCH_STATE_PREFERENCES
    StateCategory.Role -> VaultLayout.BRANCH_STATE_ROLES
    StateCategory.Knowledge -> VaultLayout.BRANCH_STATE_KNOWLEDGE
    StateCategory.Fact -> VaultLayout.BRANCH_STATE_KNOWLEDGE
  }
  return "$branch/${slugify(id)}"
}

private fun buildEpisodeTargetId(domain: String, id: String): String {
  if (id.startsWith("${VaultLayout.domainEvents(domain)}/")) return id
  return "${VaultLayout.domainEvents(domain)}/${slugify(id.substringAfterLast('/'))}"
}

private fun buildSensitiveTargetId(id: String): String {
  val tail = id.substringAfterLast('/')
  return "${VaultLayout.BRANCH_STAGING_SENSITIVE}/${slugify(tail)}"
}

private fun slugify(value: String): String = value.lowercase()
  .replace(SLUG_NORMALIZE_REGEX, "-")
  .trim('-')
  .ifEmpty { SLUG_FALLBACK }

private fun parseNodeId(value: String): NodeId? = runCatching { NodeId(value) }.getOrNull()

private fun newSubjectHub(episode: EpisodeNode): SubjectNode {
  val canonicalSubject = slugify(episode.topic)
  val targetId = NodeId(VaultLayout.subjectHub(episode.domain, canonicalSubject))
  val evidenceBody = subjectEvidenceEntry(episode)
  return SubjectNode(
    id = targetId,
    createdAt = episode.date,
    updatedAt = episode.date,
    body = "## Summary\nCanonical subject hub for ${episode.topic.trim()}.\n\n## Evidence\n$evidenceBody",
    links = mergeNodeIds(episode.links, listOf(episode.id)),
    domain = episode.domain,
    subject = canonicalSubject,
    aliases = listOfNotNull(
      episode.topic.trim().takeUnless { slugify(it) == canonicalSubject || it.isBlank() },
    ),
    evidenceCount = 1,
    sourceIds = listOf(episode.id),
  )
}

private fun SubjectNode.appendEpisodeEvidence(episode: EpisodeNode): SubjectNode {
  if (sourceIds.any { it.value == episode.id.value } || body.contains("[[${episode.id.value}]]")) {
    return copy(
      updatedAt = episode.date,
      links = mergeNodeIds(links, episode.links + episode.id),
      sourceIds = mergeNodeIds(sourceIds, listOf(episode.id)),
    )
  }
  val appendedBody = appendEvidenceEntry(body, subjectEvidenceEntry(episode))
  val canonicalTopic = slugify(episode.topic)
  val nextAliases = (aliases + episode.topic.trim())
    .map(String::trim)
    .filter { it.isNotEmpty() && slugify(it) != subject }
    .distinct()
  return copy(
    updatedAt = episode.date,
    body = appendedBody,
    links = mergeNodeIds(links, episode.links + episode.id),
    aliases = nextAliases,
    evidenceCount = evidenceCount + 1,
    sourceIds = mergeNodeIds(sourceIds, listOf(episode.id)),
    subject = if (subject.isBlank()) canonicalTopic else subject,
  )
}

private fun subjectEvidenceEntry(episode: EpisodeNode): String {
  val date = episode.date.toLocalDateTime(TimeZone.UTC).date.toString()
  val summarySuffix = firstContentLine(episode.body)?.let { " — $it" }.orEmpty()
  return "- $date: [[${episode.id.value}]]$summarySuffix\n"
}

private fun appendEvidenceEntry(body: String, entry: String): String {
  val trimmed = body.trimEnd()
  if (trimmed.contains(entry.trim())) return body
  val evidenceHeader = "## Evidence"
  return when {
    trimmed.isEmpty() -> "$evidenceHeader\n$entry"
    trimmed.contains(evidenceHeader) -> "$trimmed\n$entry"
    else -> "$trimmed\n\n$evidenceHeader\n$entry"
  }
}

private fun firstContentLine(body: String): String? = body.lineSequence()
  .map(String::trim)
  .firstOrNull { it.isNotEmpty() }
  ?.take(MAX_SUBJECT_EVIDENCE_SUMMARY_LENGTH)

private fun mergeNodeIds(left: List<NodeId>, right: List<NodeId>): List<NodeId> {
  val seen = mutableSetOf<String>()
  val result = mutableListOf<NodeId>()
  for (id in left + right) {
    if (seen.add(id.value)) result.add(id)
  }
  return result
}

private val SLUG_NORMALIZE_REGEX: Regex = Regex("[^a-z0-9]+")
private const val SLUG_FALLBACK: String = "untitled"
private const val MAX_SUBJECT_EVIDENCE_SUMMARY_LENGTH: Int = 140
private const val REASON_PEOPLE_BLOCKED: String = "people/ is read-blocked by default"
private const val FIELD_ID: String = "id"
private const val FIELD_TARGET_PATH: String = "target_path"
private const val FIELD_PAYLOAD_KIND: String = "payload_kind"

private val SINGULAR_STATE_PREFIX_REJECTIONS: List<Pair<String, String>> =
  StateCategory.entries.mapNotNull { category ->
    val canonicalPrefix = canonicalPluralPrefixFor(category)
    val singularPrefix = "${VaultLayout.BRANCH_STATE}/${category.name.lowercase()}/"
    if (singularPrefix == canonicalPrefix) null else singularPrefix to canonicalPrefix
  }

private fun canonicalPluralPrefixFor(category: StateCategory): String = when (category) {
  StateCategory.Preference -> "${VaultLayout.BRANCH_STATE_PREFERENCES}/"
  StateCategory.Role -> "${VaultLayout.BRANCH_STATE_ROLES}/"
  StateCategory.Knowledge -> "${VaultLayout.BRANCH_STATE_KNOWLEDGE}/"
  StateCategory.Fact -> "${VaultLayout.BRANCH_STATE_KNOWLEDGE}/"
}

private sealed interface FlagSensitiveValidation {
  data class Ok(val sourceId: NodeId, val fallbackId: NodeId) : FlagSensitiveValidation
  data class Invalid(val result: CaptureResult) : FlagSensitiveValidation
}
