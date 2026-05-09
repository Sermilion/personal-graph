# PG-6 Subtask 3: Validation

Status: Complete

Parent spec: [spec.md](spec.md)

## Scope

Add and harden the Kotest coverage required for the completed traversal and session-start optimization, then run the full repository quality gate. This subtask should focus on behavior verification and small root-cause fixes uncovered by tests, not new feature scope.

Likely files:
- `core/**/src/test/**`
- `mcp-server/**/src/test/**`
- Test fixtures or helpers already used by search, branch listing, traversal, retrieval, or MCP runtime tests.

## Acceptance Criteria

1. Kotest coverage verifies traversal scoring, including exact-match boosts, subject-hub/direct-evidence boosts, recency-query recent-event boosts, and penalties for broad/high-degree/unrelated hubs.
2. Kotest coverage verifies hub-explosion prevention through `max_nodes` and `budget_tokens` pruning, including stable `pruned` reasons.
3. Kotest coverage verifies all traversal edge labels: `link`, `backlink`, `subject_evidence`, `timeline`, `state`, `pattern`, `contradiction`, and `background`.
4. Kotest coverage verifies identifier suggestions for issue keys such as `SKILL-33` and `PG-6`, PR references such as `PR #91`, branch names, and canonical node path fragments, including index-style `search_nodes` args.
5. Kotest coverage verifies domain-prompt suggestions for branch-constrained `search_nodes` or `list_branch(mode=index)` before full body reads.
6. Kotest coverage verifies read-blocked branches and ids, including `people/` and `staging/sensitive/`, never appear in traversal outputs or session-start suggestions.
7. Kotest coverage verifies `estimated_tokens` accounting for traversal and `session_start`.
8. `./gradlew check` passes through `bill-quality-check`.

## Non-Goals

- Adding new production retrieval capabilities beyond small fixes required to satisfy tests.
- README or walkthrough documentation.
- Embeddings, vector search, or replacing `read_node`.
- Feature-flag work; PG-6 uses no feature flag.

## Dependencies

Depends on Subtasks 1 and 2 because the tests should cover the completed data/domain foundation plus MCP/session runtime behavior. Use existing Kotest FunSpec and mockk conventions, avoiding relaxed mocks and suppressions.

## Validation Strategy

Run `bill-quality-check`, which routes this Gradle/Kotlin JVM repo to `./gradlew check`. Fix failures at the root cause without suppressions.

## Recommended Next Prompt

Run bill-feature-implement on `.feature-specs/PG-6-traverse-and-session-start-retrieval-optimization/spec_subtask_3_validation.md`.
