# PG-6 traverse-and-session-start-retrieval-optimization

Status: In Progress

## Sources

- Briefing-provided subtask spec path: `.feature-specs/PG-6-complete-mcp-graph-retrieval-optimization/spec_subtask_3_traverse-and-session-start.md`
- Parent feature spec directory: `.feature-specs/PG-6-complete-mcp-graph-retrieval-optimization/`

## Acceptance Criteria

1. traverse_graph accepts query, start_ids, branches, edge_types, max_depth, max_nodes, budget_tokens, include_bodies, and rank_by, with conservative defaults.
2. traverse_graph returns entrypoints, scored nodes, labeled weighted edges, pruned, prioritized suggested_reads, and estimated_tokens.
3. Edge labels include link, backlink, subject_evidence, timeline, state, pattern, contradiction, and background.
4. Ranking boosts exact matches, subject hubs, direct evidence, and recent events for recency queries, while penalizing broad/high-degree/unrelated hubs.
5. max_nodes and budget_tokens pruning moves skipped candidates into pruned with reasons.
6. Read-blocked branches and ids, including people/ and staging/sensitive/, never appear in traversal outputs.
7. session_start adds backward-compatible suggested_actions.
8. Identifier detection covers issue keys like SKILL-33 and PG-6, PR #91, branch names, and canonical node path fragments, suggesting search_nodes with index-style args.
9. Domain-classified prompts suggest branch-constrained search_nodes or list_branch(mode=index) before full body reads.
10. session_start includes token accounting via estimated_tokens.
11. MCP schema descriptions expose the new traverse_graph and session_start fields/defaults.
12. Kotest coverage includes traversal scoring, hub-explosion prevention, all edge labels, identifier suggestions, domain-prompt suggestions, blocked-branch absence, and token accounting.
13. ./gradlew check passes through bill-quality-check.

## Non-Goals

- README and docs/session-start-retrieval.md prose.
- SKILL-33 walkthrough documentation.
- New retrieval surfaces beyond traverse_graph and session_start.
- Embeddings, vector search, or replacing read_node.
- Parent spec non-goals.

## Consolidated Spec Content

Upgrade `traverse_graph` to scored, bounded, explainable traversal with semantic edge labels and budget pruning.

Upgrade `session_start` to detect identifier-like queries and emit `suggested_actions` additive to `suggested_reads`, and report token accounting.
