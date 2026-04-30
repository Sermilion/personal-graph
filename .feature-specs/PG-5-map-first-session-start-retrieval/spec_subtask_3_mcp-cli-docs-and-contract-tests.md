# PG-5 Subtask 3 - MCP, CLI, docs, and contract tests

- **Parent spec:** `.feature-specs/PG-5-map-first-session-start-retrieval/spec.md`
- **Issue key:** PG-5
- **Feature:** map-first-session-start-retrieval
- **Status:** Ready
- **Validation strategy:** `bill-quality-check`
- **Depends on:** Subtask 1 - Contract and scoped-state foundation; Subtask 2 - Map-first retrieval engine

## Goal

Expose the map-first retrieval behavior cleanly through MCP and CLI, document the new contract, and complete cross-boundary regression coverage for PG-5.

## Scope

- Update MCP `session_start` formatting to return the new report shape:
  - `loaded_context`
  - `available_map`
  - `suggested_reads`
  - classification
  - skipped branches
  - audit.
- Update MCP tool schemas/descriptions so `session_start` is described as map-first and any explicit full-loading mode/support path is discoverable without making eager loading the default.
- Preserve separate full-body tools (`read_node`, `list_branch`) and document when agents should use them after `suggested_reads`.
- Update CLI `session-start` output to present the compact map and suggested reads instead of old eager `loaded_branches`/`loaded_nodes` expectations.
- Update `docs/session-start-retrieval.md` with:
  - map-first retrieval model
  - default budget behavior
  - active domain classification list
  - scoped state metadata guidance
  - subject hub preference
  - audit semantics
  - explicit full-loading support path.
- Replace old MCP/CLI tests that asserted eager branch loading with contract tests for the new shape.
- Add or update final acceptance coverage across core/data, mcp-server, cli, and docs-facing examples.

## Acceptance Criteria

1. MCP `session_start` JSON exposes `loaded_context`, `available_map`, and `suggested_reads` and does not expose full broad state branch bodies by default.
2. MCP schemas/descriptions accurately describe map-first behavior and the explicit full-loading support path.
3. CLI output clearly distinguishes loaded context, available map, suggested reads, skips, and audit reasons.
4. Docs describe the PG-5 retrieval contract and state-scope guidance without implying vault migration is complete.
5. Tests cover map shape, budget enforcement, domain-specific suggested reads, global preference handling, no eager load of broad state branches, and adapter output shape.
6. Existing full-body follow-up tools remain available and documented.

## Non-goals

- Do not change the retrieval scoring algorithm except to fix integration defects exposed while adapting MCP/CLI.
- Do not migrate the active vault.
- Do not add proactive surfacing, embeddings, cloud sync, auth, or multi-user behavior.
- Do not load `people/`, `staging/`, or `staging/sensitive/` by default.

## Dependency Notes

Requires subtask 2's stable retrieval behavior and subtask 1's contract names. This should be the last PG-5 implementation subtask because it validates the behavior through public adapters and documentation.

## Files Likely Touched

- `mcp-server/src/main/kotlin/com/sermilion/personalgraph/mcp/tools/VaultMcpToolFormatters.kt`
- `mcp-server/src/main/kotlin/com/sermilion/personalgraph/mcp/tools/ToolSchemas.kt`
- `mcp-server/src/main/kotlin/com/sermilion/personalgraph/mcp/tools/VaultMcpToolArgsParsers.kt`
- `mcp-server/src/test/kotlin/com/sermilion/personalgraph/mcp/tools/VaultMcpToolsTest.kt`
- `cli/src/main/kotlin/com/sermilion/personalgraph/cli/command/SessionStartCommand.kt`
- `cli/src/test/kotlin/com/sermilion/personalgraph/cli/command/SessionStartCommandTest.kt`
- `docs/session-start-retrieval.md`
- `core/data/src/test/kotlin/com/sermilion/personalgraph/data/retrieval/PersonalGraphSessionStartRetrievalServiceTest.kt`

## Validation

Run `bill-quality-check` after implementation. This subtask is the final PG-5 contract validation and should leave the repository passing `./gradlew check` through the routed quality-check skill.

## Handoff

Run `bill-feature-implement` on `.feature-specs/PG-5-map-first-session-start-retrieval/spec_subtask_3_mcp-cli-docs-and-contract-tests.md`.
