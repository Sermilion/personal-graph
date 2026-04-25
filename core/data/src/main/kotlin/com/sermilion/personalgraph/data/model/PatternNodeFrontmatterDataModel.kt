package com.sermilion.personalgraph.data.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PatternNodeFrontmatterDataModel(
  val type: String = NODE_TYPE,
  val created: LocalDate,
  val hypothesis: String,
  @SerialName("evidence_count") val evidenceCount: Int,
  @SerialName("last_observed") val lastObserved: LocalDate,
  @SerialName("domains_seen_in") val domainsSeenIn: List<String> = emptyList(),
  @SerialName("contradicted_by") val contradictedBy: List<String> = emptyList(),
) {
  companion object {
    const val NODE_TYPE: String = "pattern"
  }
}
