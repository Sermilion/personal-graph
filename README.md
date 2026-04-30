# personal-graph

A local-first, Obsidian-compatible markdown graph that captures observations about you — preferences, behaviors, recurring patterns, life episodes, emotional states — and exposes them to any AI tool via a shared protocol.

Context about a person is currently locked inside individual AI tools. The same observation made to ChatGPT on Monday is invisible to Claude on Tuesday. `personal-graph` is a neutral storage layer that any LLM can read from and write to, so agents stop re-learning the same things and start noticing cross-cutting patterns over time.

## Status

Stage 1 shipped. The CLI scaffolds an Obsidian-compatible vault on demand (`personal-graph init --vault <path>`) and the local MCP server exposes scoped read/write capture tools over stdio. Tier 1 capture (`write_state`, `write_episode`, `write_to_staging`), sensitivity routing (`flag_sensitive`, `list_pending_sensitive`), and scoped reads (`read_node`, `list_branch`) are working end-to-end. Stage 2 consolidation is implemented as a manual CLI command that promotes repeated staged observations, merges equivalent duplicates, extracts pattern hubs, annotates contradictions, and migrates fragmented legacy domain notes into canonical subject hubs when possible. Stage 3 session-start retrieval is map-first: it loads bounded root context from `Braian.md`, classifies the first user message, returns a compact `available_map`, and suggests precise follow-up reads through the CLI and MCP server. Proactive surfacing remains a placeholder. See [`.feature-spec/spec.md`](./.feature-spec/spec.md) for the full vision and phased roadmap; per-stage progress lives in [`.feature-specs/STAGES.md`](./.feature-specs/STAGES.md).

## Design principles

- **Local-first.** The vault lives on disk only. Cloud sync is deferred.
- **Normalized graph.** Cross-cutting observations are extracted into their own pattern nodes and referenced by wikilink from each domain where they appear. No duplicated descriptions.
- **Reuse-first capture.** Agents should look for a relevant existing note before creating a new one. Durable state and pattern notes should be named for reusable concepts, while exact incident names belong mainly to dated episode/evidence nodes. When a write replaces an existing graph path, the previous version is archived under `outdated/resolved/` instead of being silently deleted.
- **Evidence over labels.** Entries that shape future agent behavior (knowledge state, emotional state, patterns) must be specific dated incidents with hypotheses — never compressed personality labels.
- **Confidence-gated writes.** High-confidence observations land in permanent branches; lower-confidence observations land in `staging/` and graduate only after repetition or explicit promotion.
- **Async sensitivity handling.** Agents never pause mid-conversation to ask whether to log. Potentially sensitive episodes go to `staging/sensitive/` for batch review.
- **Domain-neutral protocol.** Any AI tool that can read and write files, or call a well-defined MCP server, can participate.

## Vault layout

```
  <your-vault>/
    Braian.md                        # root orienting note; always loaded first
    state/                           # durable facts, preferences, roles
    domains/                         # work/capmo, personal, creative, ...
      <domain>/events/               # dated work/life records in the canonical domain/topic
      <domain>/subjects/             # reusable subject hubs with appended dated evidence
    patterns/                        # extracted cross-cutting pattern hubs
    emotional-states/                # dated incidents, evidence-only
    timeline/YYYY-MM/                # chronological backlink stubs, not duplicate content
    staging/                         # pending consolidation + sensitive queue
```

Full schema, node types, and capture rules in [`.feature-spec/spec.md`](./.feature-spec/spec.md).

## Architecture

```
core/common         DispatcherProvider, AppScope, CoreComponent
core/domain         node models, VaultRepository, ConsolidationService, SessionStartRetrievalService, WriteOutcome
core/data           PersonalGraphVaultRepository, DataComponent, markdown + frontmatter I/O
core/testing        fixtures for downstream tests

mcp-server          MCP protocol handlers (kotlin-inject @Component)
cli                 init, consolidation, and session-start retrieval CLI (Clikt)
```

Dependency direction is inward: `mcp-server` and `cli` depend on `core/*`; no `core/*` module depends on an application module.

## Build

Requires JDK 17. The Gradle wrapper handles everything else.

```bash
./gradlew assemble            # build everything
./gradlew :cli:run --args="--help"
./gradlew :mcp-server:run
./gradlew :cli:installDist    # runnable distribution under cli/build/install/
```

## Connecting your vault

The vault is any directory on your disk that follows the layout above; pass its absolute path as `--vault <path>` to both executables.

Build the runnable distributions:

```bash
./gradlew :cli:installDist :mcp-server:installDist
```

Scaffold a fresh vault (idempotent — never overwrites `Braian.md`):

```bash
cli/build/install/personal-graph-cli/bin/personal-graph-cli init --vault /absolute/path/to/your/vault
```

The CLI creates the directory layout (`state/...`, `domains/.../events`, `domains/.../subjects`, `patterns/`, `emotional-states/`, `timeline/`, `staging/...`, `people/`) and seeds `Braian.md` with a short orientation note. Replace the `# TODO` block with a few sentences about yourself before pointing agents at it.

Run manual consolidation:

```bash
cli/build/install/personal-graph-cli/bin/personal-graph-cli consolidate --vault /absolute/path/to/your/vault
```

Consolidation reads `staging/observations/` and durable graph branches only. By default it never reads `staging/sensitive/` or `people/`; sensitive review remains explicit through the Stage 1 sensitive queue tools. It also migrates legacy `domains/.../notes/...` content into canonical `domains/.../subjects/...` hubs when possible.

Load session-start context:

```bash
cli/build/install/personal-graph-cli/bin/personal-graph-cli session-start --vault /absolute/path/to/your/vault "first user message"
```

Session-start retrieval always reports `Braian.md` first, classifies the message into the active domains (`work/capmo`, `work/skill-bill`, `work/readian`, `work/context-app`, `creative/music`, `personal`, or `general`), and returns a compact map plus suggested follow-up reads. The default report separates `loaded_context`, `available_map`, `suggested_reads`, skipped branches, and audit reasons; it does not dump broad state or domain branch bodies. Use `read_node` or `list_branch` for exact full-body follow-up reads, or pass `--retrieval-mode full-loading` only when explicitly opting into compatibility full loading. It skips `people/`, skips `staging/` including `staging/sensitive/`, and excludes `emotional-states/` unless the first message explicitly asks about emotional context or self-reflection. Scoped state (`scope` / `scopes`) can make durable state visible only for matching domains while global preferences remain global. Non-MCP usage guidance lives in [`docs/session-start-retrieval.md`](./docs/session-start-retrieval.md).

Run the local MCP server over stdio:

```bash
mcp-server/build/install/personal-graph-mcp-server/bin/personal-graph-mcp-server --vault /absolute/path/to/your/vault
```

Register that binary with your AI tool's MCP configuration. The server exposes the Stage 1 capture/read tools plus Stage 3 retrieval:

- `write_state`, `write_episode`, `write_to_staging` — Tier 1 capture. Existing target paths are archived under `outdated/resolved/` before replacement and returned as `archived_paths`; `write_episode` also reuses or appends to a canonical subject hub and writes only a timeline index stub.
- `flag_sensitive`, `list_pending_sensitive` — sensitivity routing for batch disposition.
- `read_node`, `list_branch` — explicit full-body follow-up reads. `people/` is read-blocked by default; reads outside the vault root or outside whitelisted branches are rejected.
- `session_start` — audited map-first retrieval of bounded `Braian.md` context, classification metadata, `available_map`, `suggested_reads`, skips, and audit reasons. `retrieval_mode=full-loading` remains the explicit opt-in compatibility path for loaded node bodies.

All log output goes to stderr; stdout is reserved for the MCP framing channel.

## Roadmap

- **Stage 1 — vault + capture (MVP) — shipped.** `personal-graph init` scaffolds the layout; the local MCP server writes Tier 1 observations and episode nodes passively during agent conversations; sensitivity flagging routes to `staging/sensitive/`.
- **Stage 2 — consolidation — implemented:** standalone CLI promotes staged observations, merges equivalent staged duplicates, extracts cross-cutting pattern hubs, annotates contradictions, and reports changed node ids.
- **Stage 3 — session-start retrieval — implemented:** agents load bounded `Braian.md` context plus a compact classified graph map at the start of every session through CLI or MCP, then follow `suggested_reads` with explicit `read_node` / `list_branch` calls when full bodies are needed.
- **Stage 4 — proactive surfacing:** agents detect trigger conditions in-session and surface relevant prior context ("btw, you usually forget X around this point"). Gated behind 2+ months of Stages 1-3 in continuous use.

## Contributing

The design is specified in [`.feature-spec/spec.md`](./.feature-spec/spec.md) — start there. Issues and pull requests welcome. No contributor license agreement; a project license will be added before Stage 1 ships.

## License

[MIT](./LICENSE) — do whatever you want with this code, just keep the copyright notice when redistributing.
