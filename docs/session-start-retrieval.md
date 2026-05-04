# Session-Start Retrieval

PG-5 makes session-start retrieval map-first. The default call or CLI command
loads only bounded orientation context, returns a compact graph map, and names
exact nodes or branches that an agent may read next.

## Contract

`session_start` returns:

- `loaded_context` — full bodies actually loaded into the prompt. By default
  this is `Braian.md` only, bounded by an explicit word budget.
- `available_map` — compact navigation entries for safe branches and nodes.
  Entries include id, kind/type, category or domain when known, scope metadata,
  dates, aliases, link counts, and a short summary.
- `suggested_reads` — ranked follow-up ids. Agents should call `read_node` for
  exact node bodies or `list_branch` only when a full branch body load is
  explicitly needed.
- `suggested_actions` — cheaper follow-up tool calls. Identifier-like prompts
  suggest `search_nodes`; broader prompts suggest branch-constrained
  `search_nodes` and `list_branch(mode=index)` before any full-body read.
- `estimated_tokens` — approximate token accounting for the retrieval response.
- `classification` — the selected active domain and matched terms.
- `skipped_branches` — branches intentionally not loaded.
- `audit` / `audit_entries` — reasons for classification, skips, suggestions,
  and any full-loading decisions.

## Search-First Flow

The optimized path is:

```text
session_start -> suggested_actions -> search_nodes / list_branch(mode=index) -> traverse_graph -> read_node
```

Use `suggested_actions` first. `search_nodes` should come before any full-body
branch listing, and `list_branch(mode=index)` should be used when you only need
compact metadata for a branch.

## SKILL-33 Example

For a query like `SKILL-33 PR #91 and PR #92`, `session_start` should surface a
`search_nodes` action that targets the identifier first:

```json
{
  "tool": "search_nodes",
  "args": {
    "query": "SKILL-33",
    "branches": ["state", "domains", "patterns", "emotional-states", "timeline", "staging/observations", "outdated"],
    "limit": 20,
    "search_fields": ["id", "metadata"],
    "body_fallback": false,
    "include_body": false
  },
  "reason": "identifier-like token SKILL-33 detected; search ids and metadata before reading bodies",
  "priority": "high"
}
```

Suggested follow-up flow:

1. `search_nodes` to find the exact hub or node ids.
2. `traverse_graph` to inspect a bounded neighborhood when the query fans out.
3. `read_node` for only the final bodies you actually need.

Typical token accounting should show most cost in metadata for the initial
response and body tokens only when the loaded context includes full node bodies.

## `traverse_graph` Wire Format

Use `traverse_graph` after the index pass when you want a bounded, explainable
subgraph instead of a raw branch dump.

Request shape:

```json
{
  "query": "SKILL-33",
  "start_ids": ["domains/work/skill-bill/subjects/skill-bill-skill-33-pr-91-and-pr"],
  "branches": ["domains/work/skill-bill", "state/knowledge"],
  "edge_types": ["link", "backlink", "subject_evidence", "timeline", "state"],
  "max_depth": 2,
  "max_nodes": 30,
  "budget_tokens": 4000,
  "include_bodies": false,
  "rank_by": ["exact_id_match", "edge_weight", "recency", "branch_relevance"]
}
```

Response shape:

```json
{
  "status": "ok",
  "entrypoints": [
    {
      "id": "domains/work/skill-bill/subjects/skill-bill-skill-33-pr-91-and-pr",
      "reason": "query",
      "score": 100
    }
  ],
  "nodes": [
    {
      "id": "domains/work/skill-bill/events/skill-33-pr91-pr92-merged-2026-05-02",
      "type": "episode",
      "distance": 1,
      "score": 91,
      "reason": "subject_evidence",
      "snippet": "Merged the two SKILL-33 pull requests...",
      "match_fields": ["subject_evidence"]
    }
  ],
  "edges": [
    {
      "from": "domains/work/skill-bill/subjects/skill-bill-skill-33-pr-91-and-pr",
      "to": "domains/work/skill-bill/events/skill-33-pr91-pr92-merged-2026-05-02",
      "type": "subject_evidence",
      "label": "subject_evidence",
      "weight": 90,
      "reason": "subject evidence link"
    }
  ],
  "pruned": [
    {
      "id": "domains/work/skill-bill/subjects/skill-32-technical-stabilization-plan",
      "reason": "budget_tokens",
      "score": 12,
      "estimated_tokens": 16000
    }
  ],
  "suggested_reads": [
    {
      "id": "domains/work/skill-bill/subjects/skill-bill-skill-33-pr-91-and-pr",
      "reason": "pruned by budget_tokens",
      "priority": "high"
    }
  ],
  "estimated_tokens": {
    "response_total": 2600,
    "metadata_tokens": 2600,
    "body_tokens": 0,
    "pruned_body_tokens": 16000
  }
}
```

Blocked ids and branches such as `people/` and `staging/sensitive/` are
filtered before formatting, so they do not appear in entrypoints, nodes,
edges, pruned candidates, or suggested reads.

Default retrieval does not return full bodies for broad `state/` branches or
classified domain subtrees. `retrieval_mode=full-loading` is the explicit
compatibility path for callers that intentionally want node bodies in
`loaded_context`; otherwise use `read_node` / `list_branch` after reviewing the
map and suggestions.

## Active Domains

The classifier currently routes to:

- `work/capmo`
- `work/skill-bill`
- `work/readian`
- `work/context-app`
- `creative/music`
- `personal`
- `general`

`general` loads durable global state map entries. Domain-specific prompts map
the matching domain branch and eligible scoped state.

## State Scope Guidance

State nodes may be global or scoped:

```yaml
scope: "work/capmo"
```

or:

```yaml
scopes:
  - "work/capmo"
  - "work/readian"
```

Global/unscoped preferences remain visible across domains. Scoped state is
eligible when the classified domain matches `scope` or one of `scopes`.
Existing vault nodes do not need to be migrated before PG-5 works; migration and
cleanup are separate hygiene tasks.

## Default Skips

Session-start retrieval skips `people/`, skips `staging/` including
`staging/sensitive/`, and excludes `emotional-states/` unless the first message
explicitly asks about emotional context or self-reflection.

## CLI Wrapper

Non-MCP tools can call:

```bash
personal-graph-cli session-start --vault /absolute/path/to/vault "first user message"
```

Use the `Loaded context` section as the initial preamble. Use `Available map`
and `Suggested reads` to decide which precise `read_node` or `list_branch`
follow-up is justified. Prefer `Suggested actions` first when the report points
at `search_nodes` or `list_branch(mode=index)`.

## Non-MCP Prompt Snippet

```text
Before answering the user, run:

personal-graph-cli session-start --vault <vault> "<first substantive user message>"

Use Loaded context as the only automatic full-body context. Review Available map
and Suggested reads, then follow Suggested actions before any full-body branch
read. Do not read people/, staging/, or staging/sensitive/. Do not read
emotional-states/ unless the report says emotional_context=true.
```
