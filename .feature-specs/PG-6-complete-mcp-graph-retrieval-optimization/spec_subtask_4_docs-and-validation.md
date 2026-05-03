# PG-6 Subtask 4 - Docs, end-to-end workflow tests, and final validation

- **Status:** Planned
- **Issue key:** PG-6
- **Subtask:** 4 of 4
- **Sources:** [parent spec](./spec.md)

## Problem

With behavior changes from subtasks 1-3 merged, PG-6 still needs:

- Documentation that teaches the optimized workflow (search/traverse/read before list_branch full).
- A SKILL-33-style worked example agents can imitate.
- An end-to-end workflow test that exercises the whole chain (`session_start -> suggested_actions -> search_nodes / list_branch(mode=index) -> traverse_graph -> read_node`) including read-blocked-branch non-leakage across all stages.
- Final `./gradlew check` validation gate for the parent feature.

## Acceptance Criteria

Parent ACs primarily owned: **9**, **10**.

Subtask-internal criteria:

1. `docs/session-start-retrieval.md` updated with the optimized retrieval workflow diagram and a complete SKILL-33 example covering session_start suggestion, search_nodes call, traverse_graph call, and a terminal read_node call. Includes expected token accounting at each step.
2. README updated to point at the new workflow and the SKILL-33 example, mentioning that retrieval is search-first and bounded by default.
3. `session_start` user-facing docs (whatever module currently documents it) explicitly state: prefer search/traverse/read before `list_branch(mode=full)`.
4. MCP schema descriptions reviewed end-to-end for cost/compactness clarity (any gaps left from subtasks 2-3 are closed here; no functional behavior changes).
5. End-to-end test (kotest funspec) on a fixture vault that includes a SKILL-33-style subject hub, related events, unrelated high-degree hubs, blocked `people/` content, and `staging/sensitive/` content. The test asserts:
   - identifier query produces a `search_nodes` `suggested_action`,
   - `list_branch(mode=index, filter="SKILL-33")` returns compact matches without bodies,
   - `search_nodes("SKILL-33")` finds id matches without decoding unrelated bodies,
   - `traverse_graph(query="SKILL-33", max_depth=2, budget_tokens=...)` returns a bounded subgraph with entrypoints, edges, pruned entries, and path explanations,
   - token accounting separates metadata/body/pruned estimates across all responses,
   - `people/` and `staging/sensitive/` content is never present in any response (search hits, snippets, links, suggested_actions, suggested_reads, pruned, edges).
6. `./gradlew check` passes via bill-quality-check (final parent gate).

## Non-goals

- Any new behavior. This subtask is documentation + final integration tests + validation only.
- Vault hygiene migrations, broken-link cleanup.
- Plus all parent non-goals.

## Dependencies

- Subtask 1, 2, and 3 all merged.

## Validation strategy

bill-quality-check (`./gradlew check`). This is the final PG-6 validation gate; the run must be green for the parent feature to be considered complete.

## Files likely touched

- `docs/session-start-retrieval.md`
- `README.md`
- Any module-level README or `agent/history.md` that documents `session_start` semantics
- New end-to-end test under `mcp-server/src/test/.../EndToEndRetrievalSpec.kt`
- Test fixture vault assets under the test resources directory.

## Handoff prompt

Run `bill-feature-implement` on `.feature-specs/PG-6-complete-mcp-graph-retrieval-optimization/spec_subtask_4_docs-and-validation.md`.
