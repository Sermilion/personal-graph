# PG-6 Subtask 2 - search_nodes index-first and list_branch index mode

- **Status:** Complete
- **Issue key:** PG-6
- **Subtask:** 2 of 4
- **Sources:** [parent spec](./spec.md)

## Problem

With the graph index and token estimator from subtask 1 in place, the two cheapest MCP retrieval surfaces can be made index-aware:

- `search_nodes` should consult the index (id/path/alias/title/subject/topic) before falling back to body scanning, returning compact snippets and a `search_plan`.
- `list_branch` should gain a non-default `mode=index` with `filter`, `limit`, `include_links`, `include_body=false` that returns ids/type/metadata/snippet/link counts/optional links, never full bodies unless explicitly requested.

Both responses must also report `estimated_tokens.{response_total,metadata_tokens,body_tokens,pruned_body_tokens}` using the new `TokenEstimator`.

Default `list_branch` behavior (full-body listing) must remain backward compatible.

## Acceptance Criteria

Parent ACs primarily owned: **2**, **3**. Partially contributes to **8** for these two surfaces (parent AC 8 final coverage closes in subtask 3).

Subtask-internal criteria:

1. `search_nodes` evolves to use `GraphIndexRepository` for exact id, path, alias, title, subject, topic, hypothesis matches first. Body scan is reached only when `body_fallback=true` (default) and metadata matches are insufficient. Implementation must avoid decoding full bodies when index metadata is enough.
2. `search_nodes` input adds `search_fields` (default `["id","metadata","body"]`) and `body_fallback` (default `true`). Output adds `search_plan` (`metadata_index_used`, `body_fallback_used`, `branches_searched`) and `estimated_tokens`. Existing fields (`status`, `nodes`, `score`, `match_fields`, `snippet`, `links`) remain.
3. `list_branch` input adds `mode` (default `full`), `filter`, `limit`, `include_links` (default `false`), `include_body` (default `true` for `mode=full`, `false` for `mode=index`). Default invocation (no new fields) returns the same shape as today.
4. `list_branch(mode=index)` returns compact entries: `id`, `type`, `domain`, `subject` (or category-appropriate label), `snippet`, optional `links`, `match_fields`, `score`. No full bodies. Response includes `mode`, `estimated_tokens` with `pruned_body_tokens` reflecting bodies it would have returned in full mode.
5. Ranking inside `search_nodes` follows the parent ranking rules: exact full id/path > leaf id/slug > subject/topic/alias/hypothesis > domain/branch relevance > body mention; recency boost when query contains recent/latest/today/merged/opened/status.
6. MCP schema descriptions for `search_nodes` and `list_branch` are updated to teach cost/compactness defaults; these new fields surface in `ToolSchemas`/`ToolSchemaBuilder` and are documented in tool descriptions (full README/docs prose stays in subtask 4, but schema descriptions ship here so the schema is correct).
7. Read-blocked branches and ids (per VaultPolicy and the `people/` + `staging/sensitive/` hard exclusions) never appear in matches, snippets, links, or counts.
8. Tests (kotest funspec): `search_nodes("SKILL-33")` finds id matches without decoding unrelated branch bodies; body fallback engages only when needed; `list_branch(mode=index, filter="SKILL-33")` returns compact entries with no bodies; default `list_branch` shape unchanged; token accounting fields present and consistent; blocked branches absent in all responses.
9. `./gradlew check` passes via bill-quality-check.

## Non-goals

- `traverse_graph` upgrade (subtask 3).
- `session_start.suggested_actions` (subtask 3).
- README / docs/session-start-retrieval.md prose updates (subtask 4); schema-level descriptions are in scope here.
- Plus all parent non-goals.

## Dependencies

- Subtask 1 must be merged. This subtask consumes `GraphIndexRepository` and `TokenEstimator`.

## Validation strategy

bill-quality-check (`./gradlew check`).

## Files likely touched

- `mcp-server/src/main/kotlin/.../tools/VaultMcpTools.kt` (search_nodes, list_branch dispatch)
- `mcp-server/src/main/kotlin/.../tools/VaultGraphMcpTools.kt` (if search_nodes lives there)
- `mcp-server/src/main/kotlin/.../schema/ToolSchemas.kt`, `ToolSchemaBuilder.kt`
- `mcp-server/src/main/kotlin/.../formatters/VaultMcpToolFormatters.kt`
- New domain service for index-first search e.g. `core/domain/.../search/IndexFirstNodeSearchService.kt`
- Tests under `mcp-server/src/test` and the new service's test directory.

## Handoff prompt

Run `bill-feature-implement` on `.feature-specs/PG-6-complete-mcp-graph-retrieval-optimization/spec_subtask_2_search-and-list-branch-upgrade.md`.
