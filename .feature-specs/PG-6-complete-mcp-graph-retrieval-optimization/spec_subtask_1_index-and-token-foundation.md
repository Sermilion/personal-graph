# PG-6 Subtask 1 - Graph index and token estimator foundation

- **Status:** Complete
- **Issue key:** PG-6
- **Subtask:** 1 of 4
- **Sources:** [parent spec](./spec.md)

## Problem

Subsequent retrieval upgrades (search_nodes index-first, list_branch index mode, traverse_graph scoring, session_start token accounting) all depend on two primitives that do not yet exist:

1. A lightweight, in-process, cached graph index that exposes compact metadata entries (id, branch, type, category, domain, scope(s), subject, topic, aliases, hypothesis, dates, links, snippet, file metadata, body token estimate) without decoding full bodies.
2. A pure Kotlin deterministic token estimator usable from `core/domain` so every MCP response can report `estimated_tokens.{response_total,metadata_tokens,body_tokens,pruned_body_tokens}`.

This subtask delivers both as standalone, well-tested foundation. No MCP tool surfaces change yet; the index and estimator are wired behind repository APIs and a domain helper, ready for subtask 2 to consume.

The current `VaultPolicy` allows `staging/` reads; the index must add an explicit exclusion for `people/` and `staging/sensitive/` so later index-backed tools cannot leak them.

## Acceptance Criteria

Parent ACs primarily owned: **4**, **8**.

Subtask-internal criteria:

1. New repository contract in `core/domain` exposes compact graph index entries, e.g. `GraphIndexRepository` with operations to list indexed entries by branch, fetch by id, and look up by alias/title/path. Returned entries match the minimum fields listed in the parent spec Graph Index Design section (id, branch, type, category, domain, scope, scopes, subject, topic, aliases, hypothesis, date, updated, created, links, link_count, snippet, body_token_estimate, file_size, file_modified_at).
2. `core/data` provides a `PersonalGraph`-prefixed implementation that builds entries lazily from frontmatter + a small body preview (no full body decode). Built entries are cached in-process; cache invalidation keys on `(file_size, file_modified_at)` per file plus branch root mtime, and is invalidated when the repository writes a node (write/delete/move).
3. Index respects `VaultPolicy.isReadAllowed`/`isReadBlocked` and additionally hard-excludes `people/` and `staging/sensitive/` regardless of current `VaultPolicy` defaults. Blocked ids must never appear in returned entries, snippets, links, alias maps, or backlink targets.
4. New domain helper `TokenEstimator` (pure Kotlin, in `core/domain`) provides a deterministic approximation (e.g. characters / N with documented constants). Exposed methods cover estimating a string, a metadata block, a body, and a list of entries. Documented in KDoc as approximate-and-deterministic.
5. DI: `GraphIndexRepository` and `TokenEstimator` are wired through the existing kotlin-inject `@AppScope`/`DataComponent` graph and made available to MCP-server-bound services (no consumers wired yet beyond DI).
6. Tests (kotest funspec, mockk without `relaxed=true`) cover: index correctness on a fixture vault including subject hubs, episodes, states, patterns; alias/title/path lookup; cache hit + invalidation by mtime/size and by repository write; safety rules — `people/` and `staging/sensitive/` never appear under any query path; token estimator determinism and additivity.
7. `./gradlew check` passes via bill-quality-check.

## Non-goals

- Any MCP tool surface change (search_nodes / list_branch / traverse_graph / session_start unchanged this subtask).
- Persistent on-disk index, embeddings, semantic search.
- Cross-process or multi-user index sync.
- Replacing existing `VaultRepository.findNode` / full-body listing.
- Plus all parent non-goals.

## Dependencies

None. This is the foundation subtask and must merge before subtasks 2, 3, 4.

## Validation strategy

bill-quality-check (`./gradlew check`). Tests live alongside the new domain/data classes. No MCP integration tests required in this subtask.

## Files likely touched

- `core/domain/src/main/kotlin/.../repository/GraphIndexRepository.kt` (new)
- `core/domain/src/main/kotlin/.../graph/GraphIndexEntry.kt` (new model)
- `core/domain/src/main/kotlin/.../tokens/TokenEstimator.kt` (new)
- `core/data/src/main/kotlin/.../PersonalGraphGraphIndexRepository.kt` (new)
- `core/data/src/main/kotlin/.../PersonalGraphVaultRepository.kt` (write hooks invalidate index)
- `core/domain/src/main/kotlin/.../layout/VaultPolicy.kt` or a sibling guard (explicit `people/` and `staging/sensitive/` exclusion for index path)
- `core/data/src/main/kotlin/.../di/DataComponent.kt` (provides)
- Test fixtures + tests under `core/domain/src/test` and `core/data/src/test`.

## Handoff prompt

Run `bill-feature-implement` on `.feature-specs/PG-6-complete-mcp-graph-retrieval-optimization/spec_subtask_1_index-and-token-foundation.md`.
