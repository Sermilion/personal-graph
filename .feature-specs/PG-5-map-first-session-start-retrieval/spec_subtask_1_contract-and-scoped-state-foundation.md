# PG-5 Subtask 1 - Contract and scoped-state foundation

- **Parent spec:** `.feature-specs/PG-5-map-first-session-start-retrieval/spec.md`
- **Issue key:** PG-5
- **Feature:** map-first-session-start-retrieval
- **Status:** Ready
- **Validation strategy:** `bill-quality-check`

## Goal

Prepare the domain/data contract needed by map-first retrieval without replacing the retrieval algorithm yet. This subtask should make scoped state and the new session-start result shape representable end to end, while preserving compatibility with existing vault nodes.

## Scope

- Extend retrieval domain contracts in `core/domain` so `SessionStartRetrievalReport` can represent `loaded_context`, `available_map`, and `suggested_reads`.
- Add an explicit retrieval mode or equivalent request field that lets callers choose default map-first behavior versus full branch loading support.
- Add active vault domain values for `work/capmo`, `work/skill-bill`, `work/readian`, `work/context-app`, `creative/music`, `personal`, and `general`.
- Add scoped state metadata support to `StateNode` and state frontmatter mapping:
  - support a singular `scope`
  - support plural `scopes`
  - preserve existing nodes with neither field as global/unscoped state.
- Add capture-layer plumbing for new scoped state writes where appropriate, including MCP argument parsing/schema support if the current write path needs it to persist scope metadata.
- Keep existing retrieval behavior working until subtask 2 replaces it; temporary adapters may populate both old and new report fields only if needed to keep compilation and tests green.

## Acceptance Criteria

1. Domain models compile with a map-first session-start contract containing loaded full-body context, compact map entries, suggested reads, skipped branches, and audit entries.
2. The retrieval request contract includes explicit support for default map-first retrieval and a full-loading mode or equivalent explicit opt-in.
3. `RetrievalDomain` or its replacement supports all active vault domains: `work/capmo`, `work/skill-bill`, `work/readian`, `work/context-app`, `creative/music`, `personal`, and `general`.
4. `StateNode` can carry global/unscoped state, a single scope, or multiple scopes without breaking existing state nodes.
5. State frontmatter round-trips existing nodes without `scope` or `scopes`, and round-trips new nodes containing `scope` and/or `scopes`.
6. Write-state/capture plumbing can persist scoped state metadata for new captures without requiring a vault migration.
7. Existing tests that do not assert the old eager retrieval behavior still pass, or are updated only for contract compilation.

## Non-goals

- Do not implement map scoring, budget enforcement, or suggested-read ranking in this subtask.
- Do not migrate the existing vault.
- Do not change default `session_start` behavior beyond what is necessary to compile against the new contract.
- Do not introduce embeddings or semantic search.
- Do not load `people/`, `staging/`, or `staging/sensitive/` by default.

## Dependency Notes

This is the foundation for the rest of PG-5. Subtask 2 depends on these domain DTOs, retrieval mode fields, domain values, and state scope metadata being present and tested.

## Files Likely Touched

- `core/domain/src/main/kotlin/com/sermilion/personalgraph/domain/retrieval/SessionStartRetrievalService.kt`
- `core/domain/src/main/kotlin/com/sermilion/personalgraph/domain/model/VaultNode.kt`
- `core/domain/src/main/kotlin/com/sermilion/personalgraph/domain/capture/VaultCaptureService.kt`
- `core/data/src/main/kotlin/com/sermilion/personalgraph/data/model/StateNodeFrontmatterDataModel.kt`
- `core/data/src/main/kotlin/com/sermilion/personalgraph/data/mapper/VaultNodeMappers.kt`
- `core/data/src/main/kotlin/com/sermilion/personalgraph/data/capture/PersonalGraphVaultCaptureService.kt`
- `mcp-server/src/main/kotlin/com/sermilion/personalgraph/mcp/tools/ToolSchemas.kt`
- `mcp-server/src/main/kotlin/com/sermilion/personalgraph/mcp/tools/VaultMcpToolArgsParsers.kt`
- `core/data/src/test/kotlin/com/sermilion/personalgraph/data/codec/MarkdownFrontmatterCodecRoundTripTest.kt`
- `core/data/src/test/kotlin/com/sermilion/personalgraph/data/mapper/VaultNodeMappersTest.kt`
- `core/data/src/test/kotlin/com/sermilion/personalgraph/data/capture/PersonalGraphVaultCaptureServiceTest.kt`

## Validation

Run `bill-quality-check` after implementation. At minimum, targeted tests should cover state scope frontmatter round-trip and scoped write-state persistence.

## Handoff

Run `bill-feature-implement` on `.feature-specs/PG-5-map-first-session-start-retrieval/spec_subtask_1_contract-and-scoped-state-foundation.md`.
