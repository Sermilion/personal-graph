package com.sermilion.personalgraph.data.capture

import com.sermilion.personalgraph.common.di.AppScope
import com.sermilion.personalgraph.domain.capture.BacklinkStatus
import com.sermilion.personalgraph.domain.capture.CaptureResult
import com.sermilion.personalgraph.domain.capture.FlagSensitiveArgs
import com.sermilion.personalgraph.domain.capture.PayloadKind
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
    return persistEpisodeWithBacklink(node, args.sensitive)
  }

  private suspend fun persistEpisodeWithBacklink(node: EpisodeNode, sensitive: Boolean): CaptureResult {
    val primary = persistPrimary(node)
    if (primary !is CaptureResult.Created) return primary
    if (sensitive) return primary.copy(backlinkStatus = BacklinkStatus.Skipped)
    val backlinkResult = writeTimelineBacklink(node)
    return primary.copy(
      backlinkId = backlinkResult.first,
      backlinkStatus = backlinkResult.second,
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

  private fun precheckFlagSensitive(targetPath: String): FlagSensitiveValidation? = when {
    VaultPolicy.isReadBlocked(targetPath) ->
      FlagSensitiveValidation.Invalid(CaptureResult.PermissionDenied(REASON_PEOPLE_BLOCKED))
    parseNodeId(targetPath) == null ->
      FlagSensitiveValidation.Invalid(CaptureResult.InvalidInput(FIELD_TARGET_PATH, "invalid target_path"))
    else -> null
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
    return FlagSensitiveValidation.Ok(sourceId = sourceId, fallbackId = newId)
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

  private sealed interface FlagSensitiveValidation {
    data class Ok(val sourceId: NodeId, val fallbackId: NodeId) : FlagSensitiveValidation
    data class Invalid(val result: CaptureResult) : FlagSensitiveValidation
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

  private suspend fun writeTimelineBacklink(episode: EpisodeNode): Pair<NodeId?, BacklinkStatus> {
    val date = episode.date.toLocalDateTime(TimeZone.UTC)
    val yearMonth = "%04d-%02d".format(date.year, date.monthNumber)
    val datePrefix = "%04d-%02d-%02d".format(date.year, date.monthNumber, date.dayOfMonth)
    val backlinkPath = "${VaultLayout.timeline(yearMonth)}/$datePrefix-${slugify(episode.topic)}"
    val backlinkId = parseNodeId(backlinkPath)
      ?: return null to BacklinkStatus.Failed
    val backlinkBody = "[[${episode.id.value}]]\n"
    val backlinkNode = EpisodeNode(
      id = backlinkId,
      createdAt = episode.date,
      updatedAt = episode.date,
      body = backlinkBody,
      links = listOf(episode.id),
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

  private fun expectedPayloadKind(node: VaultNode): PayloadKind = when (node) {
    is StateNode -> PayloadKind.State
    is EpisodeNode -> PayloadKind.Episode
    is PatternNode -> PayloadKind.Pattern
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
    if (id.startsWith("${VaultLayout.BRANCH_DOMAINS}/")) return id
    return "${VaultLayout.BRANCH_DOMAINS}/$domain/events/${slugify(id)}"
  }

  private fun buildSensitiveTargetId(id: String): String {
    val tail = id.substringAfterLast('/')
    val slug = slugify(tail)
    return "${VaultLayout.BRANCH_STAGING_SENSITIVE}/$slug"
  }

  private fun slugify(value: String): String {
    val slug = value.lowercase()
      .replace(SLUG_NORMALIZE_REGEX, "-")
      .trim('-')
    return slug.ifEmpty { SLUG_FALLBACK }
  }

  private fun parseNodeId(value: String): NodeId? = runCatching { NodeId(value) }.getOrNull()

  companion object {
    private val SLUG_NORMALIZE_REGEX: Regex = Regex("[^a-z0-9]+")
    private const val SLUG_FALLBACK: String = "untitled"
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
  }
}
