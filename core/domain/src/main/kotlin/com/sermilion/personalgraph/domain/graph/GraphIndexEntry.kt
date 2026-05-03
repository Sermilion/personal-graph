package com.sermilion.personalgraph.domain.graph

import com.sermilion.personalgraph.domain.model.NodeId
import kotlinx.datetime.Instant

data class GraphIndexEntry(
  val id: NodeId,
  val branch: String,
  val type: String,
  val category: String?,
  val domain: String?,
  val scope: String?,
  val scopes: List<String>,
  val subject: String?,
  val topic: String?,
  val aliases: List<String>,
  val hypothesis: String?,
  val date: Instant?,
  val updated: Instant,
  val created: Instant,
  val links: List<NodeId>,
  val linkCount: Int,
  val snippet: String,
  val bodyTokenEstimate: Int,
  val fileSize: Long,
  val fileModifiedAt: Instant,
)
