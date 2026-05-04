# PG-6: traversal-foundation

Status: Complete

## Sources

- Source spec: `.feature-specs/PG-6-traverse-and-session-start-retrieval-optimization/spec_subtask_1_traversal-foundation.md`
- Parent feature: PG-6 traverse and session-start retrieval optimization

## Acceptance Criteria

1. `traverse_graph` domain/data service accepts `query`, `start_ids`, `branches`, `edge_types`, `max_depth`, `max_nodes`, `budget_tokens`, `include_bodies`, and `rank_by`, with conservative defaults ready for MCP parser wiring.
2. Traversal result model returns `entrypoints`, scored nodes, labeled weighted edges, `pruned`, prioritized `suggested_reads`, and `estimated_tokens`.
3. Edge classification supports `link`, `backlink`, `subject_evidence`, `timeline`, `state`, `pattern`, `contradiction`, and `background`.
4. Ranking boosts exact matches, subject hubs, direct evidence, and recent events for recency queries, while penalizing broad/high-degree/unrelated hubs.
5. `max_nodes` and `budget_tokens` pruning moves skipped candidates into `pruned` with stable reasons.
6. Read-blocked branches and ids, including `people/` and `staging/sensitive/`, are filtered by service-owned policy before results, edges, suggested reads, token accounting, or pruned output are produced.
7. Traversal uses existing graph-index and token-estimation foundations instead of adding embeddings, vector search, or a new retrieval surface beyond `traverse_graph`.

## Consolidated Spec

# PG-6 Subtask 1: Traversal Foundation

Parent spec: [spec.md](spec.md)

## Scope

Implement the core/domain and core/data foundation for `traverse_graph` without exposing the MCP tool yet. This subtask owns the traversal request/response contracts, index-backed candidate expansion, scoring, edge labeling, pruning, policy filtering, and token estimation needed by later runtime wiring.

Likely files:
- `core/domain/search/**`
- `core/domain/retrieval/**`
- `core/data/search/**`
- `core/data/retrieval/**`
- `core/data/di/**`

## Acceptance Criteria

1. `traverse_graph` domain/data service accepts `query`, `start_ids`, `branches`, `edge_types`, `max_depth`, `max_nodes`, `budget_tokens`, `include_bodies`, and `rank_by`, with conservative defaults ready for MCP parser wiring.
2. Traversal result model returns `entrypoints`, scored nodes, labeled weighted edges, `pruned`, prioritized `suggested_reads`, and `estimated_tokens`.
3. Edge classification supports `link`, `backlink`, `subject_evidence`, `timeline`, `state`, `pattern`, `contradiction`, and `background`.
4. Ranking boosts exact matches, subject hubs, direct evidence, and recent events for recency queries, while penalizing broad/high-degree/unrelated hubs.
5. `max_nodes` and `budget_tokens` pruning moves skipped candidates into `pruned` with stable reasons.
6. Read-blocked branches and ids, including `people/` and `staging/sensitive/`, are filtered by service-owned policy before results, edges, suggested reads, token accounting, or pruned output are produced.
7. Traversal uses existing graph-index and token-estimation foundations instead of adding embeddings, vector search, or a new retrieval surface beyond `traverse_graph`.

## Non-Goals

- MCP tool registration, schema descriptions, parser changes, or JSON formatter output.
- `session_start` changes, including `suggested_actions`.
- README or walkthrough documentation.
- Embeddings, vector search, or replacing `read_node`.

## Dependencies

This is the first subtask and has no implementation dependency on later PG-6 subtasks. Reuse existing graph-index warming, `TokenEstimator`, `VaultPolicy`, metadata-first search ranking, and kotlin-inject binding patterns described in the parent planning digest.

## Validation Strategy

Run repo-native targeted tests if available for touched domain/data packages, then run `bill-quality-check` before handoff when practical. If broad MCP tests fail because runtime wiring is intentionally absent, document that this subtask stops at the data/domain boundary.

## Recommended Next Prompt

Run bill-feature-implement on `.feature-specs/PG-6-traverse-and-session-start-retrieval-optimization/spec_subtask_1_traversal-foundation.md`.
