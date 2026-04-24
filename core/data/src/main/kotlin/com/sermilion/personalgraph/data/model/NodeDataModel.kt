package com.sermilion.personalgraph.data.model

import kotlinx.serialization.Serializable

@Serializable
data class NodeDataModel(
  val id: String,
  val type: String,
  val frontmatter: Map<String, String>,
  val body: String,
  val links: List<String>,
  val backlinks: List<String> = emptyList(),
)
