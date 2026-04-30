# PG-5 Subtask 2 - Map-first retrieval engine

- **Parent spec:** `.feature-specs/PG-5-map-first-session-start-retrieval/spec.md`
- **Issue key:** PG-5
- **Feature:** map-first-session-start-retrieval
- **Status:** Ready
- **Validation strategy:** `bill-quality-check`
- **Depends on:** Subtask 1 - Contract and scoped-state foundation

## Goal

Replace eager session-start branch loading with deterministic map-first retrieval in the core data service. The default `session_start` result should load only bounded full-body orientation context, expose a compact navigable graph map, and rank suggested nodes for follow-up reads.

## Scope

- Update `PersonalGraphSessionStartRetrievalService` to use the new map-first report contract from subtask 1.
- Default behavior:
  - load `Braian.md` as root orientation when available
  - do not load full bodies for broad `state/preferences`, `state/roles`, `state/knowledge`, or domain subtrees
  - build `available_map` entries for safe navigable nodes
  - produce ranked `suggested_reads`
  - preserve explicit skips for `people/`, `staging/`, `staging/sensitive/`, and emotional state gating.
- Generate compact map entries with node id, node kind, domain/category, scope, updated/date, summary/excerpt, aliases or terms when available, and cheap link counts/direct links.
- Derive summaries deterministically:
  - prefer explicit frontmatter summary if available later
  - for subject hubs, prefer the `## Summary` section
  - for episodes, use topic plus first evidence/body line
  - otherwise use the first meaningful body line.
- Enforce an explicit default full-body budget targeting root orientation plus map under roughly 1,500 words for the active vault. The budget should bound loaded bodies, not merely formatter output.
- Implement deterministic classification for all active domains:
  - `work/capmo`
  - `work/skill-bill`
  - `work/readian`
  - `work/context-app`
  - `creative/music`
  - `personal`
  - `general`.
- Implement scoped-state suggestion rules:
  - global/unscoped preferences remain eligible across domains
  - Capmo/Skill Bill/Readian/Context scoped state is suggested only for relevant tasks
  - existing unscoped state remains supported.
- Prefer subject hubs over raw events in `suggested_reads`; suggest events when recent evidence is likely needed.
- Add audit entries that explain why nodes were suggested and why full broad branches were not loaded.
- Implement the explicit full-loading mode or support path introduced in subtask 1, reusing existing repository `read_node`/`list_branch` behavior where possible and keeping default `session_start` map-first.

## Acceptance Criteria

1. Default session-start retrieval no longer returns full bodies for broad state branches or classified domain subtrees.
2. Default reports contain compact `available_map` entries with the required map fields.
3. Default reports distinguish `loaded_context`, `available_map`, and `suggested_reads`.
4. Default full-body loading is bounded by an explicit budget targeting root orientation plus map under roughly 1,500 words for the current vault shape.
5. Full branch loading remains possible only through the explicit opt-in mode/support path, not the default behavior.
6. Classification supports all active vault domains listed above.
7. Scoped state is suggested only when global or relevant to the classified domain/tool/project.
8. Subject hubs rank ahead of raw event nodes except when recent event evidence is specifically useful.
9. Audit output explains suggestion reasons and map-first branch skip reasons.

## Non-goals

- Do not update MCP JSON formatting, CLI presentation, or docs beyond compile-required changes; subtask 3 owns adapter/docs polish.
- Do not migrate existing vault nodes.
- Do not add embeddings, cloud sync, auth, multi-user behavior, or proactive surfacing.
- Do not load `people/`, `staging/`, or `staging/sensitive/` by default.

## Dependency Notes

Requires subtask 1's map-first DTOs, retrieval mode, active domain values, and scoped state metadata. Subtask 3 depends on this service behavior to expose stable MCP/CLI output and integration tests.

## Files Likely Touched

- `core/data/src/main/kotlin/com/sermilion/personalgraph/data/retrieval/PersonalGraphSessionStartRetrievalService.kt`
- `core/domain/src/main/kotlin/com/sermilion/personalgraph/domain/retrieval/SessionStartRetrievalService.kt`
- `core/domain/src/main/kotlin/com/sermilion/personalgraph/domain/repository/VaultRepository.kt`
- `core/data/src/main/kotlin/com/sermilion/personalgraph/data/repository/PersonalGraphVaultRepository.kt`
- `core/testing/src/main/kotlin/com/sermilion/personalgraph/testing/VaultNodeFixtures.kt`
- `core/data/src/test/kotlin/com/sermilion/personalgraph/data/retrieval/PersonalGraphSessionStartRetrievalServiceTest.kt`

## Validation

Run `bill-quality-check` after implementation. Targeted retrieval tests should cover map shape, budget enforcement, domain-specific suggestions, global preference handling, subject-hub preference, audit reasons, explicit full-load opt-in, and no eager loading of broad state branches.

## Handoff

Run `bill-feature-implement` on `.feature-specs/PG-5-map-first-session-start-retrieval/spec_subtask_2_map-first-retrieval-engine.md`.
