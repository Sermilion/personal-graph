package com.sermilion.personalgraph.domain.capture

import com.sermilion.personalgraph.domain.model.Confidence
import com.sermilion.personalgraph.domain.model.EpisodeType
import com.sermilion.personalgraph.domain.model.Intensity
import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.model.StateCategory
import kotlinx.datetime.Instant

interface VaultCaptureService {

  suspend fun captureObservation(args: CaptureObservationArgs): CaptureObservationResult

  suspend fun writeStateObservation(args: WriteStateArgs): CaptureResult

  suspend fun writeEpisode(args: WriteEpisodeArgs): CaptureResult

  suspend fun writeToStaging(args: WriteToStagingArgs): CaptureResult

  suspend fun flagSensitive(args: FlagSensitiveArgs): CaptureResult
}

data class CaptureObservationArgs(
  val observation: String,
  val sourceContext: String,
  val suggestedKind: CaptureObservationKind?,
  val id: String?,
  val category: StateCategory?,
  val confidence: Confidence?,
  val date: Instant?,
  val episodeType: EpisodeType?,
  val domain: String?,
  val topic: String?,
  val intensity: Intensity?,
  val links: List<NodeId>,
  val sensitive: Boolean,
)

enum class CaptureObservationKind {
  State,
  Episode,
}

data class WriteStateArgs(
  val id: String,
  val category: StateCategory,
  val confidence: Confidence,
  val body: String,
  val links: List<NodeId>,
  val sensitive: Boolean,
)

data class WriteEpisodeArgs(
  val id: String,
  val date: Instant,
  val episodeType: EpisodeType,
  val domain: String,
  val topic: String,
  val intensity: Intensity,
  val body: String,
  val linked: List<NodeId>,
  val sensitive: Boolean,
)

data class WriteToStagingArgs(
  val id: String,
  val category: StateCategory,
  val confidence: Confidence,
  val body: String,
  val links: List<NodeId>,
)

data class FlagSensitiveArgs(val targetPath: String, val payloadKind: PayloadKind)

enum class PayloadKind {
  State,
  Episode,
  Pattern,
  Subject,
  EmotionalState,
}

sealed interface CaptureResult {
  data class Created(
    val id: NodeId,
    val backlinkId: NodeId? = null,
    val backlinkStatus: BacklinkStatus = BacklinkStatus.Skipped,
    val subjectHubId: NodeId? = null,
    val subjectHubStatus: SubjectHubStatus = SubjectHubStatus.Skipped,
  ) : CaptureResult

  data class PermissionDenied(val reason: String) : CaptureResult

  data class InvalidInput(val field: String, val reason: String, val expected: String? = null) : CaptureResult

  data class NotFound(val targetPath: String) : CaptureResult

  data class Failed(val reason: String) : CaptureResult
}

sealed interface CaptureObservationResult {
  data class Decided(
    val decision: CaptureObservationDecision,
    val reason: String,
    val captureResult: CaptureResult? = null,
  ) : CaptureObservationResult

  data class InvalidInput(val field: String, val reason: String) : CaptureObservationResult
}

enum class CaptureObservationDecision {
  Rejected,
  StagedObservation,
  StagedSensitive,
  StateWritten,
  StateUpdated,
  EpisodeWritten,
  EpisodeUpdated,
}

enum class BacklinkStatus {
  Ok,
  Failed,
  Skipped,
}

enum class SubjectHubStatus {
  Created,
  Updated,
  Failed,
  Skipped,
}
