package com.sermilion.personalgraph.data.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class StateNodeFrontmatterDataModel(
  val type: String = NODE_TYPE,
  val category: String,
  val confidence: String,
  val created: LocalDate,
  val updated: LocalDate,
) {
  companion object {
    const val NODE_TYPE: String = "state"
  }
}
