# PG-6 - Complete MCP graph retrieval optimization

- **Status:** Complete
- **Issue key:** PG-6
- **Feature size:** Large
- **Rollout needed:** false
- **Sources:** SKILL-33 lookup token-overhead analysis on 2026-05-03; PG-5 map-first retrieval; current MCP search/traversal baseline added after the analysis.

## Problem

PG-5 made `session_start` map-first, but follow-up graph navigation can still be wasteful or underspecified:

- Agents may still use `list_branch` as a poor-man's grep because it is the only obvious branch follow-up in `session_start`.
- `list_branch` returns full node bodies only; it has no index/filter mode for cheap branch inspection.
- `search_nodes` and `traverse_graph` now exist as a baseline, but they are runtime scans rather than an explicit graph retrieval plan with token accounting, persistent indexing, or richer edge semantics.
- `session_start` does not yet detect identifier-like queries such as `SKILL-33` and recommend the cheap search/traverse/read path directly.
- Responses do not report approximate token cost, so retrieval optimization is hard to measure.
- Graph traversal edges are currently generic links/backlinks; traversal cannot yet distinguish evidence, background, state, timeline, subject, contradiction, or broad-hub relationships.
- Broad hubs can still dominate traversal unless callers manually constrain depth/branch/node limits.

The system needs a complete retrieval workflow where agents can cheaply find entrypoints, inspect a bounded graph neighborhood, understand why each node was included, and decide which exact bodies to read without dumping unrelated branches.

## Goal

Make MCP retrieval search-first, graph-aware, explainable, and measurable:

```text
session_start -> suggested_actions -> search_nodes/list_branch(index) -> traverse_graph -> read_node
```

The optimized path should make a query like `find all info about SKILL-33` cheap and predictable:

1. `session_start` recognizes the query as identifier-like and suggests `search_nodes`.
2. `search_nodes` finds exact id/title/body matches using an index-first path and compact snippets.
3. `traverse_graph` gathers a bounded, scored subgraph around the best entrypoints.
4. `read_node` loads only selected full bodies.
5. Every retrieval response reports approximate token cost and pruning decisions.

## Acceptance Criteria

1. `session_start` returns `suggested_actions` in addition to `suggested_reads`.
   - Identifier-like queries such as `SKILL-33`, `PR #91`, branch names, issue keys, and canonical node path fragments should suggest `search_nodes`.
   - Domain-classified prompts should suggest branch-constrained searches before branch body reads.
   - The old `suggested_reads` field remains backward compatible.
2. `list_branch` supports an index/filter mode.
   - Default behavior remains full-body branch listing for backward compatibility.
   - New inputs support at least `mode=index`, `filter`, `limit`, `include_links`, and `include_body=false`.
   - Index mode returns ids, type/kind, metadata, summaries/snippets, link counts, and optional direct links, but no full bodies unless explicitly requested.
3. `search_nodes` uses an index-first implementation.
   - Exact id/path/alias/title matches are checked before body text scanning.
   - The implementation avoids decoding or returning full bodies when id/frontmatter/index metadata is enough.
   - Body scanning remains available as a fallback when metadata matches are insufficient.
4. A lightweight graph index exists behind repository APIs.
   - It can be built lazily per request or cached in-process.
   - It respects `VaultPolicy` read allow/block rules.
   - It records node id, path, type, domain/category/scope/scopes, topic/subject/aliases/hypothesis, dates, link targets, first meaningful snippet, and file metadata needed for invalidation.
   - It never indexes or leaks `people/` and does not include `staging/sensitive/` unless a future explicit sensitive-review path adds consent-gated behavior.
5. `traverse_graph` supports scored, bounded graph traversal.
   - Inputs include `query`, `start_ids`, `branches`, `edge_types`, `max_depth`, `max_nodes`, `budget_tokens`, `include_bodies`, and `rank_by`.
   - Defaults are conservative: depth 1, compact nodes, no full bodies, hard node/token caps.
   - Output includes entrypoints, subgraph nodes, edges, pruned nodes, suggested exact reads, and path explanations.
6. Traversal edge labels are semantically useful.
   - At minimum support `link`, `backlink`, `subject_evidence`, `timeline`, `state`, `pattern`, `contradiction`, and `background`.
   - Subject hubs should expose evidence links distinctly from generic body links when the body structure makes that clear.
   - Timeline links should be treated as chronological index links, not duplicate evidence bodies.
7. Traversal ranking prevents broad-hub explosions.
   - Exact id/title/query matches are boosted.
   - Subject hubs and direct evidence are boosted.
   - Broad hubs, high-degree nodes, and unrelated background links are penalized unless explicitly requested.
   - Recency can boost events when the query asks for latest/recent status.
8. Retrieval responses include token accounting.
   - Each relevant MCP response includes `estimated_tokens.response_total`, `metadata_tokens`, `body_tokens`, and `pruned_body_tokens` where applicable.
   - Token counts may use an approximate local estimator if no model tokenizer is available, but the estimator must be deterministic and documented.
9. Tool descriptions and docs teach the optimized workflow.
   - `session_start` docs should say: search/traverse/read before list-branch-full.
   - MCP schema descriptions should make cost and default compactness obvious.
   - README and `docs/session-start-retrieval.md` should include a SKILL-33-style example.
10. Tests cover the complete workflow.
    - Identifier query suggests `search_nodes`.
    - `list_branch(mode=index, filter="SKILL-33")` returns compact matching entries without bodies.
    - `search_nodes("SKILL-33")` finds id matches without loading unrelated branch bodies.
    - `traverse_graph(query="SKILL-33", max_depth=2, budget_tokens=...)` returns a bounded subgraph with path explanations and pruning.
    - Token accounting separates metadata/body/pruned estimates.
    - Read-blocked branches are not indexed, searched, suggested, traversed, or leaked through snippets/links.
    - `./gradlew check` passes.

## Proposed MCP Contracts

### `session_start`

Add:

```json
{
  "suggested_actions": [
    {
      "tool": "search_nodes",
      "args": {
        "query": "SKILL-33",
        "branches": ["domains/work/skill-bill", "state/knowledge"],
        "limit": 20,
        "include_body": false
      },
      "reason": "message contains identifier-like token SKILL-33; search ids and snippets before reading bodies",
      "priority": "high"
    }
  ],
  "estimated_tokens": {
    "response_total": 1200,
    "metadata_tokens": 900,
    "body_tokens": 300
  }
}
```

`suggested_actions` is additive. Existing clients can continue using `suggested_reads`.

### `list_branch`

Extend input:

```json
{
  "branch": "domains/work/skill-bill",
  "mode": "index",
  "filter": "SKILL-33",
  "limit": 50,
  "include_links": true,
  "include_body": false
}
```

Default `mode` is `full` to preserve existing behavior. `mode=index` returns:

```json
{
  "status": "ok",
  "mode": "index",
  "nodes": [
    {
      "id": "domains/work/skill-bill/subjects/skill-bill-skill-33-pr-91-and-pr",
      "type": "subject",
      "domain": "work/skill-bill",
      "subject": "SKILL-33 PR #91 and PR #92",
      "snippet": "Canonical subject hub for Skill Bill SKILL-33 PR #91 and PR #92 merged.",
      "links": ["domains/work/skill-bill/events/skill-33-pr91-pr92-merged-2026-05-02"],
      "match_fields": ["id", "subject", "body"],
      "score": 173
    }
  ],
  "estimated_tokens": {
    "response_total": 700,
    "metadata_tokens": 700,
    "body_tokens": 0,
    "pruned_body_tokens": 5200
  }
}
```

### `search_nodes`

Evolve current baseline input:

```json
{
  "query": "SKILL-33",
  "branches": ["domains/work/skill-bill", "state/knowledge"],
  "limit": 20,
  "include_body": false,
  "search_fields": ["id", "metadata", "body"],
  "body_fallback": true
}
```

Output keeps current compact result shape and adds index/source and token fields:

```json
{
  "status": "ok",
  "nodes": [
    {
      "id": "state/knowledge/skill-bill-skill-33-opencode-native-subagent-planning",
      "type": "state",
      "category": "knowledge",
      "score": 141,
      "match_fields": ["id", "body"],
      "snippet": "For Skill Bill SKILL-33 OpenCode native subagent planning...",
      "links": []
    }
  ],
  "search_plan": {
    "metadata_index_used": true,
    "body_fallback_used": true,
    "branches_searched": ["domains/work/skill-bill", "state/knowledge"]
  },
  "estimated_tokens": {
    "response_total": 900,
    "metadata_tokens": 900,
    "body_tokens": 0
  }
}
```

### `traverse_graph`

Evolve current baseline input:

```json
{
  "query": "SKILL-33",
  "start_ids": [],
  "branches": ["domains/work/skill-bill", "state/knowledge"],
  "edge_types": ["link", "backlink", "subject_evidence", "state", "timeline"],
  "max_depth": 2,
  "max_nodes": 30,
  "budget_tokens": 4000,
  "rank_by": ["exact_id_match", "edge_weight", "recency", "branch_relevance"],
  "include_bodies": false
}
```

Output:

```json
{
  "status": "ok",
  "entrypoints": [
    "domains/work/skill-bill/subjects/skill-bill-skill-33-pr-91-and-pr"
  ],
  "nodes": [
    {
      "id": "domains/work/skill-bill/events/skill-33-pr91-pr92-merged-2026-05-02",
      "type": "episode",
      "distance": 1,
      "score": 91,
      "reason": "linked evidence from exact SKILL-33 subject hub",
      "snippet": "Merged the two SKILL-33 pull requests in skill-bill..."
    }
  ],
  "edges": [
    {
      "from": "domains/work/skill-bill/subjects/skill-bill-skill-33-pr-91-and-pr",
      "to": "domains/work/skill-bill/events/skill-33-pr91-pr92-merged-2026-05-02",
      "type": "subject_evidence",
      "weight": 90,
      "reason": "subject Evidence section links this event"
    }
  ],
  "suggested_reads": [
    {
      "id": "domains/work/skill-bill/subjects/skill-bill-skill-33-pr-91-and-pr",
      "reason": "best exact subject hub; read first for synthesis",
      "priority": "high"
    }
  ],
  "pruned": [
    {
      "id": "domains/work/skill-bill/subjects/skill-32-technical-stabilization-plan",
      "reason": "high-degree background hub; excluded by budget_tokens"
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

## Graph Index Design

Add an internal index model in `core/domain` or `core/data` behind repository interfaces. The first version can be in-process and lazily rebuilt; it does not need a persisted database.

Minimum index entry:

- `id`
- `branch`
- `type`
- `category`
- `domain`
- `scope`
- `scopes`
- `subject`
- `topic`
- `aliases`
- `hypothesis`
- `date`
- `updated`
- `created`
- `links`
- `link_count`
- `snippet`
- `body_token_estimate`
- `file_size`
- `file_modified_at`

Index safety rules:

- Apply `VaultPolicy.isReadAllowed` and `isReadBlocked` before indexing.
- Do not index `people/`.
- Do not index `staging/sensitive/` for general retrieval.
- Do not return blocked ids through snippets, links, backlinks, suggested actions, or pruning metadata.
- Treat symlink escapes the same way existing reads do.

Invalidation:

- Initial implementation may rebuild per request if simple enough and tests stay fast.
- If cached, invalidate by file size + modified time and branch roots.
- Writes through repository should invalidate affected ids/branches.

## Ranking Rules

Search scoring:

- Exact full id/path match: highest.
- Leaf id/slug match.
- Subject/topic/alias/hypothesis match.
- Domain/branch relevance.
- Body mention.
- Recency boost when query includes recent/latest/today/merged/opened/status.

Traversal scoring:

- Entry node exact match carries high score.
- `subject_evidence` and direct `link` from exact subject hubs are high weight.
- `state` and `knowledge` nodes linked to entrypoints are medium-high.
- `timeline` edges are low weight unless chronology is requested.
- `background` edges are low weight.
- High-degree hubs get a penalty unless exact-matched.
- Nodes outside requested branches get a penalty unless directly linked evidence.

## Implementation Plan

1. Add graph index contracts and DTOs.
   - Repository APIs should expose compact indexed entries separately from full `VaultNode` bodies.
   - Keep full body reads behind `findNode`, `listNodesInBranch(mode=full)`, or explicit `include_body`.
2. Upgrade `search_nodes`.
   - Use index metadata first.
   - Use body fallback only when needed.
   - Return `search_plan` and `estimated_tokens`.
3. Upgrade `list_branch`.
   - Add parser/schema support for `mode`, `filter`, `limit`, `include_links`, `include_body`.
   - Preserve current full mode as default.
4. Upgrade `traverse_graph`.
   - Use graph index for entrypoints and adjacency.
   - Add edge labels, scores, budget pruning, and suggested reads.
5. Upgrade `session_start`.
   - Add identifier detection and `suggested_actions`.
   - Branch suggestions should prefer `search_nodes` or `list_branch(mode=index)` before `list_branch(mode=full)`.
6. Add token accounting.
   - Shared estimator in a common/domain-safe location.
   - Include token fields in `session_start`, `search_nodes`, `list_branch(index)`, and `traverse_graph`.
7. Update docs and examples.
   - Include SKILL-33 lookup example and expected tool sequence.
8. Run and fix full validation.
   - `./gradlew check`.

## Migration / Compatibility

- Existing `read_node`, `list_branch` default full mode, and `session_start.suggested_reads` remain compatible.
- New fields are additive.
- `search_nodes` and `traverse_graph` current baseline shapes may gain fields, but existing `status` and `nodes` fields remain.
- MCP clients should gradually prefer `suggested_actions` over manually interpreting branch maps.

## Non-goals

- Embeddings or semantic vector search.
- Cloud indexing, multi-user sync, or background daemon indexing.
- Reading `people/` or `staging/sensitive/` in normal retrieval.
- Perfect model-token parity. Approximate deterministic token accounting is acceptable for PG-6.
- Full vault hygiene migration or broken-link cleanup.
- Replacing `read_node`; exact full body reads remain the final step.
