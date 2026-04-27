package com.sermilion.personalgraph.testing

import com.sermilion.personalgraph.domain.model.Confidence
import com.sermilion.personalgraph.domain.model.EmotionMarker
import com.sermilion.personalgraph.domain.model.EmotionalStateNode
import com.sermilion.personalgraph.domain.model.EpisodeNode
import com.sermilion.personalgraph.domain.model.EpisodeType
import com.sermilion.personalgraph.domain.model.Intensity
import com.sermilion.personalgraph.domain.model.NodeId
import com.sermilion.personalgraph.domain.model.PatternNode
import com.sermilion.personalgraph.domain.model.StateCategory
import com.sermilion.personalgraph.domain.model.StateNode
import com.sermilion.personalgraph.domain.model.SubjectNode
import kotlinx.datetime.Instant

object VaultNodeFixtures {
  val sampleInstant: Instant = Instant.parse("2026-04-24T12:00:00Z")
  val episodeInstant: Instant = Instant.parse("2026-04-24T15:02:00Z")
  val emotionalInstant: Instant = Instant.parse("2026-04-24T02:14:00Z")

  fun stateNode(
    id: String = "state/preferences/sample",
    body: String = "",
    category: StateCategory = StateCategory.Preference,
    confidence: Confidence = Confidence.High,
    links: List<NodeId> = emptyList(),
  ): StateNode = StateNode(
    id = NodeId(id),
    createdAt = sampleInstant,
    updatedAt = sampleInstant,
    body = body,
    links = links,
    category = category,
    confidence = confidence,
  )

  fun episodeNode(): EpisodeNode = EpisodeNode(
    id = NodeId("domains/work/capmo/events/sample-episode"),
    createdAt = episodeInstant,
    updatedAt = episodeInstant,
    body = "Body text linking [[patterns/applies-normalization-thinking]].\n",
    links = listOf(
      NodeId("patterns/applies-normalization-thinking"),
      NodeId("state/roles/current-role"),
    ),
    date = episodeInstant,
    episodeType = EpisodeType.Decision,
    domain = "work/capmo",
    topic = "sample-topic",
    intensity = Intensity.Medium,
  )

  fun patternNode(
    id: String = "patterns/applies-normalization-thinking",
    body: String = "Body referencing [[domains/work/capmo/events/sample-episode]].\n",
    hypothesis: String = "Short description of the pattern",
    evidenceCount: Int = 5,
    domainsSeenIn: List<String> = listOf("work/capmo", "personal"),
    contradictedBy: List<NodeId> = emptyList(),
    links: List<NodeId> = listOf(NodeId("domains/work/capmo/events/sample-episode")),
  ): PatternNode = PatternNode(
    id = NodeId(id),
    createdAt = Instant.parse("2026-04-24T00:00:00Z"),
    updatedAt = Instant.parse("2026-04-23T00:00:00Z"),
    body = body,
    links = links,
    hypothesis = hypothesis,
    evidenceCount = evidenceCount,
    lastObserved = Instant.parse("2026-04-23T00:00:00Z"),
    domainsSeenIn = domainsSeenIn,
    contradictedBy = contradictedBy,
  )

  fun subjectNode(
    id: String = "domains/work/capmo/subjects/build-pipeline",
    body: String =
      "## Summary\nBuild pipeline ownership is shared.\n\n## Evidence\n- 2026-04-24: paired on deployment fixes.\n",
    domain: String = "work/capmo",
    subject: String = "build-pipeline",
    aliases: List<String> = listOf("deploy-pipeline"),
    links: List<NodeId> = listOf(NodeId("domains/work/capmo/events/sample-episode")),
  ): SubjectNode = SubjectNode(
    id = NodeId(id),
    createdAt = sampleInstant,
    updatedAt = sampleInstant,
    body = body,
    links = links,
    domain = domain,
    subject = subject,
    aliases = aliases,
    evidenceCount = 1,
  )

  fun emotionalStateNode(): EmotionalStateNode = EmotionalStateNode(
    id = NodeId("emotional-states/2026-04-24-debug-frustration"),
    createdAt = emotionalInstant,
    updatedAt = emotionalInstant,
    body = "Body linking [[domains/work/capmo/events/sample-episode]].\n",
    links = listOf(NodeId("domains/work/capmo/events/sample-episode")),
    date = emotionalInstant,
    marker = EmotionMarker.Frustration,
    intensity = Intensity.Medium,
    context = "What was happening (task, topic, approximate time-of-day, duration if relevant)",
    triggerHypothesis = "tired and blocked after 90 min stuck on a bug",
    contradictedBy = emptyList(),
  )

  const val STATE_NODE_MARKDOWN: String = """---
type: "state"
category: "preference"
confidence: "high"
created: "2026-04-24"
updated: "2026-04-24"
---
2-space indentation, kotest funspec.
"""

  const val EPISODE_NODE_MARKDOWN: String = """---
type: "episode"
date: "2026-04-24T15:02:00Z"
episode_type: "decision"
domain: "work/capmo"
topic: "sample-topic"
linked:
  - "[[patterns/applies-normalization-thinking]]"
  - "[[state/roles/current-role]]"
intensity: "medium"
---
Body text linking [[patterns/applies-normalization-thinking]].
"""

  const val PATTERN_NODE_MARKDOWN: String = """---
type: "pattern"
created: "2026-04-24"
hypothesis: "Short description of the pattern"
evidence_count: 5
last_observed: "2026-04-23"
domains_seen_in:
  - "work/capmo"
  - "personal"
contradicted_by: []
---
Body referencing [[domains/work/capmo/events/sample-episode]].
"""

  const val SUBJECT_NODE_MARKDOWN: String = """---
type: "subject"
domain: "work/capmo"
subject: "build-pipeline"
created: "2026-04-24"
updated: "2026-04-24"
linked:
  - "[[domains/work/capmo/events/sample-episode]]"
aliases:
  - "deploy-pipeline"
evidence_count: 1
---
## Summary
Build pipeline ownership is shared.

## Evidence
- 2026-04-24: paired on deployment fixes.
"""

  const val EMOTIONAL_STATE_NODE_MARKDOWN: String = """---
type: "emotional-state"
date: "2026-04-24T02:14:00Z"
marker: "frustration"
intensity: "medium"
context: "What was happening (task, topic, approximate time-of-day, duration if relevant)"
trigger_hypothesis: "tired and blocked after 90 min stuck on a bug"
linked:
  - "[[domains/work/capmo/events/sample-episode]]"
contradicted_by: []
---
Body linking [[domains/work/capmo/events/sample-episode]].
"""
}
