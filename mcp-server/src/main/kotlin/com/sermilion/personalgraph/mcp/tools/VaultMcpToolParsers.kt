package com.sermilion.personalgraph.mcp.tools

import com.sermilion.personalgraph.domain.capture.PayloadKind
import com.sermilion.personalgraph.domain.model.Confidence
import com.sermilion.personalgraph.domain.model.EpisodeType
import com.sermilion.personalgraph.domain.model.Intensity
import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.model.StateCategory
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray

internal fun JsonObject.stringOrNull(key: String): String? {
  val primitive = this[key] as? JsonPrimitive ?: return null
  return if (!primitive.isString) null else primitive.contentOrNull
}

internal fun JsonObject.booleanOrNull(key: String): Boolean? = this[key]?.let { element ->
  val primitive = element as? JsonPrimitive ?: return@let null
  primitive.contentOrNull?.lowercase()?.let { content ->
    when (content) {
      "true" -> true
      "false" -> false
      else -> null
    }
  }
}

internal fun JsonObject.stringListOrNull(key: String): List<String>? = this[key]?.let { element ->
  val arr = (element as? JsonArray) ?: return@let null
  arr.jsonArray.mapNotNull { entry ->
    val primitive = entry as? JsonPrimitive ?: return@mapNotNull null
    if (!primitive.isString) null else primitive.contentOrNull
  }
}

internal fun parseNodeId(value: String): NodeId? = runCatching { NodeId(value) }.getOrNull()

internal fun parseInstant(raw: String): Instant? = runCatching { Instant.parse(raw) }.getOrNull()

internal fun parseStateCategory(raw: String): StateCategory? = when (raw.lowercase()) {
  "preference" -> StateCategory.Preference
  "role" -> StateCategory.Role
  "knowledge" -> StateCategory.Knowledge
  "fact" -> StateCategory.Fact
  else -> null
}

internal fun parseConfidence(raw: String): Confidence? = when (raw.lowercase()) {
  "high" -> Confidence.High
  "medium" -> Confidence.Medium
  "low" -> Confidence.Low
  else -> null
}

internal fun parseEpisodeType(raw: String): EpisodeType? = when (raw.lowercase()) {
  "purchase" -> EpisodeType.Purchase
  "advice-seeking" -> EpisodeType.AdviceSeeking
  "research" -> EpisodeType.Research
  "design-doc" -> EpisodeType.DesignDoc
  "question" -> EpisodeType.Question
  "personal-story" -> EpisodeType.PersonalStory
  "work-interaction" -> EpisodeType.WorkInteraction
  "decision" -> EpisodeType.Decision
  else -> null
}

internal fun parseIntensity(raw: String): Intensity? = when (raw.lowercase()) {
  "low" -> Intensity.Low
  "medium" -> Intensity.Medium
  "high" -> Intensity.High
  else -> null
}

internal fun parsePayloadKind(raw: String): PayloadKind? = when (raw.lowercase()) {
  ToolSchemas.PAYLOAD_KIND_STATE -> PayloadKind.State
  ToolSchemas.PAYLOAD_KIND_EPISODE -> PayloadKind.Episode
  ToolSchemas.PAYLOAD_KIND_PATTERN -> PayloadKind.Pattern
  ToolSchemas.PAYLOAD_KIND_SUBJECT -> PayloadKind.Subject
  ToolSchemas.PAYLOAD_KIND_EMOTIONAL_STATE -> PayloadKind.EmotionalState
  else -> null
}

internal fun List<String>.toNodeIds(): List<NodeId> = mapNotNull { raw ->
  val cleaned = raw.trim().removeSurrounding("[[", "]]")
  parseNodeId(cleaned)
}

internal data class ParseError(val field: String, val reason: String)

internal sealed interface ParseOutcome<out T> {
  data class Success<T>(val value: T) : ParseOutcome<T>
  data class Failure(val error: ParseError) : ParseOutcome<Nothing>
}

internal fun JsonObject.requiredString(key: String, reason: String): ParseOutcome<String> {
  val value = stringOrNull(key) ?: return ParseOutcome.Failure(ParseError(key, reason))
  return ParseOutcome.Success(value)
}

internal inline fun <T> JsonObject.parsedField(
  key: String,
  reason: String,
  transform: (String) -> T?,
): ParseOutcome<T> = stringOrNull(key)?.let(transform)?.let { ParseOutcome.Success(it) }
  ?: ParseOutcome.Failure(ParseError(key, reason))
