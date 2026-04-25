# GRP-1 — Stage 1: Vault + Capture (MVP)

- **Status:** Complete
- **Issue key:** GRP-1
- **Stage:** 1 of 4
- **Source of truth (full vision/roadmap):** [`/.feature-spec/spec.md`](../../.feature-spec/spec.md)

This per-stage spec is a focused extract of the master spec for Stage 1 only. The master spec remains the canonical reference for the long-term vision; everything below is scoped to what Stage 1 must deliver. Sections that are merely referenced (consolidation, session-start retrieval, proactive surfacing, cross-AI wrappers for non-MCP tools) are deferred to later stages and explicitly out of scope here.

## Stage 1 deliverables

From the master spec, the Stage 1 deliverables are:

- Directory structure per the vault layout below, created on demand by an init command.
- Root `Braian.md` seeded with a short orientation. Idempotent — never overwritten if it already exists.
- Local MCP server (stdio transport) exposing scoped read/write on the vault.
- Tier 1 capture: agents write Tier 1 observations and episode nodes per protocol.
- Sensitivity flagging routes content to `staging/sensitive/` and exposes a pending list for batch disposition.
- No retrieval beyond what the agent does naturally with file access (Stage 3 work).

Exit criterion (from master spec): after two weeks of normal use, the vault contains >=30 non-trivial observations with correct typing, >=5 episodes per domain in active use, and no observed drift from the schema constraints.

## Vault layout (verbatim from master spec)

```
personal-graph/
  Braian.md                        # root orienting note; always loaded first

  state/                           # durable facts, preferences, roles
    preferences/
    roles/
    knowledge/                     # skill-level entries, evidence-based only

  domains/
    work/
      capmo/
        events/                    # episode nodes for current role
        notes/                     # state scoped to this role
      reddit/                      # prior role; preserved, not current
    personal/
      events/
      notes/
    creative/
      events/
      notes/

  patterns/                        # extracted cross-cutting pattern hubs
    <slug>.md                      # e.g. avoids-hard-conversations.md

  emotional-states/                # dated incidents, evidence-only
    <date>-<context>-<marker>.md

  timeline/                        # chronological index of episodes
    YYYY-MM/
      <date>-<slug>.md             # short notes linking to the full episode

  staging/                         # pending consolidation
    sensitive/                     # flagged by agent; user disposes in batch
    observations/                  # low-confidence; promoted on repetition
```

The Stage 1 scaffolder must create: `state/{preferences,roles,knowledge}`, `domains/<domain>/{events,notes}` (with at least the `work/capmo`, `personal`, `creative` subtrees), `patterns/`, `emotional-states/`, `timeline/`, `staging/{observations,sensitive}`, `people/`. The seeded `Braian.md` is never overwritten if it already exists.

Folder nesting is a routing mechanism for agent attention, not the retrieval mechanism. Stage 1 does not implement retrieval beyond raw file access.

## Node-type frontmatter schemas

All four node types are part of the Stage 1 schema surface so the round-trip layer can read/write any well-formed vault file. Tier 1 capture only writes **state** and **episode** nodes directly (and routes sensitive content to `staging/sensitive/`). Pattern and emotional-state schemas are preserved here so the round-trip layer covers them, but their primary write tools are deferred to later stages.

### State nodes

Durable facts about the user. No date in body. Example: `state/preferences/editor-indent.md`.

```yaml
---
type: state
category: preference | role | knowledge | fact
confidence: high | medium
created: 2026-04-24
updated: 2026-04-24
---
```

Knowledge nodes carry extra constraints: required `evidence` block (dated incidents) and `hypothesis` field; optional `contradicted_by` list. A knowledge node may never be a bare label like "not good at trees."

### Episode nodes

Timestamped events. One per topic-shift within a conversation — not one per session. Live under a domain branch, with a short backlink note from `timeline/YYYY-MM/` so chronological queries work without duplicating content.

```yaml
---
type: episode
date: 2026-04-24T15:02
episode_type: purchase | advice-seeking | research | design-doc | question | personal-story | work-interaction | decision
domain: work/capmo | personal | creative | ...
topic: short-slug
linked:
  - [[patterns/applies-normalization-thinking]]
  - [[state/roles/current-role]]
intensity: low | medium | high
---
```

Body: what happened, what was decided, open threads. Short. Links carry most of the structure; people referenced in the body use stable anonymous labels (see Anonymization in the master spec).

### Pattern nodes (schema preserved; primary write tools deferred to later stages)

Cross-cutting observations promoted once they appear in >=2 domains or >=3 times overall. Live under `patterns/`.

```yaml
---
type: pattern
created: 2026-04-24
hypothesis: >
  Short description of the pattern
evidence_count: 5
last_observed: 2026-04-23
domains_seen_in:
  - work/capmo
  - personal
contradicted_by: []
---
```

Backlinks from Obsidian automatically show all incoming evidence; no manual evidence list is maintained in the pattern node. Pattern nodes may link to other pattern nodes — meta-patterns emerge this way. Stage 1 must round-trip the schema cleanly but does not expose a Tier-1 MCP tool that creates pattern nodes; promotion lives in Stage 2 consolidation.

### Emotional-state nodes (schema preserved; primary write tools deferred to later stages)

Dated incidents only. Structure is constrained hard on purpose, because bad emotion entries are the most dangerous kind — they reshape how every future agent talks to the user.

```yaml
---
type: emotional-state
date: 2026-04-24T02:14
marker: frustration | excitement | anxiety | curiosity | disengagement | ...
intensity: low | medium | high
context: >
  What was happening (task, topic, approximate time-of-day, duration if relevant)
trigger_hypothesis: >
  Proposed trigger, e.g. "tired and blocked after 90 min stuck on a bug"
  Never "topic X makes Braian anxious" on a single sighting.
linked:
  - [[episode/...]]
  - [[patterns/...]]
contradicted_by: []
---
```

Stage 1 round-trips this schema; primary capture tools for emotional-state nodes are deferred until trigger-detection heuristics mature in later stages.

## Tier 1 capture triggers (in scope for Stage 1)

Tier 1 = auto-write, high confidence; written directly to the appropriate branch. Stage 1 implements only Tier 1 triggers plus sensitivity routing.

- Explicit corrections: "don't do X", "stop doing Y", "prefer Z" -> state node.
- Stated facts: role, employer, history, preferences -> state node.
- Self-reports with pattern language: "I always", "I tend to", "I usually", "I have trouble with" -> state node (typically `state/knowledge/` if evidenced).
- Episode capture: fires on topic-shift within a conversation, or at session close. One episode node per distinct topic. Topic-shift heuristics: explicit pivots by the user, a new question after prior conclusion, a significant gap in time followed by a fresh start.

Tier 2 (`staging/observations/`), Tier 3 consolidation, pattern promotion, emotional-state capture, and contradiction reconciliation are explicitly **not** Stage 1 work.

ISO-8601 dates everywhere via `kotlinx-datetime` (`Instant`/`LocalDate`).

## Sensitivity flagging (in scope for Stage 1)

On write, if the agent judges content potentially sensitive, redirect to `staging/sensitive/` instead of the normal branch. The MCP server exposes this as either a dedicated `flag_sensitive` tool or a `sensitive: true` flag on existing write tools (implementation choice). Trigger heuristics (agent best-judgement, not enforced by code):

- Money amounts.
- Identifiable third parties by real name.
- Health specifics.
- Private disputes or conflicts.
- Anything the user has previously tagged `#private` in the vault.

Surface the pending list at session close for batch disposition (keep, move to permanent, delete). The MCP server exposes `list_pending_sensitive` for this. Never interrupt conversational flow to ask.

Real-name to anonymous-label automatic rewriting is **not** Stage 1 work; agents follow the anonymization protocol on their own and the vault stores whatever they hand it.

## MCP server surface (Stage 1)

Local MCP server over stdio with `--vault <path>`. Tools exposed:

- `write_state` — write/update a state node (Tier 1 path).
- `write_episode` — write/update an episode node (Tier 1 path).
- `write_to_staging` — write to `staging/observations/` (used by future Tier 2 wiring; available now).
- `flag_sensitive` (or a `sensitive` flag on the write tools) — route a write to `staging/sensitive/`.
- `list_pending_sensitive` — list everything currently in `staging/sensitive/`.
- `read_node` — read a node by id.
- `list_branch` — list nodes under a branch path.

Path-scoped permissions: reject any path outside the vault root and outside whitelisted branches. `people/` is read-blocked by default (anonymization protocol). The Stage 1 MCP server does not load any subtree on its own; retrieval is Stage 3 work.

## CLI surface (Stage 1)

`personal-graph init --vault <path>` performs the directory scaffold and seeds `Braian.md` idempotently. The existing `consolidate` placeholder remains a stub for Stage 2.

## Repository surface (Stage 1)

`PersonalGraphVaultRepository` (already scaffolded under `core/data`) implements:

- `findNode(id)`
- `listNodesInBranch(branchPath)`
- `writeNode(node)`
- `moveNode(id, newBranchPath)`
- `deleteNode(id)`
- `listBacklinks(id)`

`observeNode` / `observeNodesInBranch` may return `flowOf(...)` placeholders for Stage 1; a real filesystem watcher is out of scope.

## Markdown + frontmatter round-trip

Markdown body + YAML frontmatter (kaml) must round-trip cleanly per node type. Wikilinks (`[[...]]`) are preserved verbatim through read -> parse -> write. Round-trip stability is a tested invariant for all four node types in Stage 1.

## Out of scope for Stage 1

- Consolidation logic (Stage 2).
- Session-start retrieval, `Braian.md` auto-load, domain classification (Stage 3).
- Proactive in-session surfacing of prior context (Stage 4).
- Cross-AI wrappers for non-MCP tools (ChatGPT custom GPTs, Gemini, etc.).
- Real-name to anonymous-label automatic rewriting.
- Filesystem watcher for `observe*` flows.
- Pattern-node and emotional-state-node primary write tools.
- Cloud sync, multi-user, auth.

## Acceptance criteria (contract)

1. Vault scaffolder creates the layout described above under the target vault path.
2. Root `Braian.md` is seeded with orientation; idempotent (never overwrite).
3. CLI `personal-graph init --vault <path>` performs scaffold + seed.
4. Markdown + YAML frontmatter (kaml) round-trip cleanly per node type with wikilinks preserved.
5. `PersonalGraphVaultRepository` implements `findNode` / `listNodesInBranch` / `writeNode` / `moveNode` / `deleteNode` / `listBacklinks`; `observe*` may use `flowOf` placeholders for Stage 1.
6. MCP server runs over stdio with `--vault`, exposing `write_state`, `write_episode`, `write_to_staging`, `flag_sensitive` (or sensitive flag on writes), `list_pending_sensitive`, `read_node`, `list_branch`.
7. Path-scoped permissions: reject paths outside vault root and outside whitelisted branches; `people/` read-blocked by default.
8. Tier 1 capture matches the node-type schemas above; ISO-8601 dates via `kotlinx-datetime`.
9. Tests with Kotest FunSpec + mockk (no `relaxed = true`): scaffold + idempotency, frontmatter round-trip per node type, sensitivity routing, `list_pending_sensitive`, MCP tool happy-path, vault-escape error path.
10. Stage tracking: this per-stage spec lives at `.feature-specs/GRP-1-stage-1-vault-and-capture/spec.md` and the implementation maintains `.feature-specs/STAGES.md` showing each stage's status.
11. README **Status**, **Connecting your vault**, and **Roadmap** sections updated to reflect working `init` + MCP capture tools.
