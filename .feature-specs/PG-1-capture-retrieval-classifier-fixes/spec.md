# PG-1: Capture, Retrieval, and Classifier Fixes

Status: Complete

## Sources

Conversation plan dated 2026-04-25 between Braian and Claude during dogfooding the personal-graph MCP server.

## Background

Six gaps surfaced in the capture/retrieval surface while dogfooding the MCP server. This spec covers the in-repo fixes. The auto-memory ↔ MCP routing gap is out of scope (different repo, different fix mechanism).

## Acceptance criteria

1. `session_start` always loads `state/preferences/` and `state/roles/` regardless of classification; loads `state/knowledge/` only when classification is `General`.
2. Classifier compound-word matching no longer treats `-` as a word boundary, so `"personal-graph"` does not match the term `personal`.
3. Generic, leakage-prone terms pruned from term lists: `pr`, `code`, `project`, `review`, `meeting` (work) and `personal`, `home` (personal).
4. Classifier picks the domain with the highest match count instead of first-non-empty; deterministic tiebreak when counts are equal.
5. `CREATIVE_TERMS` expanded to cover music/audio/visual-art vocabulary (song, audio, recording, mixdown, bass, drums, guitar, track, arrangement, mp3, studio, compose, paint, draw, sketch, band, instrument, etc.).
6. `write_state` id normalization: accept canonical plural prefix (`state/roles/x`) as-is; accept bare leaf names (`x`) and route by `category`; reject singular-prefix ids (`state/role/x`) with an error naming the canonical alternative.
7. JSON schema descriptions surface non-obvious validation rules across all MCP tool schemas — `date` documents ISO-8601 instant format with example; `id` documents slug normalization rules; other fields reviewed.
8. Tests cover new classifier precedence, compound-word boundary, expanded creative routing, id normalization accept/reject cases, and schema descriptions where they encode rules.

## Non-goals

- Embedding-based classification.
- Migrating existing on-disk nodes whose filenames carry the old slugified prefix.
- Auto-memory ↔ MCP routing (separate work item).
- New MCP tools, new schema fields, or changes to consolidation/staging behavior.
- Server-side normalization of `YYYY-MM-DD` date-only inputs to instants.

## Resolved open questions

- Date format: strict ISO-8601 instant + documented.
- ID error message: yes, suggest the canonical plural-prefix alternative.

## Affected modules

- `core/data` — `PersonalGraphSessionStartRetrievalService.kt`, `PersonalGraphVaultCaptureService.kt`
- `mcp-server` — `ToolSchemas.kt`, `ToolSchemaBuilder.kt`, possibly `VaultMcpToolArgsParsers.kt` for date parsing

## Identified root causes (from initial reading by orchestrator)

- session_start `state/` skip: `branchPlanFor()` only loads `DURABLE_STATE_BRANCHES` for `General` classification.
- Compound-word leak: `containsTerm` regex `(?<![a-z0-9])$escaped(?![a-z0-9])` treats `-` as word boundary; `"personal-graph"` matches `personal`.
- Anemic creative terms: `CREATIVE_TERMS` has only 6 entries.
- Arbitrary precedence: `classify()` picks first non-empty match in fixed order (work > personal > creative > general).
- ID mangling: `buildStateTargetId` only recognizes plural prefixes; falls back to slugifying the whole id including `/`.
