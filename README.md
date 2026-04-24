# personal-graph

A local-first, Obsidian-compatible markdown graph that captures observations about you — preferences, behaviors, recurring patterns, life episodes, emotional states — and exposes them to any AI tool via a shared protocol.

Context about a person is currently locked inside individual AI tools. The same observation made to ChatGPT on Monday is invisible to Claude on Tuesday. `personal-graph` is a neutral storage layer that any LLM can read from and write to, so agents stop re-learning the same things and start noticing cross-cutting patterns over time.

## Status

Scaffold. The project builds end-to-end (Gradle 9.1, Kotlin 2.3, JDK 17) and both executables run, but the MCP server and consolidation CLI are placeholders — no real capture or retrieval yet. See [`.feature-spec/spec.md`](./.feature-spec/spec.md) for the full vision and phased roadmap.

## Design principles

- **Local-first.** The vault lives on disk only. Cloud sync is deferred.
- **Normalized graph.** Cross-cutting observations are extracted into their own pattern nodes and referenced by wikilink from each domain where they appear. No duplicated descriptions.
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
    <domain>/events/               # episode nodes for that domain
  patterns/                        # extracted cross-cutting pattern hubs
  emotional-states/                # dated incidents, evidence-only
  timeline/YYYY-MM/                # chronological index of episodes
  staging/                         # pending consolidation + sensitive queue
```

Full schema, node types, and capture rules in [`.feature-spec/spec.md`](./.feature-spec/spec.md).

## Architecture

```
core/common         DispatcherProvider, AppScope, CoreComponent
core/domain         node models, VaultRepository, ConsolidationService, WriteOutcome
core/data           PersonalGraphVaultRepository, DataComponent, markdown + frontmatter I/O
core/testing        fixtures for downstream tests

mcp-server          MCP protocol handlers (kotlin-inject @Component)
cli                 consolidation CLI (Clikt)
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

The vault is any directory on your disk that follows the layout above. Both executables take `--vault <path>` once wired up. For now, the MCP server and CLI print placeholder output; real capture and retrieval are in progress.

Once Stage 1 lands:

1. Create a directory for your vault (or point at an existing Obsidian vault).
2. Seed `Braian.md` with a short orientation about yourself.
3. Run `./gradlew :mcp-server:installDist` and register the produced binary with your AI tool as an MCP server pointing at your vault.
4. Agents begin writing observations into typed branches during normal conversations.

## Roadmap

- **Stage 1 — vault + capture (MVP):** MCP server writes Tier 1 observations and episode nodes passively during agent conversations. Sensitivity flagging routes to `staging/sensitive/`.
- **Stage 2 — consolidation:** standalone CLI promotes staged observations, extracts cross-cutting pattern hubs, annotates contradictions.
- **Stage 3 — session-start retrieval:** agents load `Braian.md` + classified domain subtree + linked patterns at the start of every session.
- **Stage 4 — proactive surfacing:** agents detect trigger conditions in-session and surface relevant prior context ("btw, you usually forget X around this point"). Gated behind 2+ months of Stages 1–3 in continuous use.

## Contributing

The design is specified in [`.feature-spec/spec.md`](./.feature-spec/spec.md) — start there. Issues and pull requests welcome. No contributor license agreement; a project license will be added before Stage 1 ships.

## License

[MIT](./LICENSE) — do whatever you want with this code, just keep the copyright notice when redistributing.
