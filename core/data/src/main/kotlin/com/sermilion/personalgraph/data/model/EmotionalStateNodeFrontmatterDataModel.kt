package com.sermilion.personalgraph.data.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EmotionalStateNodeFrontmatterDataModel(
  val type: String = NODE_TYPE,
  val date: Instant,
  val marker: String,
  val intensity: String,
  val context: String,
  @SerialName("trigger_hypothesis") val triggerHypothesis: String,
  val linked: List<String> = emptyList(),
  @SerialName("contradicted_by") val contradictedBy: List<String> = emptyList(),
  @SerialName("occurrence_count") val occurrenceCount: Int = 1,
  @SerialName("source_ids") val sourceIds: List<String> = emptyList(),
  @SerialName("pattern_links") val patternLinks: List<String> = emptyList(),
) {
  companion object {
    const val NODE_TYPE: String = "emotional-state"
  }
}
