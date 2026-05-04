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
  const val TOOL_SEARCH_NODES: String = "search_nodes"
  const val TOOL_TRAVERSE_GRAPH: String = "traverse_graph"
  const val TOOL_SESSION_START: String = "session_start"

  const val DESC_WRITE_STATE: String =
    "Write or update a state node (Tier 1 capture for durable facts). " +
      "If the target already exists, the prior version is archived under outdated/resolved/."
  const val DESC_CAPTURE_OBSERVATION: String =
    "Submit a candidate observation. Personal-graph decides whether to reject, stage, save, or update it; " +
      "event-like observations without durable reusable structure are staged unless they are complete episodes."
  const val DESC_WRITE_EPISODE: String =
    "Write or update an episode node, append it to a canonical subject hub, and create a timeline backlink stub. " +
      "If the target already exists, the prior version is archived under outdated/resolved/."
  const val DESC_WRITE_TO_STAGING: String = "Write a state-shaped observation to staging/observations/."
  const val DESC_FLAG_SENSITIVE: String = "Re-route an existing or inline node to staging/sensitive/ for batch review."
  const val DESC_LIST_PENDING_SENSITIVE: String =
    "List ids and short excerpts of items currently in staging/sensitive/."
  const val DESC_READ_NODE: String =
    "Read a full node body by id after session_start returns a precise available_map or suggested_reads follow-up. " +
      "Reads under people/ are blocked."
  const val DESC_LIST_BRANCH: String =
    "List nodes under a branch path as an explicit follow-up. Defaults to mode=full (includes bodies). " +
      "Use mode=index for compact metadata-only entries when full bodies are not needed; this avoids " +
      "decoding bodies. Supports filter (substring on id), limit, include_links, include_body. " +
      "Reads under people/ are blocked; staging/sensitive/ is hard-excluded."
  const val DESC_SEARCH_NODES: String =
    "Search nodes by id, metadata (subject/topic/alias/hypothesis/domain/branch), or body. " +
      "Metadata-first: exact id/path/alias/title matches are answered from the graph index without " +
      "decoding bodies. Body scan only runs when body_fallback=true (default) and metadata matches " +
      "are insufficient. Returns ranked compact hits with snippet, links, match_fields, score plus a " +
      "search_plan and estimated_tokens. Reads under people/ are blocked; staging/sensitive/ is hard-excluded."
  const val DESC_TRAVERSE_GRAPH: String =
    "Bounded graph traversal around query/start_ids. Returns entrypoints, scored nodes, labeled weighted " +
      "edges, pruned candidates, prioritized suggested_reads, and estimated_tokens. Edge labels are " +
      "link, backlink, subject_evidence, timeline, state, pattern, contradiction, and background. " +
      "max_nodes and budget_tokens cap the response; pruned candidates are surfaced with reasons instead of " +
      "silently expanding the graph. Read-blocked branches and ids are filtered before formatting."
  const val DESC_SESSION_START: String =
    "Map-first session-start retrieval: load bounded root context, return a compact available_map, " +
      "suggest search_nodes/list_branch(index) follow-ups before any full-body read, and expose " +
      "suggested_actions plus estimated_tokens for the retrieval plan."

  const val DESC_FIELD_STATE_ID: String =
    "Node id. Accepts canonical plural prefix (e.g. state/roles/<leaf>), or a bare leaf which is " +
      "routed via `category`. Singular-prefix forms (state/role/, state/preference/, state/fact/) " +
      "are rejected — use the canonical plural form named in `expected`. " +
      "Bare leaves are slugified without word bounding."
  const val DESC_FIELD_NODE_ID: String =
    "Node id; canonical form `<branch>/<leaf>` (e.g. domains/creative/events/<leaf> for episodes). " +
      "Generated/topic-derived ids are slug-bounded; bare leaves are slugified without word bounding; " +
      "explicit canonical paths are preserved."
  const val DESC_FIELD_DATE: String =
    "ISO-8601 instant in UTC, e.g. 2026-04-25T00:00:00Z. Date-only values are rejected."
  const val DESC_FIELD_TARGET_PATH: String =
    "Existing node id to re-route. Reads under people/ are blocked. Must parse as a valid node id."
  const val DESC_FIELD_BRANCH: String =
    "Branch path under the vault root, e.g. state/roles or domains/work/capmo. Reads under people/ are blocked. " +
      "Use after session_start available_map/suggested_reads when full branch bodies are explicitly needed."
  const val DESC_FIELD_LINKS: String =
    "Wikilink targets as node ids. Entries that fail to parse are silently dropped without error; " +
      "canonical-prefix enforcement is not currently performed."
  const val DESC_FIELD_PAYLOAD_KIND: String =
    "Expected payload kind for the targeted node. Must match the actual node type or the call is " +
      "rejected with invalid_input."
  const val DESC_FIELD_SCOPE: String =
    "Optional state scope, e.g. work/capmo or creative/music. Omit for global state."
  const val DESC_FIELD_SCOPES: String =
    "Optional state scopes for state that applies to multiple domains. Omit for global state."
  const val DESC_FIELD_RETRIEVAL_MODE: String =
    "Session-start retrieval mode. Defaults to map-first: loaded_context contains bounded root orientation, " +
      "available_map is compact, suggested_reads and suggested_actions name follow-up paths, and " +
      "estimated_tokens reports the retrieval cost. full-loading is an explicit opt-in for callers that " +
      "intentionally want loaded node bodies."
  const val DESC_FIELD_SEARCH_QUERY: String =
    "Search query string. Matched against ids, metadata (subject/topic/alias/hypothesis/domain/branch) " +
      "and optionally body. Recency keywords (recent/latest/today/merged/opened/status) boost score."
  const val DESC_FIELD_SEARCH_FIELDS: String =
    "Fields to search. Default [id, metadata, body]. Drop body to stay metadata-only."
  const val DESC_FIELD_BODY_FALLBACK: String =
    "If true (default), body is scanned only when metadata-only matches are insufficient. " +
      "Set false to suppress body scan entirely."
  const val DESC_FIELD_LIST_MODE: String =
    "List mode. Defaults to full (includes bodies). Use index for compact metadata-only entries; " +
      "no bodies are decoded in index mode."
  const val DESC_FIELD_LIST_FILTER: String =
    "Optional substring filter applied to entry ids before formatting (e.g. SKILL-33)."
  const val DESC_FIELD_LIST_LIMIT: String =
    "Optional maximum number of entries returned. Omit for no cap."
  const val DESC_FIELD_INCLUDE_LINKS: String =
    "If true, include link targets per entry. Default false to keep responses compact."
  const val DESC_FIELD_INCLUDE_BODY: String =
    "If true, include the node body for each entry. Default true for mode=full, false for mode=index."
  const val DESC_FIELD_SEARCH_BRANCHES: String =
    "Optional array of branch paths under the vault root to scope the search " +
      "(e.g. [\"state\", \"domains/work\"]). Omit to search a curated default set " +
      "(state, domains, patterns, emotional-states, timeline, staging/observations, outdated). " +
      "Read-blocked or index-excluded branches are silently dropped."
  const val DESC_FIELD_SEARCH_LIMIT: String =
    "Maximum number of hits returned. Defaults to 20. Hits are ranked metadata-first; lower the cap " +
      "to keep responses compact when only the top result matters."
  const val DESC_FIELD_SEARCH_INCLUDE_BODY: String =
    "If true, include the full node body for each hit. Defaults to false to keep responses compact; " +
      "use read_node to fetch a body once a hit looks relevant."
  const val DESC_FIELD_TRAVERSE_QUERY: String =
    "Traversal seed query. Exact ids, branches, and metadata hints are all treated as entrypoint signals."
  const val DESC_FIELD_TRAVERSE_START_IDS: String =
    "Optional start node ids to seed traversal directly. Use canonical node ids."
  const val DESC_FIELD_TRAVERSE_BRANCHES: String =
    "Optional branch scope for traversal. Omit to use the default allowed retrieval branches."
  const val DESC_FIELD_TRAVERSE_EDGE_TYPES: String =
    "Edge labels to include. Default includes the traversal vocabulary: link, backlink, subject_evidence, " +
      "timeline, state, pattern, contradiction, and background."
  const val DESC_FIELD_TRAVERSE_MAX_DEPTH: String =
    "Maximum hop distance from the selected entrypoints. Defaults to 1 and must be non-negative."
  const val DESC_FIELD_TRAVERSE_MAX_NODES: String =
    "Maximum number of nodes returned. Nodes beyond this cap are moved into pruned with reasons."
  const val DESC_FIELD_TRAVERSE_BUDGET_TOKENS: String =
    "Token budget for the traversal response. When the next node would exceed the budget, it is pruned " +
      "with a stable reason."
  const val DESC_FIELD_TRAVERSE_INCLUDE_BODIES: String =
    "If true, include node bodies in the traversal response. Defaults to false to keep the result compact."
  const val DESC_FIELD_TRAVERSE_RANK_BY: String =
    "Ranking hints for traversal. The parser accepts the scoring rubric terms exact_id_match, edge_weight, " +
      "recency, and branch_relevance; recency is the active ranking toggle."

  const val KEY_ID: String = "id"
  const val KEY_OBSERVATION: String = "observation"
  const val KEY_SOURCE_CONTEXT: String = "source_context"
  const val KEY_SUGGESTED_KIND: String = "suggested_kind"
  const val KEY_MESSAGE: String = "message"
  const val KEY_RETRIEVAL_MODE: String = "retrieval_mode"
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
  const val KEY_ARCHIVED_PATHS: String = "archived_paths"
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
  const val KEY_SCOPE: String = "scope"
  const val KEY_SCOPES: String = "scopes"
  const val KEY_LOADED_FULL_BODY_CONTEXT: String = "loaded_full_body_context"
  const val KEY_COMPACT_MAP_ENTRIES: String = "compact_map_entries"
  const val KEY_LOADED_CONTEXT: String = "loaded_context"
  const val KEY_AVAILABLE_MAP: String = "available_map"
  const val KEY_SUGGESTED_READS: String = "suggested_reads"
  const val KEY_SUGGESTED_ACTIONS: String = "suggested_actions"
  const val KEY_AUDIT_ENTRIES: String = "audit_entries"
  const val KEY_SOURCE: String = "source"
  const val KEY_KIND: String = "kind"
  const val KEY_TYPE: String = "type"
  const val KEY_SUMMARY: String = "summary"
  const val KEY_ALIASES: String = "aliases"
  const val KEY_UPDATED: String = "updated"
  const val KEY_LINK_COUNT: String = "link_count"
  const val KEY_PRIORITY: String = "priority"
  const val KEY_TOOL: String = "tool"
  const val KEY_ARGS: String = "args"
  const val KEY_QUERY: String = "query"
  const val KEY_BRANCHES: String = "branches"
  const val KEY_LIMIT: String = "limit"
  const val KEY_SEARCH_FIELDS: String = "search_fields"
  const val KEY_BODY_FALLBACK: String = "body_fallback"
  const val KEY_MODE: String = "mode"
  const val KEY_FILTER: String = "filter"
  const val KEY_INCLUDE_LINKS: String = "include_links"
  const val KEY_INCLUDE_BODY: String = "include_body"
  const val KEY_SEARCH_PLAN: String = "search_plan"
  const val KEY_METADATA_INDEX_USED: String = "metadata_index_used"
  const val KEY_BODY_FALLBACK_USED: String = "body_fallback_used"
  const val KEY_BRANCHES_SEARCHED: String = "branches_searched"
  const val KEY_START_IDS: String = "start_ids"
  const val KEY_EDGE_TYPES: String = "edge_types"
  const val KEY_MAX_DEPTH: String = "max_depth"
  const val KEY_MAX_NODES: String = "max_nodes"
  const val KEY_BUDGET_TOKENS: String = "budget_tokens"
  const val KEY_INCLUDE_BODIES: String = "include_bodies"
  const val KEY_RANK_BY: String = "rank_by"
  const val KEY_ENTRYPOINTS: String = "entrypoints"
  const val KEY_EDGES: String = "edges"
  const val KEY_PRUNED: String = "pruned"
  const val KEY_FROM: String = "from"
  const val KEY_TO: String = "to"
  const val KEY_WEIGHT: String = "weight"
  const val KEY_DISTANCE: String = "distance"
  const val KEY_LABEL: String = "label"
  const val KEY_ESTIMATED_TOKENS: String = "estimated_tokens"
  const val KEY_RESPONSE_TOTAL: String = "response_total"
  const val KEY_METADATA_TOKENS: String = "metadata_tokens"
  const val KEY_BODY_TOKENS: String = "body_tokens"
  const val KEY_PRUNED_BODY_TOKENS: String = "pruned_body_tokens"
  const val KEY_MATCH_FIELDS: String = "match_fields"
  const val KEY_SNIPPET: String = "snippet"
  const val KEY_SCORE: String = "score"
  const val KEY_HITS: String = "hits"
  const val KEY_ENTRIES: String = "entries"

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
  val ENUM_RETRIEVAL_MODES: List<String> = listOf("map-first", "full-loading")

  const val LIST_MODE_FULL: String = "full"
  const val LIST_MODE_INDEX: String = "index"
  val ENUM_LIST_MODES: List<String> = listOf(LIST_MODE_FULL, LIST_MODE_INDEX)

  const val SEARCH_FIELD_ID: String = "id"
  const val SEARCH_FIELD_METADATA: String = "metadata"
  const val SEARCH_FIELD_BODY: String = "body"
  val ENUM_SEARCH_FIELDS: List<String> = listOf(SEARCH_FIELD_ID, SEARCH_FIELD_METADATA, SEARCH_FIELD_BODY)

  const val TRAVERSAL_EDGE_TYPE_LINK: String = "link"
  const val TRAVERSAL_EDGE_TYPE_BACKLINK: String = "backlink"
  const val TRAVERSAL_EDGE_TYPE_SUBJECT_EVIDENCE: String = "subject_evidence"
  const val TRAVERSAL_EDGE_TYPE_TIMELINE: String = "timeline"
  const val TRAVERSAL_EDGE_TYPE_STATE: String = "state"
  const val TRAVERSAL_EDGE_TYPE_PATTERN: String = "pattern"
  const val TRAVERSAL_EDGE_TYPE_CONTRADICTION: String = "contradiction"
  const val TRAVERSAL_EDGE_TYPE_BACKGROUND: String = "background"
  val ENUM_TRAVERSAL_EDGE_TYPES: List<String> = listOf(
    TRAVERSAL_EDGE_TYPE_LINK,
    TRAVERSAL_EDGE_TYPE_BACKLINK,
    TRAVERSAL_EDGE_TYPE_SUBJECT_EVIDENCE,
    TRAVERSAL_EDGE_TYPE_TIMELINE,
    TRAVERSAL_EDGE_TYPE_STATE,
    TRAVERSAL_EDGE_TYPE_PATTERN,
    TRAVERSAL_EDGE_TYPE_CONTRADICTION,
    TRAVERSAL_EDGE_TYPE_BACKGROUND,
  )

  const val TRAVERSAL_RANK_BY_EXACT_ID_MATCH: String = "exact_id_match"
  const val TRAVERSAL_RANK_BY_EDGE_WEIGHT: String = "edge_weight"
  const val TRAVERSAL_RANK_BY_RECENCY: String = "recency"
  const val TRAVERSAL_RANK_BY_BRANCH_RELEVANCE: String = "branch_relevance"
  val ENUM_TRAVERSAL_RANK_BY: List<String> = listOf(
    TRAVERSAL_RANK_BY_EXACT_ID_MATCH,
    TRAVERSAL_RANK_BY_EDGE_WEIGHT,
    TRAVERSAL_RANK_BY_RECENCY,
    TRAVERSAL_RANK_BY_BRANCH_RELEVANCE,
  )

  const val DECISION_REJECTED: String = "rejected"
  const val DECISION_STAGED_OBSERVATION: String = "staged_observation"
  const val DECISION_STAGED_SENSITIVE: String = "staged_sensitive"
  const val DECISION_STATE_WRITTEN: String = "state_written"
  const val DECISION_STATE_UPDATED: String = "state_updated"
  const val DECISION_EPISODE_WRITTEN: String = "episode_written"
  const val DECISION_EPISODE_UPDATED: String = "episode_updated"
}
