# PG-5 - Map-first session-start retrieval

- **Status:** In Progress
- **Issue key:** PG-5
- **Feature size:** Medium
- **Rollout needed:** false
- **Sources:** Vault audit on 2026-04-30; Braian direction that 12k+ word session-start loads are unacceptable; existing GRP-3 session-start retrieval implementation.

## Problem

Current `session_start` eagerly loads full durable branches:

- `state/preferences/` and `state/roles/` are always loaded.
- `state/knowledge/` is loaded for `General` classification.
- A classified domain branch is loaded as a subtree.

In the active vault this can produce a 12k+ word preamble before the agent has done any task-specific navigation. That is the wrong default. Session start should orient the agent, not spend the context budget.

The retrieval model should be closer to codebase navigation: load a compact map first, then read exact files/nodes on demand.

## Goal

Change session-start retrieval from eager content loading to map-first retrieval:

1. Load a tiny root orientation.
2. Load or generate a compact graph map/index.
3. Return suggested node ids to inspect.
4. Let the agent call `read_node` or `list_branch` for precise follow-up context.

The default session-start payload should be small enough to use on every meaningful task without hesitation.

## Acceptance Criteria

1. `session_start` no longer returns full bodies for broad state branches by default.
2. `session_start` returns a compact graph map with node ids, node kind, domain/category, updated date, and one-line summaries or excerpts.
3. Retrieval output distinguishes:
   - `loaded_context`: full body content actually loaded into the session.
   - `available_map`: compact node inventory available for navigation.
   - `suggested_reads`: ranked node ids the agent should consider loading next.
4. The default full-body payload is bounded by an explicit budget. Target: root orientation plus map should stay under roughly 1,500 words for the current vault.
5. Full branch loading remains possible only via an explicit mode or separate tool, not the default `session_start` behavior.
6. Domain classification supports the active vault domains, at minimum:
   - `work/capmo`
   - `work/skill-bill`
   - `work/readian`
   - `work/context-app`
   - `creative/music`
   - `personal`
   - `general`
7. State nodes can be scoped by domain/tool/project so global preferences remain global, while Capmo/Skill Bill/Readian/Context state is only suggested for relevant tasks.
8. Subject hubs are preferred over raw event nodes in `suggested_reads`; events are suggested when recent evidence is likely needed.
9. The audit output explains why nodes were suggested and why full bodies were not loaded.
10. Tests cover map shape, budget enforcement, domain-specific suggested reads, global preference handling, and no eager load of broad state branches.

## Proposed Retrieval Contract

`session_start` should return a structure conceptually like:

```json
{
  "classification": {
    "domain": "work/capmo",
    "matched_terms": ["capmo", "attendance"]
  },
  "loaded_context": [
    {
      "id": "Braian.md",
      "reason": "root orientation",
      "body": "..."
    }
  ],
  "available_map": [
    {
      "id": "state/preferences/vm-state-vs-ui-state-boundary",
      "type": "state",
      "scope": "work/capmo",
      "summary": "UiState is only for data rendered by UI; VM internals use private StateFlow.",
      "updated": "2026-04-30"
    }
  ],
  "suggested_reads": [
    {
      "id": "state/preferences/vm-state-vs-ui-state-boundary",
      "reason": "matched UI/ViewModel state terms and Capmo Android scope",
      "priority": "high"
    }
  ],
  "skipped": [
    {
      "branch": "state/preferences",
      "reason": "map-first retrieval skips eager broad state loading"
    }
  ]
}
```

Exact DTO names can differ, but the behavior must preserve this separation: small loaded context, navigable map, and explicit suggested reads.

## Graph Map

The map may be generated at runtime or materialized in the vault. The implementation should choose the simplest reliable option.

Minimum map fields:

- `id`
- `type`
- `category` or `domain`
- `scope` when available
- `updated` or `date`
- `summary`
- `aliases` or terms when available
- link counts or direct links when cheap

Map summaries should come from a stable source:

- Prefer explicit frontmatter `summary` if added.
- Otherwise derive from the first meaningful body line.
- For subject hubs, prefer the `## Summary` section.
- For episodes, derive from topic plus first evidence line.

## Vault Structure Changes

Add or support scoped state metadata so durable state can be routed without loading every state file:

```yaml
scope: "work/capmo"
```

or:

```yaml
scopes:
  - "work/capmo"
  - "android"
```

This is not required for every existing node in the first implementation, but new captures should have a path toward scoped state.

Recommended hygiene rules:

- Global `state/preferences` should only contain cross-domain user preferences.
- Domain-specific durable facts should be reachable from domain subject hubs.
- Subject hub slug should be short and stable; long topic text belongs in `topic`, `aliases`, or body.
- `write_episode` should eventually accept an explicit `subject_key` to avoid duplicate long hubs.

## Implementation Notes

Likely affected areas:

- `PersonalGraphSessionStartRetrievalService`
- `SessionStartRetrievalService` domain DTOs
- MCP formatter/parser for `session_start`
- CLI `session-start` output
- docs/session-start-retrieval.md
- tests in `core/data`, `mcp-server`, and `cli`

The first implementation does not need embeddings or semantic search. Start with deterministic classification, scoped map entries, and simple scoring.

Suggested scoring inputs:

- domain match
- exact term match in id/topic/summary/aliases
- recency for events
- node type priority: global root > subject hub > scoped state > recent episode > pattern
- direct links from already suggested nodes

## Migration / Cleanup

Useful vault cleanup after this feature lands:

- Replace the TODO in `Braian.md` with a compact orientation and domain map.
- Merge duplicate long subject hubs, especially the `2026-05-04` Capmo release hubs.
- Move Skill Bill memories currently under `domains/work/capmo` into `domains/work/skill-bill`.
- Consolidate `domains/capmo-engineering` into `domains/work/capmo`.
- Pick one Context domain, likely `domains/work/context-app`, and migrate `domains/context-app`, `domains/coding/context-app`, and generic Context engineering notes into it.
- Add a vault-doctor check for broken links, duplicate hubs, overlong subject slugs, and domain/path mismatch.

## Non-goals

- Embedding-based retrieval.
- Cloud sync, auth, or multi-user behavior.
- Loading `people/` by default.
- Loading `staging/` or `staging/sensitive/` by default.
- Solving proactive surfacing.
- Fully migrating the existing vault in the same change.
