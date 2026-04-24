package com.sermilion.personalgraph.domain.model

import kotlinx.datetime.Instant

sealed interface VaultNode {
  val id: NodeId
  val createdAt: Instant
  val updatedAt: Instant
  val body: String
  val links: List<NodeId>
}

data class StateNode(
  override val id: NodeId,
  override val createdAt: Instant,
  override val updatedAt: Instant,
  override val body: String,
  override val links: List<NodeId>,
  val category: StateCategory,
  val confidence: Confidence,
) : VaultNode

data class EpisodeNode(
  override val id: NodeId,
  override val createdAt: Instant,
  override val updatedAt: Instant,
  override val body: String,
  override val links: List<NodeId>,
  val date: Instant,
  val episodeType: EpisodeType,
  val domain: String,
  val topic: String,
  val intensity: Intensity,
) : VaultNode

data class PatternNode(
  override val id: NodeId,
  override val createdAt: Instant,
  override val updatedAt: Instant,
  override val body: String,
  override val links: List<NodeId>,
  val hypothesis: String,
  val evidenceCount: Int,
  val lastObserved: Instant,
  val domainsSeenIn: List<String>,
  val contradictedBy: List<NodeId>,
) : VaultNode

data class EmotionalStateNode(
  override val id: NodeId,
  override val createdAt: Instant,
  override val updatedAt: Instant,
  override val body: String,
  override val links: List<NodeId>,
  val date: Instant,
  val marker: EmotionMarker,
  val intensity: Intensity,
  val context: String,
  val triggerHypothesis: String,
  val contradictedBy: List<NodeId>,
) : VaultNode
