package com.sermilion.personalgraph.data.capture

import com.sermilion.personalgraph.domain.capture.CaptureObservationArgs
import com.sermilion.personalgraph.domain.capture.CaptureObservationKind
import com.sermilion.personalgraph.domain.model.Confidence
import com.sermilion.personalgraph.domain.model.EpisodeType
import com.sermilion.personalgraph.domain.model.Intensity
import com.sermilion.personalgraph.domain.model.StateCategory
import kotlinx.datetime.Instant

internal data class ValidEpisodeCandidate(
  val date: Instant,
  val episodeType: EpisodeType,
  val domain: String,
  val topic: String,
  val intensity: Intensity,
)

internal fun CaptureObservationArgs.shouldRouteToSensitiveStaging(): Boolean {
  val sensitiveTextDetected = looksSensitive(observation) || looksSensitive(sourceContext)
  return sensitive || sensitiveTextDetected
}

internal fun shouldStage(args: CaptureObservationArgs, observation: String): Boolean {
  val lowConfidence = args.confidence == Confidence.Low
  val suggestedEpisode = args.suggestedKind == CaptureObservationKind.Episode
  val incompleteEpisode = suggestedEpisode && !hasCompleteEpisodeShape(args)
  val noStructureHint = args.suggestedKind == null && args.category == null
  val eventLikeWithoutDurableStructure = looksEventLike(observation) && !hasDurableStateSignal(observation)
  val lacksReusableStructure = !hasReusableSignal(observation) && noStructureHint
  return lowConfidence ||
    incompleteEpisode ||
    (noStructureHint && eventLikeWithoutDurableStructure) ||
    lacksReusableStructure
}

internal fun shouldCaptureEpisode(args: CaptureObservationArgs): Boolean {
  val requestedEpisode = args.suggestedKind == CaptureObservationKind.Episode
  return requestedEpisode || hasCompleteEpisodeShape(args)
}

internal fun observationBody(observation: String, sourceContext: String): String = buildString {
  append(observation.trim())
  val source = sourceContext.trim()
  if (source.isNotBlank()) {
    appendLine()
    appendLine()
    append("Source context: ")
    append(source)
  }
}

internal fun generatedObservationId(value: String): String = GeneratedSlugPolicy.generatedObservationId(value)

internal fun inferStateCategory(observation: String): StateCategory {
  val normalized = observation.lowercase()
  return when {
    PREFERENCE_SIGNALS.any { normalized.contains(it) } -> StateCategory.Preference
    ROLE_SIGNALS.any { normalized.contains(it) } -> StateCategory.Role
    FACT_SIGNALS.any { normalized.contains(it) } -> StateCategory.Fact
    else -> StateCategory.Knowledge
  }
}

internal fun inferConfidence(observation: String, category: StateCategory): Confidence {
  val normalized = observation.lowercase()
  return when {
    category == StateCategory.Preference && PREFERENCE_SIGNALS.any { normalized.contains(it) } -> Confidence.High
    hasReusableSignal(observation) -> Confidence.Medium
    else -> Confidence.Low
  }
}

internal fun isRoutineNoise(observation: String): Boolean {
  val normalized = observation.lowercase()
  val matchesRoutinePattern = ROUTINE_NOISE_PATTERNS.any { it.containsMatchIn(normalized) }
  return normalized.length < MIN_OBSERVATION_LENGTH || (matchesRoutinePattern && !hasReusableSignal(observation))
}

internal fun CaptureObservationArgs.validatedEpisodeCandidate(): ValidEpisodeCandidate? {
  val candidateDate = date
  val candidateType = episodeType
  val candidateDomain = domain
  val candidateTopic = topic
  val candidateIntensity = intensity
  return when {
    candidateDate == null -> null
    candidateType == null -> null
    candidateDomain == null -> null
    candidateTopic == null -> null
    candidateIntensity == null -> null
    else -> ValidEpisodeCandidate(
      date = candidateDate,
      episodeType = candidateType,
      domain = candidateDomain,
      topic = candidateTopic,
      intensity = candidateIntensity,
    )
  }
}

internal fun CaptureObservationArgs.episodeCandidateMissingReason(): String = when {
  date == null -> "episode_date_missing"
  episodeType == null -> "episode_type_missing"
  domain == null -> "episode_domain_missing"
  topic == null -> "episode_topic_missing"
  intensity == null -> "episode_intensity_missing"
  else -> "episode_shape_invalid"
}

private fun hasCompleteEpisodeShape(args: CaptureObservationArgs): Boolean = args.date != null &&
  args.episodeType != null &&
  args.domain != null &&
  args.topic != null &&
  args.intensity != null

private fun hasReusableSignal(observation: String): Boolean {
  val normalized = observation.lowercase()
  return REUSABLE_SIGNALS.any { normalized.contains(it) }
}

private fun hasDurableStateSignal(observation: String): Boolean {
  val normalized = observation.lowercase()
  return DURABLE_STATE_SIGNALS.any { normalized.contains(it) }
}

internal fun looksEventLike(observation: String): Boolean {
  val normalized = observation.lowercase()
  return EVENT_LIKE_PATTERNS.any { it.containsMatchIn(normalized) }
}

private fun looksSensitive(value: String): Boolean {
  val normalized = value.lowercase()
  return SENSITIVE_PATTERNS.any { it.containsMatchIn(normalized) }
}

private const val MIN_OBSERVATION_LENGTH: Int = 12

private val PREFERENCE_SIGNALS: List<String> = listOf(
  "prefer",
  "prefers",
  "wants",
  "always",
  "never",
  "default to",
  "do not",
  "don't",
)

private val ROLE_SIGNALS: List<String> = listOf(
  " role ",
  " responsible for ",
  " owns ",
)

private val FACT_SIGNALS: List<String> = listOf(
  " is ",
  " are ",
  " uses ",
  " installed ",
  " lives at ",
)

private val REUSABLE_SIGNALS: List<String> = listOf(
  "always",
  "never",
  "prefer",
  "prefers",
  "wants",
  "rule",
  "pattern",
  "remember",
  "future",
  "decision",
  "decided",
  "chose",
  "architecture",
  "source of truth",
  "workaround",
  "regression",
  "fix",
  "default",
  "canonical",
)

private val DURABLE_STATE_SIGNALS: List<String> = listOf(
  "always",
  "never",
  "prefer",
  "prefers",
  "wants",
  "rule",
  "pattern",
  "remember",
  "future",
  "architecture",
  "source of truth",
  "default",
  "canonical",
)

private val EVENT_LIKE_SIGNALS: List<String> = listOf(
  "decision",
  "decided",
  "fix",
  "fixed",
  "regression",
  "chose",
  "chosen",
  "implemented",
)

private val EVENT_LIKE_PATTERNS: List<Regex> = EVENT_LIKE_SIGNALS.map { signal ->
  Regex("""\b${Regex.escape(signal)}\b""")
}

private val ROUTINE_NOISE_PATTERNS: List<Regex> = listOf(
  Regex("^ran (tests|checks|build|gradle|lint)\\b"),
  Regex("^git status\\b"),
  Regex("^tests? passed\\b"),
  Regex("^build passed\\b"),
  Regex("^no changes\\b"),
)

private val SENSITIVE_PATTERNS: List<Regex> = listOf(
  Regex("\\bpassword\\s*[:=]"),
  Regex("\\bapi[_ -]?key\\s*[:=]"),
  Regex("\\bsecret\\s*[:=]"),
  Regex("\\btoken\\s*[:=]"),
  Regex("\\bbearer\\s+[a-z0-9._~+/=-]{12,}"),
  Regex("\\bcredential(s)?\\s*[:=]"),
  Regex("\\bprivate message\\b"),
  Regex("\\braw dm\\b"),
)
