package com.sermilion.personalgraph.data.model

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EpisodeNodeFrontmatterDataModel(
  val type: String = NODE_TYPE,
  val date: Instant,
  @SerialName("episode_type") val episodeType: String,
  val domain: String,
  val topic: String,
  val linked: List<String> = emptyList(),
  val intensity: String,
  @SerialName("occurrence_count") val occurrenceCount: Int = 1,
  @SerialName("source_ids") val sourceIds: List<String> = emptyList(),
  @SerialName("pattern_links") val patternLinks: List<String> = emptyList(),
  @SerialName("contradicted_by") val contradictedBy: List<String> = emptyList(),
) {
  companion object {
    const val NODE_TYPE: String = "episode"
  }
}
