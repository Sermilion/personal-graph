# PG-6 Subtask 2: Runtime And Session Wiring

Status: Complete

Parent spec: [spec.md](spec.md)

## Scope

Expose the traversal foundation through MCP and upgrade `session_start` suggestions while preserving backward compatibility. This subtask owns parser/schema/formatter/tool registration, MCP DI/runtime wiring, and session-start suggestion behavior that routes users toward index-first retrieval before full body reads.

Likely files:
- `mcp-server/tools/**`
- `mcp-server/runtime/**`
- `mcp-server/di/**`
- `core/domain/retrieval/**`
- `core/data/retrieval/**`

## Acceptance Criteria

1. MCP `traverse_graph` accepts `query`, `start_ids`, `branches`, `edge_types`, `max_depth`, `max_nodes`, `budget_tokens`, `include_bodies`, and `rank_by`, applying conservative defaults and non-negative/bounded validation consistent with existing `search_nodes` and `list_branch` parser patterns.
2. MCP `traverse_graph` returns JSON containing `entrypoints`, scored nodes, labeled weighted edges, `pruned`, prioritized `suggested_reads`, and `estimated_tokens`.
3. MCP schema descriptions expose the new `traverse_graph` fields, defaults, edge-label vocabulary, pruning semantics, and token budget behavior.
4. `session_start` remains backward compatible while adding `suggested_actions`.
5. Identifier detection covers issue keys such as `SKILL-33` and `PG-6`, PR references such as `PR #91`, branch names, and canonical node path fragments.
6. Identifier suggestions prefer `search_nodes` with index-style args.
7. Domain-classified prompts suggest branch-constrained `search_nodes` or `list_branch(mode=index)` before full body reads.
8. `session_start` includes `estimated_tokens` accounting in the MCP output.
9. Read-blocked branches and ids remain absent from traversal and session-start suggestions after MCP formatting.

## Non-Goals

- Reworking traversal scoring internals beyond integration fixes required by MCP output.
- README or walkthrough documentation.
- New retrieval surfaces beyond `traverse_graph` and existing `session_start`.
- Embeddings, vector search, or replacing `read_node`.

## Dependencies

Depends on Subtask 1 because runtime wiring should call the finalized traversal service and result contracts rather than inventing MCP-only behavior. Keep MCP adapters thin; service layers own policy enforcement, ranking, pruning, and token accounting.

## Validation Strategy

Run targeted MCP parser/schema/formatter/runtime tests where available, then run `bill-quality-check` before handoff when practical. Any remaining coverage gaps should be explicitly listed for Subtask 3.

## Recommended Next Prompt

Run bill-feature-implement on `.feature-specs/PG-6-traverse-and-session-start-retrieval-optimization/spec_subtask_2_runtime-session-wiring.md`.
