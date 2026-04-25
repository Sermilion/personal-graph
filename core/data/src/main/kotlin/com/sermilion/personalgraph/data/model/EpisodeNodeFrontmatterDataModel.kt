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
) {
  companion object {
    const val NODE_TYPE: String = "episode"
  }
}
