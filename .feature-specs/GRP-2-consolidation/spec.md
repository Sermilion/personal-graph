# GRP-2 - Stage 2: Consolidation

- **Status:** Complete
- **Issue key:** GRP-2
- **Stage:** 2 of 4
- **Source of truth (full vision/roadmap):** [`/.feature-spec/spec.md`](../../.feature-spec/spec.md)
- **Stage source:** `.feature-spec/spec.md`, Stage 2 consolidation section plus capture tiers that feed consolidation.

This per-stage spec is a focused extract of the master spec for Stage 2 only. The master spec remains the canonical reference for the long-term vision; everything below is scoped to consolidation of staged observations into durable graph nodes. Session-start retrieval, proactive surfacing, non-MCP cross-AI wrappers, scheduler integration, and sensitive-staging disposition are deferred or out of scope.

## Stage 2 deliverables

From the master spec, the Stage 2 deliverables are:

- Standalone consolidation CLI: detects repetitions, promotes staged observations, extracts pattern hubs on the >=2-domains / >=3-occurrences threshold, logs contradictions.
- Runs weekly in concept, but manual CLI trigger is sufficient for Stage 2. Cron is optional and out of scope for this implementation.
- Reports what changed each run: graduated staged observations, merged duplicate observations, promoted pattern hubs, and annotated contradictions.

Exit criterion (from master spec): first cross-domain pattern hub promoted automatically from real data, not seeded by hand.

## Consolidation scope

Consolidation is the Stage 2 implementation of Tier 3:

- Repetition detection: promote `staging/observations/` entries on second sighting in a matching context.
- Pattern hub extraction: observations seen in >=2 domains or >=3 times overall.
- Contradiction reconciliation: weaken or annotate existing entries.

Consolidation reads staged observations from `staging/observations/` and may write durable state, episode, pattern, and emotional-state nodes using the existing markdown/YAML schemas. It must preserve Stage 1 path policy constraints:

- Never read `staging/sensitive/` by default.
- Never read `people/` by default.
- Reject paths outside the vault root and outside whitelisted branches.
- Preserve symlink-aware vault containment behavior and owner-only file permission behavior already established by Stage 1 repository paths.

## Vault layout relevant to consolidation

```
personal-graph/
  state/                           # durable facts, preferences, roles
    preferences/
    roles/
    knowledge/                     # skill-level entries, evidence-based only

  domains/
    work/
      capmo/
        events/
        notes/
      reddit/
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

  timeline/
    YYYY-MM/
      <date>-<slug>.md

  staging/
    sensitive/                     # not read or dispositioned by Stage 2
    observations/                  # low-confidence observations promoted by consolidation
```

Folder nesting is a routing mechanism for attention, not the retrieval mechanism. Consolidation may scan allowed branches needed for duplicate, repetition, pattern, and contradiction checks, but it must not broaden default reads into `staging/sensitive/` or `people/`.

## Node-type frontmatter schemas

Markdown body + YAML frontmatter must remain round-trip safe. Wikilinks (`[[...]]`) must be preserved verbatim through read -> parse -> write.

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

Timestamped events. One per topic-shift within a conversation, not one per session. Live under a domain branch, with a short backlink note from `timeline/YYYY-MM/` so chronological queries work without duplicating content.

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

Body: what happened, what was decided, open threads. Short. Links carry most of the structure; people referenced in the body use stable anonymous labels.

### Pattern nodes

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

Backlinks from Obsidian automatically show all incoming evidence; no manual evidence list is maintained in the pattern node. Pattern nodes may link to other pattern nodes, so meta-patterns can emerge.

Consolidation owns Stage 2 pattern creation. It should create or update pattern hubs when equivalent observations appear in at least 2 domains or at least 3 total occurrences. Constituent durable nodes should wikilink the promoted pattern hub so backlinks remain the evidence trail.

### Emotional-state nodes

Dated incidents only. Structure is constrained hard on purpose, because bad emotion entries are the most dangerous kind: they reshape how every future agent talks to the user.

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

Consolidation may promote recurring triggers into pattern nodes under `patterns/`, linking back to constituent emotional-state nodes. No emotion entry is ever written as a trait.

## Capture tiers feeding consolidation

### Tier 1 - auto-write, high confidence

Written directly to the appropriate branch.

- Explicit corrections: "don't do X", "stop doing Y", "prefer Z".
- Stated facts: role, employer, history, preferences.
- Self-reports with pattern language: "I always", "I tend to", "I usually", "I have trouble with".

Tier 1 nodes already live in durable branches. Consolidation may use them as comparison targets for duplicate and contradiction detection, but should not silently overwrite them.

### Tier 2 - staged, graduate on consolidation

Written to `staging/observations/`.

- Validated non-obvious choices: agent proposed unusual approach, user accepted without pushback.
- Decisions with reasoning.
- Agent meta-observations: noticing a pattern mid-conversation.

Stage 2 must scan this tier and promote staged observations on repeated sighting in a matching context.

### Tier 3 - consolidation only

Runs as a manual standalone CLI in Stage 2.

- Repetition detection: promote `staging/observations/` entries on second sighting in a matching context.
- Pattern hub extraction: observations seen in >=2 domains or >=3 times overall.
- Contradiction reconciliation: weaken or annotate existing entries.

## Shared protocol relevant to consolidation

Every participating tool follows this high-level protocol:

1. At session start: load `Braian.md` + classified domain subtree + linked patterns.
2. During the session: append Tier 1 observations immediately; append Tier 2 to staging.
3. At session close: write episode node for each topic-shift; surface the sensitive-flagged list for batch disposition.
4. Periodically: run consolidation to graduate staged entries, merge duplicates, annotate contradictions.

Stage 2 implements only step 4 as a manual CLI.

## CLI surface

`personal-graph consolidate --vault <path>` runs real consolidation against the vault. The current stub is replaced by code that:

- Builds the same dependency graph style as the existing CLI.
- Runs consolidation through a domain-level service boundary.
- Emits/logs a run report with counts and changed node ids for graduated observations, merged duplicates, promoted patterns, and annotated contradictions.
- Does not read `staging/sensitive/` or `people/` by default.

## Implementation notes

Stage 2 is implemented by the manual `personal-graph consolidate --vault <path>` command. Each run scans only `staging/observations/` plus durable graph branches, promotes repeated matching observations into durable state nodes, merges equivalent staged sightings into a canonical durable node, creates or updates pattern hubs under `patterns/` when the domain/occurrence threshold is met, and annotates durable entries when a staged observation contradicts existing state. Run output reports graduated observations, merged duplicates, promoted patterns, and annotated contradictions.

Default consolidation reads deliberately exclude `staging/sensitive/` and `people/`. Sensitive disposition remains a Stage 1 explicit-review workflow and is not part of Stage 2 consolidation.

## Acceptance criteria (contract)

1. Add a per-stage spec at `.feature-specs/GRP-2-consolidation/spec.md` with status In Progress and update `.feature-specs/STAGES.md` to point Stage 2 at it.
2. Standalone CLI `personal-graph consolidate --vault <path>` runs real consolidation instead of the current stub.
3. Consolidation scans `staging/observations/` and promotes staged observations on repeated sighting in a matching context.
4. Consolidation detects and merges duplicates so repeated equivalent staged observations do not produce duplicate durable nodes.
5. Consolidation extracts pattern hubs under `patterns/` when observations appear in at least 2 domains or at least 3 total occurrences.
6. Consolidation annotates or logs contradictions against existing entries instead of silently overwriting them.
7. Consolidation reports what changed each run: graduated, merged, promoted patterns, annotated contradictions.
8. Consolidation preserves Stage 1 repository/path policy constraints and never reads `staging/sensitive/` or `people/` by default.
9. Markdown/YAML schemas and wikilinks remain round-trip safe for state, episode, pattern, and emotional-state nodes.
10. Add focused tests for consolidation promotion, duplicate merging, pattern thresholding, contradiction annotation/reporting, CLI wiring, and path-safety/sensitive-skip behavior.
11. README and stage tracker reflect Stage 2 as implemented when complete.

## Non-goals

- Cron/scheduler integration beyond manual CLI.
- Session-start retrieval or domain classification (Stage 3).
- Proactive surfacing/dosing policy (Stage 4).
- Real-name anonymization automation.
- Cloud sync, multi-user, auth.
- Reading or dispositioning `staging/sensitive/`.
- Non-MCP cross-AI wrappers.
