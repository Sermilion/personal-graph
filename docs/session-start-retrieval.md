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
- `classification` — the selected active domain and matched terms.
- `skipped_branches` — branches intentionally not loaded.
- `audit` / `audit_entries` — reasons for classification, skips, suggestions,
  and any full-loading decisions.

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
follow-up is justified.

## Non-MCP Prompt Snippet

```text
Before answering the user, run:

personal-graph-cli session-start --vault <vault> "<first substantive user message>"

Use Loaded context as the only automatic full-body context. Review Available map
and Suggested reads, then read exact nodes only when needed. Do not read people/,
staging/, or staging/sensitive/. Do not read emotional-states/ unless the report
says emotional_context=true.
```
