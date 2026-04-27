package com.sermilion.personalgraph.data.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SubjectNodeFrontmatterDataModel(
  val type: String = NODE_TYPE,
  val domain: String,
  val subject: String,
  val created: LocalDate,
  val updated: LocalDate,
  val linked: List<String> = emptyList(),
  val aliases: List<String> = emptyList(),
  @SerialName("evidence_count") val evidenceCount: Int = 0,
  @SerialName("source_ids") val sourceIds: List<String> = emptyList(),
  @SerialName("pattern_links") val patternLinks: List<String> = emptyList(),
) {
  companion object {
    const val NODE_TYPE: String = "subject"
  }
}
