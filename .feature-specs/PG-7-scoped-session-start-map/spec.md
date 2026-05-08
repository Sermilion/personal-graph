# PG-7 scoped-session-start-map

Status: Complete

## Sources

- Pre-planning briefing provided for feature implementation on 2026-05-08.

## Acceptance Criteria

1. session_start for a classified project like work/skill-bill should avoid eager full branch/body loading.
2. The default map should be project-scoped and top-K/summary-first, not a full project index dump.
3. Global state should be narrowed to domain-relevant scoped state plus essential global preferences, instead of all preferences/roles.
4. Follow-up actions should still expose exact search_nodes, list_branch(mode=index), and read_node paths.
5. Token accounting and tests should prove lower startup cost while preserving relevant suggested reads.

## Non-Goals

- No full graph search rewrite.
- No persistent on-disk index format change.
- No UI/MCP protocol-breaking response shape change.

## Consolidated Spec

Optimize personal-graph session_start so classified project sessions use scoped, top-K map-first retrieval instead of loading broad/global branch maps. Current measurement against the configured vault for message "working on skill-bill feature implementation": map-first reports 9,774 tokens, full-loading reports 38,127 tokens. The project-only list_branch(index) for domains/work/skill-bill still reports 13,997 metadata tokens when uncapped with links, so the desired behavior is project-scoped plus top-K/summary-first rather than dumping the whole project index.
