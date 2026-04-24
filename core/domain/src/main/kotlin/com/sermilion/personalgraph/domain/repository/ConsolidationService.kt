package com.sermilion.personalgraph.domain.repository

import com.sermilion.personalgraph.domain.model.NodeId

interface ConsolidationService {
  suspend fun runConsolidation(): ConsolidationReport
}

data class ConsolidationReport(
  val graduated: List<NodeId>,
  val mergedDuplicates: List<NodeId>,
  val promotedPatterns: List<NodeId>,
  val annotatedContradictions: List<NodeId>,
)
