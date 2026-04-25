package com.sermilion.personalgraph.data.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StateNodeFrontmatterDataModel(
  val type: String = NODE_TYPE,
  val category: String,
  val confidence: String,
  val created: LocalDate,
  val updated: LocalDate,
  val linked: List<String> = emptyList(),
  @SerialName("occurrence_count") val occurrenceCount: Int = 1,
  @SerialName("source_ids") val sourceIds: List<String> = emptyList(),
  @SerialName("pattern_links") val patternLinks: List<String> = emptyList(),
  @SerialName("contradicted_by") val contradictedBy: List<String> = emptyList(),
) {
  companion object {
    const val NODE_TYPE: String = "state"
  }
}
