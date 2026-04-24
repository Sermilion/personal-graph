# Personal Graph — Feature Spec

## Problem

Context about a person — preferences, behaviors, recurring patterns, life episodes, emotional states — is currently locked inside individual AI tools. The same observation made to one assistant is invisible to another. Over time this produces fragmented assistance: every agent re-learns the same things, misses cross-cutting patterns, and never notices recurring failure modes early enough to flag them.

## Vision

A local-only, Obsidian-compatible markdown graph that:

- Captures observations about Braian's behavior, preferences, decisions, episodes, and emotional states, written passively by AI agents during normal conversations
- Normalizes cross-cutting patterns into dedicated pattern nodes, referenced by wikilink from each domain where they appear
- Is readable and writable by any AI tool through a shared protocol
- Is browsable and editable by Braian directly through Obsidian's graph view and note editor
- Surfaces relevant prior context at session start, so agents act with knowledge of prior observations rather than re-learning from scratch
- Eventually: detects trigger conditions for known patterns in-session and surfaces timely prior context ("these are the moments you usually forget and come back to fix too late")

The proactive-surfacing end state is explicitly out of scope for the initial stages. See Stage 4.

## Design principles

- **Local-first.** The vault lives on disk only. Cloud sync is deferred; privacy is a harder problem once the vault leaves the device.
- **Normalized graph.** Cross-cutting observations are extracted into their own nodes and referenced by wikilink from each domain where they appear. Descriptions live in one place; pointers are cheap. Evidence stays in domain branches; abstractions live in `patterns/`.
- **Evidence over labels.** Entries that shape future agent behavior (knowledge-state, emotional-states, patterns) must be specific incidents with dated evidence and a hypothesis. Never compressed labels. Labels distort every future interaction the agent has with Braian.
- **Revise aggressively.** Contradicting evidence weakens or annotates existing entries. No entry is immutable.
- **Confidence-gated writes.** High-confidence observations land in permanent branches immediately. Lower-confidence observations land in `staging/` and graduate only after repetition or explicit promotion.
- **Async sensitivity handling.** The agent never pauses mid-conversation to ask whether to log. Potentially sensitive episodes go to `staging/sensitive/` and are reviewed in batch at session close.
- **No behavior damping.** Entries noting a gap (e.g. "loose on complexity analysis in one instance") must not cause agents to hedge precision in general. The entry records a specific gap, not a character trait.
- **Domain-neutral protocol.** Any AI tool that can read and write files, or call a well-defined MCP server, should be a first-class participant.

## Vault layout

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

Folder nesting is not the retrieval mechanism; it is a routing mechanism for attention. Agents load a subtree plus any wikilinked pattern hubs, not the entire tree.

## Node types

### State nodes

Durable facts about Braian. No date in body. Example: `state/preferences/editor-indent.md`.

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

Body: what happened, what was decided, open threads. Short. Links carry most of the structure; people referenced in the body use stable anonymous labels (see Anonymization below).

### Pattern nodes

Cross-cutting observations promoted once they appear in ≥2 domains or ≥3 times overall. Live under `patterns/`.

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

Backlinks from Obsidian automatically show all incoming evidence; no manual evidence list is maintained in the pattern node. Pattern nodes may link to other pattern nodes — meta-patterns emerge this way.

### Emotional-state nodes

Dated incidents only. Structure is constrained hard on purpose, because bad emotion entries are the most dangerous kind — they reshape how every future agent talks to Braian.

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

## Capture triggers

### Tier 1 — auto-write, high confidence

Written directly to the appropriate branch.

- Explicit corrections: "don't do X", "stop doing Y", "prefer Z"
- Stated facts: role, employer, history, preferences
- Self-reports with pattern language: "I always", "I tend to", "I usually", "I have trouble with"

### Tier 2 — staged, graduate on consolidation

Written to `staging/observations/`.

- Validated non-obvious choices (agent proposed unusual approach, user accepted without pushback)
- Decisions with reasoning
- Agent meta-observations (noticing a pattern mid-conversation)

### Tier 3 — consolidation only (not a real-time trigger)

Runs on a schedule.

- Repetition detection: promote `staging/observations/` entries on second sighting in a matching context
- Pattern hub extraction: observations seen in ≥2 domains or ≥3 times overall
- Contradiction reconciliation: weaken or annotate existing entries

### Episode capture

Separate from the tier system. Fires on topic-shift within a conversation, or at session close. One episode node per distinct topic. Topic-shift heuristics: explicit pivots by the user, a new question after prior conclusion, a significant gap in time followed by a fresh start.

### Emotional-state capture

Fires on linguistic markers of strong affect combined with a specific trigger context. Enforces the full schema constraints above; does not write a node if a specific trigger and context cannot be articulated.

### Sensitivity flagging

On write, if the agent judges content potentially sensitive, redirect to `staging/sensitive/` instead of the normal branch. Trigger heuristics (agent best-judgement):

- Money amounts
- Identifiable third parties by real name
- Health specifics
- Private disputes or conflicts
- Anything the user has previously tagged `#private` in the vault

Surface the pending list at session close for batch disposition (keep, move to permanent, delete). Never interrupt conversational flow to ask.

## Retrieval protocol

At session start, an agent:

1. Loads `Braian.md` — the orienting root note
2. Classifies the conversation domain from the user's first substantive message
3. Loads the relevant domain subtree and any `patterns/` hubs wikilinked from it
4. Skips `staging/sensitive/` entirely
5. Does not load `emotional-states/` by default — only when the conversation is itself about emotional context or self-reflection

During the session the agent may follow wikilinks to pull additional context on demand.

## Cross-AI protocol

Vault format is plain markdown + wikilinks, readable and writable by any AI tool. Glue per tool:

- **Claude and any MCP-capable tool:** a local MCP server exposes scoped read/write on the vault, with path-level permissions (e.g. `staging/sensitive/` is read-only unless explicitly requested)
- **Non-MCP tools (ChatGPT custom GPTs, Gemini, etc.):** a system-prompt snippet describing the protocol, plus a CLI wrapper that loads relevant nodes into context at session start and appends observations at session end
- **Consolidation job:** a standalone CLI, model-agnostic, runs periodically to merge duplicates, promote repetitions, annotate contradictions

Shared protocol every participating tool follows:

1. At session start: load `Braian.md` + classified domain subtree + linked patterns
2. During the session: append Tier 1 observations immediately; append Tier 2 to staging
3. At session close: write episode node for each topic-shift; surface the sensitive-flagged list for batch disposition
4. Periodically: run consolidation to graduate staged entries, merge duplicates, annotate contradictions

## Anonymization

Third parties (colleagues, friends, family, public figures) are never written into the vault by real name. Agents replace real names with stable anonymous labels of the form `[[people/<role>-<n>]]` (e.g. `[[people/manager-1]]`, `[[people/sibling-1]]`). A local `people/` index stays out of any agent's retrieval set unless the user explicitly loads it.

## Explicitly out of scope (initially)

- **Proactive nudges.** In-session surfacing of "btw, you usually forget X" depends on reliable pattern detection plus good dosing, and premature rollout destroys trust. Stage 4 only, after 1–3 produce enough signal.
- **Cloud sync.** Privacy is a harder problem when the vault leaves the device. Deferred.
- **Third-party private information capture.** Only substance is kept; real names are anonymized.

## Stages

### Stage 1 — vault + capture (MVP target)

Deliverables:

- Directory structure per the layout above
- Root `Braian.md` seeded with a short orientation
- Local MCP server exposing scoped read/write on the vault
- Claude writes Tier 1 observations and episode nodes per protocol
- Sensitivity flagging routes to `staging/sensitive/`
- Session-close surfaces the sensitive pending list for batch disposition
- No retrieval yet beyond what Claude does naturally with file access

Exit criterion: after two weeks of normal use, the vault contains ≥30 non-trivial observations with correct typing, ≥5 episodes per domain in active use, and no observed drift from the schema constraints.

### Stage 2 — consolidation

Deliverables:

- Standalone consolidation CLI: detects repetitions, promotes staged observations, extracts pattern hubs on the ≥2-domains / ≥3-occurrences threshold, logs contradictions
- Runs weekly (manual trigger initially; cron optional)
- Reports what changed each run (graduated, merged, contradicted)

Exit criterion: first cross-domain pattern hub promoted automatically from real data, not seeded by hand.

### Stage 3 — session-start retrieval

Deliverables:

- Formal session-start protocol baked into the MCP server / system-prompt snippet: load `Braian.md` + classified domain subtree + linked patterns
- Cross-AI wrapper for at least one non-MCP tool (initial target TBD)
- Classification step is explicit and auditable (agent states, in its internal log, which branches it loaded and why)

Exit criterion: measurable reduction in redundant re-explaining of preferences across new sessions, on either Claude or the second supported tool.

### Stage 4 — proactive surfacing (out of scope until 1–3 prove out)

Deliverables:

- Agent detects trigger conditions for known patterns during a live session and surfaces relevant prior context
- Dosing policy: at most one proactive note per session; only patterns with ≥5 evidence entries and no unresolved contradictions are eligible
- Easy off-switch; easy to demote a pattern back below the proactive-eligible threshold

Entry criterion: stages 1–3 must be in continuous use for ≥2 months before Stage 4 work begins.

## Open questions to revisit after Stage 1

- Consolidation cadence: weekly vs threshold-based (e.g. after N new staged observations)
- Anonymization: agent-assisted mapping of real names to stable anonymous labels, or manual maintenance
- Retention: does old domain data (e.g. `work/reddit/` after leaving) get archived, or stay live?
- Sync strategy: local-only forever, or encrypted sync to private storage?
- Scope of skill-bill integration: does the existing feature-implement / feature-verify workflow become a first-class write source, or remain independent?
