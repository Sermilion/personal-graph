package com.sermilion.personalgraph.mcp.tools

import com.sermilion.personalgraph.domain.capture.FlagSensitiveArgs
import com.sermilion.personalgraph.domain.capture.WriteEpisodeArgs
import com.sermilion.personalgraph.domain.capture.WriteStateArgs
import com.sermilion.personalgraph.domain.capture.WriteToStagingArgs
import com.sermilion.personalgraph.domain.layout.VaultPolicy
import com.sermilion.personalgraph.domain.model.Confidence
import com.sermilion.personalgraph.domain.model.EpisodeType
import com.sermilion.personalgraph.domain.model.Intensity
import com.sermilion.personalgraph.domain.model.StateCategory
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject

internal const val PERMISSION_DENIED_PEOPLE: String = "people/ is read-blocked by default"
internal const val PERMISSION_DENIED_OUTSIDE: String = "path is outside the vault root"
internal const val REASON_MISSING: String = "missing"
internal const val REASON_INVALID: String = "invalid"

internal sealed interface Parsed<out T> {
  data class Success<T>(val value: T) : Parsed<T>
  data class Failure<T>(val json: JsonObject) : Parsed<T>
}

internal data class StateCore(
  val id: String,
  val category: StateCategory,
  val confidence: Confidence,
)

internal data class EpisodeCore(
  val id: String,
  val date: Instant,
  val episodeType: EpisodeType,
  val domain: String,
  val topic: String,
  val intensity: Intensity,
)

internal data class EpisodeRaw(
  val id: String?,
  val date: Instant?,
  val episodeType: EpisodeType?,
  val domain: String?,
  val topic: String?,
  val intensity: Intensity?,
)

internal fun parseStateCore(args: JsonObject): Parsed<StateCore> {
  val id = args.stringOrNull(ToolSchemas.KEY_ID)
    ?: return Parsed.Failure(invalidInputJson(ToolSchemas.KEY_ID, REASON_MISSING))
  val category = args.stringOrNull(ToolSchemas.KEY_CATEGORY)?.let(::parseStateCategory)
  val confidence = args.stringOrNull(ToolSchemas.KEY_CONFIDENCE)?.let(::parseConfidence)
  return when {
    category == null -> Parsed.Failure(invalidInputJson(ToolSchemas.KEY_CATEGORY, REASON_INVALID))
    confidence == null -> Parsed.Failure(invalidInputJson(ToolSchemas.KEY_CONFIDENCE, REASON_INVALID))
    else -> Parsed.Success(StateCore(id, category, confidence))
  }
}

internal fun parseWriteStateArgs(args: JsonObject): Parsed<WriteStateArgs> = when (val core = parseStateCore(args)) {
  is Parsed.Failure -> Parsed.Failure(core.json)
  is Parsed.Success -> Parsed.Success(
    WriteStateArgs(
      id = core.value.id,
      category = core.value.category,
      confidence = core.value.confidence,
      body = args.stringOrNull(ToolSchemas.KEY_BODY).orEmpty(),
      links = args.stringListOrNull(ToolSchemas.KEY_LINKS).orEmpty().toNodeIds(),
      sensitive = args.booleanOrNull(ToolSchemas.KEY_SENSITIVE) == true,
    ),
  )
}

internal fun parseWriteToStagingArgs(args: JsonObject): Parsed<WriteToStagingArgs> {
  val core = parseStateCore(args)
  return when (core) {
    is Parsed.Failure -> Parsed.Failure(core.json)
    is Parsed.Success -> Parsed.Success(
      WriteToStagingArgs(
        id = core.value.id,
        category = core.value.category,
        confidence = core.value.confidence,
        body = args.stringOrNull(ToolSchemas.KEY_BODY).orEmpty(),
        links = args.stringListOrNull(ToolSchemas.KEY_LINKS).orEmpty().toNodeIds(),
      ),
    )
  }
}

internal fun parseWriteEpisodeArgs(args: JsonObject): Parsed<WriteEpisodeArgs> {
  val core = parseWriteEpisodeCore(args)
  return when (core) {
    is Parsed.Failure -> Parsed.Failure(core.json)
    is Parsed.Success -> Parsed.Success(
      WriteEpisodeArgs(
        id = core.value.id,
        date = core.value.date,
        episodeType = core.value.episodeType,
        domain = core.value.domain,
        topic = core.value.topic,
        intensity = core.value.intensity,
        body = args.stringOrNull(ToolSchemas.KEY_BODY).orEmpty(),
        linked = args.stringListOrNull(ToolSchemas.KEY_LINKED).orEmpty().toNodeIds(),
        sensitive = args.booleanOrNull(ToolSchemas.KEY_SENSITIVE) == true,
      ),
    )
  }
}

internal fun parseWriteEpisodeCore(args: JsonObject): Parsed<EpisodeCore> {
  val raw = collectEpisodeRaw(args)
  val firstError = firstEpisodeError(raw)
  if (firstError != null) return Parsed.Failure(firstError)
  return Parsed.Success(
    EpisodeCore(
      id = requireNotNull(raw.id),
      date = requireNotNull(raw.date),
      episodeType = requireNotNull(raw.episodeType),
      domain = requireNotNull(raw.domain),
      topic = requireNotNull(raw.topic),
      intensity = requireNotNull(raw.intensity),
    ),
  )
}

private fun collectEpisodeRaw(args: JsonObject): EpisodeRaw = EpisodeRaw(
  id = args.stringOrNull(ToolSchemas.KEY_ID),
  date = args.stringOrNull(ToolSchemas.KEY_DATE)?.let(::parseInstant),
  episodeType = args.stringOrNull(ToolSchemas.KEY_EPISODE_TYPE)?.let(::parseEpisodeType),
  domain = args.stringOrNull(ToolSchemas.KEY_DOMAIN),
  topic = args.stringOrNull(ToolSchemas.KEY_TOPIC),
  intensity = args.stringOrNull(ToolSchemas.KEY_INTENSITY)?.let(::parseIntensity),
)

private fun firstEpisodeError(raw: EpisodeRaw): JsonObject? = when {
  raw.id == null -> invalidInputJson(ToolSchemas.KEY_ID, REASON_MISSING)
  raw.date == null -> invalidInputJson(ToolSchemas.KEY_DATE, REASON_INVALID)
  raw.episodeType == null -> invalidInputJson(ToolSchemas.KEY_EPISODE_TYPE, REASON_INVALID)
  raw.domain == null -> invalidInputJson(ToolSchemas.KEY_DOMAIN, REASON_MISSING)
  raw.topic == null -> invalidInputJson(ToolSchemas.KEY_TOPIC, REASON_MISSING)
  raw.intensity == null -> invalidInputJson(ToolSchemas.KEY_INTENSITY, REASON_INVALID)
  else -> null
}

internal fun parseFlagSensitiveArgs(args: JsonObject): Parsed<FlagSensitiveArgs> {
  val targetPath = args.stringOrNull(ToolSchemas.KEY_TARGET_PATH)
    ?: return Parsed.Failure(invalidInputJson(ToolSchemas.KEY_TARGET_PATH, REASON_MISSING))
  val gate = flagSensitiveGate(args, targetPath)
  return gate ?: resolveFlagSensitiveSuccess(args, targetPath)
}

private fun flagSensitiveGate(args: JsonObject, targetPath: String): Parsed<FlagSensitiveArgs>? {
  if (VaultPolicy.isReadBlocked(targetPath)) {
    return Parsed.Failure(
      statusJson(
        ToolSchemas.STATUS_PERMISSION_DENIED,
        mapOf(
          ToolSchemas.KEY_TARGET_PATH to targetPath,
          ToolSchemas.KEY_REASON to PERMISSION_DENIED_PEOPLE,
        ),
      ),
    )
  }
  val payloadKindRaw = args.stringOrNull(ToolSchemas.KEY_PAYLOAD_KIND) ?: ToolSchemas.PAYLOAD_KIND_STATE
  if (parsePayloadKind(payloadKindRaw) == null) {
    return Parsed.Failure(
      invalidInputJson(
        ToolSchemas.KEY_PAYLOAD_KIND,
        "unsupported ${ToolSchemas.KEY_PAYLOAD_KIND}: $payloadKindRaw",
      ),
    )
  }
  return null
}

private fun resolveFlagSensitiveSuccess(args: JsonObject, targetPath: String): Parsed<FlagSensitiveArgs> {
  val payloadKindRaw = args.stringOrNull(ToolSchemas.KEY_PAYLOAD_KIND) ?: ToolSchemas.PAYLOAD_KIND_STATE
  val payloadKind = checkNotNull(parsePayloadKind(payloadKindRaw))
  return Parsed.Success(FlagSensitiveArgs(targetPath = targetPath, payloadKind = payloadKind))
}

internal fun permissionDeniedReadBlocked(rawId: String): JsonObject = statusJson(
  ToolSchemas.STATUS_PERMISSION_DENIED,
  mapOf(
    ToolSchemas.KEY_PATH to rawId,
    ToolSchemas.KEY_REASON to PERMISSION_DENIED_PEOPLE,
  ),
)

internal fun permissionDeniedOutside(rawId: String): JsonObject = statusJson(
  ToolSchemas.STATUS_PERMISSION_DENIED,
  mapOf(
    ToolSchemas.KEY_PATH to rawId,
    ToolSchemas.KEY_REASON to PERMISSION_DENIED_OUTSIDE,
  ),
)
