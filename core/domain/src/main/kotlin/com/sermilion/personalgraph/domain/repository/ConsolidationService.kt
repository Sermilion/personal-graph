package com.sermilion.personalgraph.domain.repository

import com.sermilion.personalgraph.domain.model.NodeId

interface ConsolidationService {
  suspend fun consolidate(request: ConsolidationRequest = ConsolidationRequest()): ConsolidationReport
}

data class ConsolidationReport(
  val graduated: List<GraduatedObservation> = emptyList(),
  val mergedDuplicates: List<MergedDuplicate> = emptyList(),
  val promotedPatterns: List<PromotedPattern> = emptyList(),
  val annotatedContradictions: List<AnnotatedContradiction> = emptyList(),
  val migratedLegacyNotes: List<MigratedLegacyNote> = emptyList(),
)

data class ConsolidationRequest(
  val includeSensitiveStaging: Boolean = false,
  val includePeople: Boolean = false,
)

sealed interface ConsolidationChange {
  val nodeId: NodeId
  val sourceIds: List<NodeId>
}

data class GraduatedObservation(
  override val nodeId: NodeId,
  override val sourceIds: List<NodeId>,
  val occurrenceCount: Int,
) : ConsolidationChange

data class MergedDuplicate(
  override val nodeId: NodeId,
  override val sourceIds: List<NodeId>,
  val mergedInto: NodeId,
) : ConsolidationChange

data class PromotedPattern(
  override val nodeId: NodeId,
  override val sourceIds: List<NodeId>,
  val evidenceCount: Int,
  val domainsSeenIn: List<String>,
) : ConsolidationChange

data class AnnotatedContradiction(
  override val nodeId: NodeId,
  override val sourceIds: List<NodeId>,
  val contradictedNodeId: NodeId,
  val reason: String,
) : ConsolidationChange

data class MigratedLegacyNote(
  override val nodeId: NodeId,
  override val sourceIds: List<NodeId>,
  val migratedFrom: NodeId,
) : ConsolidationChange
