# PG-6 Subtask 3 - traverse_graph scoring and session_start suggested_actions

- **Status:** Planned
- **Issue key:** PG-6
- **Subtask:** 3 of 4
- **Sources:** [parent spec](./spec.md)

## Problem

With the index, token estimator, and the cheap retrieval surfaces (search_nodes index-first, list_branch index mode) ready, the remaining MCP behavior changes are:

1. Upgrade `traverse_graph` to a scored, bounded, explainable traversal with semantic edge labels and budget pruning.
2. Upgrade `session_start` to detect identifier-like queries and emit `suggested_actions` (additive to `suggested_reads`), and to report token accounting on its response.

These two together complete the optimized workflow: `session_start -> suggested_actions -> search_nodes/list_branch(index) -> traverse_graph -> read_node`.

## Acceptance Criteria

Parent ACs primarily owned: **1**, **5**, **6**, **7**. Closes parent AC **8** (token accounting) for `traverse_graph` and `session_start`.

Subtask-internal criteria:

### traverse_graph

1. Inputs: `query`, `start_ids`, `branches`, `edge_types`, `max_depth`, `max_nodes`, `budget_tokens`, `include_bodies`, `rank_by`. Defaults conservative: `max_depth=1`, `include_bodies=false`, hard `max_nodes` and `budget_tokens` caps.
2. Output: `entrypoints`, `nodes` (with `distance`, `score`, `reason`, `snippet`), `edges` (with `from`, `to`, `type`, `weight`, `reason`), `pruned` (with `id`, `reason`), `suggested_reads` (with `priority`), `estimated_tokens`.
3. Edge labels supported: `link`, `backlink`, `subject_evidence`, `timeline`, `state`, `pattern`, `contradiction`, `background`. Subject hubs expose evidence links distinctly from generic body links when subject body structure makes that clear; timeline edges treated as chronological index links rather than duplicate evidence.
4. Ranking: exact id/title/query matches boosted; subject hubs and direct evidence boosted; broad hubs / high-degree / unrelated background penalized; recency boosts events when query contains recent/latest/today/merged/opened/status. Ranking prevents broad-hub explosions on real fixtures.
5. Budget enforcement: when adding the next node would exceed `budget_tokens` or `max_nodes`, it is moved to `pruned` with a reason.
6. Read-blocked branches and ids (incl. `people/`, `staging/sensitive/`) never appear in entrypoints, nodes, edges, suggested_reads, or pruned.

### session_start

7. Response gains `suggested_actions: [{tool, args, reason, priority}]`. Existing `suggested_reads` remains backward compatible (no clients break, no fields removed/renamed).
8. Identifier detector recognizes at minimum: ticket/issue keys (e.g. `SKILL-33`, `PG-6`), `PR #91`-style references, branch names, and canonical node path fragments. For these, suggest `search_nodes` with appropriate `branches`/`limit`/`include_body=false`.
9. For domain-classified prompts (no identifier), suggest a branch-constrained `search_nodes` or `list_branch(mode=index)` before any `list_branch(mode=full)` body read.
10. `session_start` response includes `estimated_tokens` (response_total/metadata_tokens/body_tokens) using the shared estimator.

### Cross-cutting

11. MCP schema descriptions for `traverse_graph` and `session_start` updated to expose the new fields and defaults.
12. Tests (kotest funspec): bounded subgraph traversal on a SKILL-33-style fixture; hub-explosion prevention (a high-degree unrelated subject is pruned); each edge label is exercised at least once; identifier detection (SKILL-33, PG-6, PR #91) routes to `search_nodes` suggestion; domain prompt routes to branch-constrained search; blocked branches absent across all responses; token accounting fields populated and additive.
13. `./gradlew check` passes via bill-quality-check.

## Non-goals

- README and `docs/session-start-retrieval.md` prose / SKILL-33 walkthrough (subtask 4); schema descriptions are in scope here.
- New retrieval surfaces beyond traverse_graph and session_start.
- Embeddings, semantic vector search, or replacement of `read_node`.
- Plus all parent non-goals.

## Dependencies

- Subtask 1 (index, token estimator).
- Subtask 2 (index-first search; traverse_graph entrypoint discovery and session_start suggestions both prefer the same index-first search behavior, and session_start's domain-prompt suggestion targets `list_branch(mode=index)`).

## Validation strategy

bill-quality-check (`./gradlew check`).

## Files likely touched

- `mcp-server/src/main/kotlin/.../tools/VaultGraphMcpTools.kt` (traverse_graph)
- `mcp-server/src/main/kotlin/.../tools/VaultMcpTools.kt` or session-start tool wiring
- `core/domain/.../session/PersonalGraphSessionStartRetrievalService.kt` (or sibling) — identifier detector, suggested_actions assembly, token accounting
- `core/domain/.../graph/Traversal*.kt` (scoring, edge labeling, budget pruning) — new
- `mcp-server/.../schema/ToolSchemas.kt`, `GraphToolSchemaBuilder.kt`
- `mcp-server/.../formatters/VaultMcpToolFormatters.kt`
- Tests under `core/domain/src/test`, `mcp-server/src/test`.

## Handoff prompt

Run `bill-feature-implement` on `.feature-specs/PG-6-complete-mcp-graph-retrieval-optimization/spec_subtask_3_traverse-and-session-start.md`.
