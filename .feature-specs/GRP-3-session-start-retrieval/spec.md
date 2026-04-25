# GRP-3 - Stage 3: Session-start retrieval

- **Status:** Complete
- **Issue key:** GRP-3
- **Stage:** 3 of 4
- **Source of truth (full vision/roadmap):** [`/.feature-spec/spec.md`](../../.feature-spec/spec.md)
- **Stage source:** `.feature-spec/spec.md`, Stage 3 session-start retrieval section plus Retrieval protocol and Cross-AI protocol sections.

This per-stage spec is a focused extract of the master spec for Stage 3 only. The master spec remains the canonical reference for the long-term vision; everything below is scoped to loading relevant prior context at session start. Proactive surfacing, live trigger detection, dosing policy, sensitive-staging disposition, anonymization automation, cloud sync, auth, and multi-user behavior are deferred or out of scope.

## Stage 3 deliverables

From the master spec, the Stage 3 deliverables are:

- Formal session-start protocol baked into the MCP server / system-prompt snippet: load `Braian.md` + classified domain subtree + linked patterns.
- Cross-AI wrapper for at least one non-MCP tool (initial target TBD).
- Classification step is explicit and auditable (agent states, in its internal log, which branches it loaded and why).

Exit criterion (from master spec): measurable reduction in redundant re-explaining of preferences across new sessions, on either Claude or the second supported tool.

## Retrieval protocol

Verbatim from the master spec:

> At session start, an agent:
>
> 1. Loads `Braian.md` — the orienting root note
> 2. Classifies the conversation domain from the user's first substantive message
> 3. Loads the relevant domain subtree and any `patterns/` hubs wikilinked from it
> 4. Skips `staging/sensitive/` entirely
> 5. Does not load `emotional-states/` by default — only when the conversation is itself about emotional context or self-reflection
>
> During the session the agent may follow wikilinks to pull additional context on demand.

Stage 3 must turn that protocol into an explicit implementation contract:

- `Braian.md` is always loaded first and reported separately from classified subtree loads.
- Domain classification is derived from the first substantive user message, not from the whole vault.
- The relevant domain subtree is loaded as a bounded vault subtree, then pattern hubs linked from those loaded nodes are loaded from `patterns/`.
- Pattern hub loading follows wikilinks to `patterns/<slug>` nodes. Pattern nodes may link to other pattern nodes; linked pattern loading should cover pattern hubs required to understand the initially loaded domain context without broadening into unrelated vault branches.
- The retrieval result must include an audit surface explaining the classified domain, loaded branches/nodes, linked pattern hubs, skipped branches, and reasons.
- Emotional-state nodes are excluded by default and only included when the first substantive message explicitly concerns emotional context or self-reflection.

## Cross-AI protocol

Verbatim from the master spec:

> Vault format is plain markdown + wikilinks, readable and writable by any AI tool. Glue per tool:
>
> - **Claude and any MCP-capable tool:** a local MCP server exposes scoped read/write on the vault, with path-level permissions (e.g. `staging/sensitive/` is read-only unless explicitly requested)
> - **Non-MCP tools (ChatGPT custom GPTs, Gemini, etc.):** a system-prompt snippet describing the protocol, plus a CLI wrapper that loads relevant nodes into context at session start and appends observations at session end
> - **Consolidation job:** a standalone CLI, model-agnostic, runs periodically to merge duplicates, promote repetitions, annotate contradictions
>
> Shared protocol every participating tool follows:
>
> 1. At session start: load `Braian.md` + classified domain subtree + linked patterns
> 2. During the session: append Tier 1 observations immediately; append Tier 2 to staging
> 3. At session close: write episode node for each topic-shift; surface the sensitive-flagged list for batch disposition
> 4. Periodically: run consolidation to graduate staged entries, merge duplicates, annotate contradictions

Stage 3 implements only the session-start retrieval part of this shared protocol. It must expose the retrieval protocol through the MCP server and/or a CLI-visible surface so agents can use it at session start, and it must add at least one non-MCP cross-AI wrapper or prompt snippet showing how a non-MCP tool should invoke or follow the protocol.

## Vault layout relevant to retrieval

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

## Path policy

Stage 3 must preserve the Stage 1 and Stage 2 repository/path policy constraints:

- Reject any path outside the vault root.
- Reject symlink escapes and do not decode symlinked files as retrieval content.
- Use the existing whitelisted vault branch policy for reads and writes.
- Keep `people/` read-blocked by default.
- Skip `staging/sensitive/` entirely for retrieval.
- Do not load `emotional-states/` by default unless emotional or self-reflection context is explicit.
- Do not broaden retrieval into cloud sync, auth, multi-user behavior, anonymization automation, or sensitive-staging disposition.

## Acceptance criteria (contract)

1. Add a per-stage spec at `.feature-specs/GRP-3-session-start-retrieval/spec.md` with status In Progress and update `.feature-specs/STAGES.md` to point Stage 3 at it.
2. Implement a formal session-start retrieval protocol that loads Braian.md first, classifies the conversation domain from the first substantive message, loads the relevant domain subtree, and loads linked pattern hubs.
3. Expose the protocol through the MCP server and/or CLI-visible surface so agents can use it at session start.
4. Make classification explicit and auditable by reporting or logging which branches/nodes were loaded and why.
5. Preserve path policy: skip staging/sensitive/, skip people/, reject outside-vault/symlink escapes, and do not load emotional-states/ by default unless emotional/self-reflection context is explicit.
6. Provide at least one non-MCP cross-AI wrapper or prompt snippet for using the retrieval protocol from a non-MCP tool.
7. Add focused tests for retrieval classification, path safety, linked pattern loading, audit output, and CLI/MCP wiring as implemented.
8. Update README and stage tracker to reflect Stage 3 when complete.

## Non-goals

- Proactive surfacing or live trigger detection.
- Stage 4 dosing policy or proactive eligibility.
- Cloud sync, auth, multi-user behavior.
- Anonymization automation or people-index loading by default.
- Sensitive-staging disposition.
- Broad emotional-state retrieval by default.
