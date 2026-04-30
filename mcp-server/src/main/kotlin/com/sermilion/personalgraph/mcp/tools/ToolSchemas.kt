package com.sermilion.personalgraph.mcp.tools

import com.sermilion.personalgraph.domain.layout.VaultLayout

object ToolSchemas {

  const val TOOL_WRITE_STATE: String = "write_state"
  const val TOOL_CAPTURE_OBSERVATION: String = "capture_observation"
  const val TOOL_WRITE_EPISODE: String = "write_episode"
  const val TOOL_WRITE_TO_STAGING: String = "write_to_staging"
  const val TOOL_FLAG_SENSITIVE: String = "flag_sensitive"
  const val TOOL_LIST_PENDING_SENSITIVE: String = "list_pending_sensitive"
  const val TOOL_READ_NODE: String = "read_node"
  const val TOOL_LIST_BRANCH: String = "list_branch"
  const val TOOL_SESSION_START: String = "session_start"

  const val DESC_WRITE_STATE: String = "Write or update a state node (Tier 1 capture for durable facts)."
  const val DESC_CAPTURE_OBSERVATION: String =
    "Submit a candidate observation. Personal-graph decides whether to reject, stage, save, or update it."
  const val DESC_WRITE_EPISODE: String =
    "Write or update an episode node, append it to a canonical subject hub, and create a timeline backlink stub."
  const val DESC_WRITE_TO_STAGING: String = "Write a state-shaped observation to staging/observations/."
  const val DESC_FLAG_SENSITIVE: String = "Re-route an existing or inline node to staging/sensitive/ for batch review."
  const val DESC_LIST_PENDING_SENSITIVE: String =
    "List ids and short excerpts of items currently in staging/sensitive/."
  const val DESC_READ_NODE: String = "Read a node by id. Reads under people/ are blocked by default."
  const val DESC_LIST_BRANCH: String = "List nodes under a branch path. Reads under people/ are blocked by default."
  const val DESC_SESSION_START: String =
    "Load session-start context: Braian.md, classified domain subtree, and linked pattern hubs."

  const val DESC_FIELD_STATE_ID: String =
    "Node id. Accepts canonical plural prefix (e.g. state/roles/<leaf>), or a bare leaf which is " +
      "routed via `category`. Singular-prefix forms (state/role/, state/preference/, state/fact/) " +
      "are rejected — use the canonical plural form named in `expected`."
  const val DESC_FIELD_NODE_ID: String =
    "Node id; canonical form `<branch>/<leaf>` (e.g. domains/creative/events/<leaf> for episodes)."
  const val DESC_FIELD_DATE: String =
    "ISO-8601 instant in UTC, e.g. 2026-04-25T00:00:00Z. Date-only values are rejected."
  const val DESC_FIELD_TARGET_PATH: String =
    "Existing node id to re-route. Reads under people/ are blocked. Must parse as a valid node id."
  const val DESC_FIELD_BRANCH: String =
    "Branch path under the vault root, e.g. state/roles or domains/work/capmo. Reads under people/ are blocked."
  const val DESC_FIELD_LINKS: String =
    "Wikilink targets as node ids. Entries that fail to parse are silently dropped without error; " +
      "canonical-prefix enforcement is not currently performed."
  const val DESC_FIELD_PAYLOAD_KIND: String =
    "Expected payload kind for the targeted node. Must match the actual node type or the call is " +
      "rejected with invalid_input."

  const val KEY_ID: String = "id"
  const val KEY_OBSERVATION: String = "observation"
  const val KEY_SOURCE_CONTEXT: String = "source_context"
  const val KEY_SUGGESTED_KIND: String = "suggested_kind"
  const val KEY_MESSAGE: String = "message"
  const val KEY_TOPIC: String = "topic"
  const val KEY_BODY: String = "body"
  const val KEY_LINKS: String = "links"
  const val KEY_LINKED: String = "linked"
  const val KEY_CATEGORY: String = "category"
  const val KEY_CONFIDENCE: String = "confidence"
  const val KEY_SENSITIVE: String = "sensitive"
  const val KEY_DATE: String = "date"
  const val KEY_EPISODE_TYPE: String = "episode_type"
  const val KEY_DOMAIN: String = "domain"
  const val KEY_INTENSITY: String = "intensity"
  const val KEY_MARKER: String = "marker"
  const val KEY_TARGET_PATH: String = "target_path"
  const val KEY_PAYLOAD_KIND: String = "payload_kind"
  const val KEY_BRANCH: String = "branch"
  const val KEY_REASON: String = "reason"
  const val KEY_DECISION: String = "decision"
  const val KEY_STATUS: String = "status"
  const val KEY_PATH: String = "path"
  const val KEY_NODE: String = "node"
  const val KEY_NODES: String = "nodes"
  const val KEY_EXCERPT: String = "excerpt"
  const val KEY_BACKLINK_PATH: String = "backlink_path"
  const val KEY_BACKLINK_STATUS: String = "backlink_status"
  const val KEY_SUBJECT_HUB_PATH: String = "subject_hub_path"
  const val KEY_SUBJECT_HUB_STATUS: String = "subject_hub_status"
  const val KEY_FIELD: String = "field"
  const val KEY_EXPECTED: String = "expected"
  const val KEY_INCLUDE_EXCERPTS: String = "include_excerpts"
  const val KEY_ROOT: String = "root"
  const val KEY_CLASSIFICATION: String = "classification"
  const val KEY_LOADED_BRANCHES: String = "loaded_branches"
  const val KEY_SKIPPED_BRANCHES: String = "skipped_branches"
  const val KEY_AUDIT: String = "audit"
  const val KEY_ACTION: String = "action"
  const val KEY_SUBJECT: String = "subject"
  const val KEY_LOAD_ORDER: String = "load_order"
  const val KEY_MATCHED_TERMS: String = "matched_terms"
  const val KEY_EMOTIONAL_CONTEXT: String = "emotional_context"
  const val KEY_EMOTIONAL_TERMS: String = "emotional_terms"
  const val KEY_NODE_COUNT: String = "node_count"
  const val KEY_PATTERN_LINKS: String = "pattern_links"

  const val STATUS_OK: String = "ok"
  const val STATUS_PERMISSION_DENIED: String = "permission_denied"
  const val STATUS_NOT_FOUND: String = "not_found"
  const val STATUS_INVALID_INPUT: String = "invalid_input"
  const val STATUS_FAILED: String = "failed"

  const val BACKLINK_STATUS_OK: String = "ok"
  const val BACKLINK_STATUS_FAILED: String = "failed"
  const val BACKLINK_STATUS_SKIPPED: String = "skipped"
  const val SUBJECT_HUB_STATUS_CREATED: String = "created"
  const val SUBJECT_HUB_STATUS_UPDATED: String = "updated"
  const val SUBJECT_HUB_STATUS_FAILED: String = "failed"
  const val SUBJECT_HUB_STATUS_SKIPPED: String = "skipped"

  const val PAYLOAD_KIND_STATE: String = "state"
  const val PAYLOAD_KIND_EPISODE: String = "episode"
  const val PAYLOAD_KIND_PATTERN: String = "pattern"
  const val PAYLOAD_KIND_SUBJECT: String = "subject"
  const val PAYLOAD_KIND_EMOTIONAL_STATE: String = "emotional-state"

  const val BRANCH_STATE_PREFERENCES: String = VaultLayout.BRANCH_STATE_PREFERENCES
  const val BRANCH_STATE_ROLES: String = VaultLayout.BRANCH_STATE_ROLES
  const val BRANCH_STATE_KNOWLEDGE: String = VaultLayout.BRANCH_STATE_KNOWLEDGE
  const val BRANCH_STAGING_OBSERVATIONS: String = VaultLayout.BRANCH_STAGING_OBSERVATIONS
  const val BRANCH_STAGING_SENSITIVE: String = VaultLayout.BRANCH_STAGING_SENSITIVE
  const val BRANCH_TIMELINE: String = VaultLayout.BRANCH_TIMELINE

  val ENUM_STATE_CATEGORIES: List<String> = listOf("preference", "role", "knowledge", "fact")
  val ENUM_CONFIDENCES: List<String> = listOf("high", "medium", "low")
  val ENUM_EPISODE_TYPES: List<String> = listOf(
    "purchase",
    "advice-seeking",
    "research",
    "design-doc",
    "question",
    "personal-story",
    "work-interaction",
    "decision",
  )
  val ENUM_INTENSITIES: List<String> = listOf("low", "medium", "high")
  val ENUM_EMOTION_MARKERS: List<String> = listOf(
    "frustration",
    "excitement",
    "anxiety",
    "curiosity",
    "disengagement",
    "satisfaction",
    "confusion",
  )
  val ENUM_PAYLOAD_KINDS: List<String> = listOf(
    PAYLOAD_KIND_STATE,
    PAYLOAD_KIND_EPISODE,
    PAYLOAD_KIND_PATTERN,
    PAYLOAD_KIND_SUBJECT,
    PAYLOAD_KIND_EMOTIONAL_STATE,
  )

  val ENUM_CAPTURE_OBSERVATION_KINDS: List<String> = listOf(PAYLOAD_KIND_STATE, PAYLOAD_KIND_EPISODE)

  const val DECISION_REJECTED: String = "rejected"
  const val DECISION_STAGED_OBSERVATION: String = "staged_observation"
  const val DECISION_STAGED_SENSITIVE: String = "staged_sensitive"
  const val DECISION_STATE_WRITTEN: String = "state_written"
  const val DECISION_STATE_UPDATED: String = "state_updated"
  const val DECISION_EPISODE_WRITTEN: String = "episode_written"
  const val DECISION_EPISODE_UPDATED: String = "episode_updated"
}
