# Agent Guidelines for Working with This Repository

This file provides guidance to Claude Code, Codex, and other coding agents when working with code in this repository.

## What this project is

Personal Graph is a **JVM Kotlin** project that backs a personal knowledge graph stored as a markdown vault. It exposes two entry points:

- `:mcp-server` — an MCP (Model Context Protocol) server agents talk to for graph capture, search, traversal, and retrieval
- `:cli` — a command-line tool for vault scaffolding, consolidation, and session-start retrieval

There is no Android, iOS, KMP, or Compose target. There is no UI, no GraphQL, no SQL database. Treat the project as **pure JVM library code** (Kotlin 17 target).

## Required reading

**Before architectural work, feature implementation, or significant refactoring, read the relevant doc(s).**

- **[Build logic](docs/build-logic.md)** — convention plugins, what each one owns, what stays in module scripts
- **[DI scope guidelines](docs/di-scope-guidelines.md)** — single-`AppScope` kotlin-inject graph, component composition, testing patterns
- **[Error contracts](docs/error-contracts.md)** — typed result contracts, the "repositories never throw" rule, no `kotlin.Result`
- **[Capability signaling](docs/capability-signaling.md)** — typed prerequisites at boundaries (`VaultPolicy`, index freshness, resolution)
- **[Session-start retrieval](docs/session-start-retrieval.md)** — current behavior of the CLI / MCP session-start surface

When in doubt, check the docs first, then ask.

## Module structure

- `:core:common` — shared utilities, `DispatcherProvider`, `AppScope`, `CoreComponent`
- `:core:domain` — framework-independent contracts and models (`VaultRepository`, `GraphIndexRepository`, `TokenEstimator`, `VaultNode` family, search/traversal services, `VaultPolicy`)
- `:core:data` — implementations (`PersonalGraph*Repository`, `PersonalGraph*Service`, codec, path resolver, scaffolder, indexer)
- `:core:testing` — shared test fixtures, fakes, helpers
- `:mcp-server` — MCP tool surface (`VaultMcpTools`, `VaultGraphMcpTools`, schemas, formatters), JSON-RPC stdio entry point
- `:cli` — clikt-based CLI commands
- `build-logic/convention` — Gradle convention plugins (`personalgraph.jvm.library`, `personalgraph.application`, `personalgraph.detekt`, `personalgraph.spotless`, `personalgraph.jacoco`)

## Essential commands

### Build

- Build everything: `./gradlew build`
- Build a single module: `./gradlew :<module>:build` (e.g. `./gradlew :mcp-server:build`)
- Clean: `./gradlew clean`

### Run

- CLI: `./gradlew :cli:run --args="<args>"`
- MCP server (stdio): `./gradlew :mcp-server:run` or install + run `mcp-server/build/install/personal-graph-mcp-server/bin/personal-graph-mcp-server`
- Install distribution: `./gradlew :mcp-server:installDist` / `./gradlew :cli:installDist`

### Tests

- Run all tests: `./gradlew test`
- Run tests in one module: `./gradlew :<module>:test`
- Tests use **JUnit Platform** (`useJUnitPlatform()` is wired by the convention plugin)

### Quality gate

- Full gate: `./gradlew check` (runs detekt + spotless + jacoco + tests)
- Format: `./gradlew spotlessApply`
- Static analysis only: `./gradlew detekt`

`./gradlew check` is the source of truth for "is this branch ready". `allWarningsAsErrors = true` on library modules — fix at the root, do not suppress.

## Tech stack

- **Language**: Kotlin 17 (`JvmTarget.JVM_17`), 100% JVM
- **DI**: kotlin-inject (single `AppScope`, abstract `*Component` interfaces composed by entry-point modules)
- **Coroutines**: `kotlinx.coroutines` with project-wide `DispatcherProvider` — never `Dispatchers.*` directly
- **Serialization**: `kotlinx.serialization` (JSON) where needed
- **Markdown / frontmatter**: `MarkdownFrontmatterCodec` (project-local)
- **MCP**: `mcp-kotlin-sdk-server`
- **CLI parsing**: `clikt`
- **Logging**: `kotlin-logging` + `logback-classic`
- **Testing**: JUnit Platform + kotest funspec + mockk (no `relaxed = true`)
- **Quality**: Detekt, Spotless (ktfmt), Jacoco

## Code style and project standards

These rules override agent defaults. They apply to every change.

### General

- 2-space indentation in code
- never use `kotlin.Result`
- never use `Any` as a type — anywhere
- never use fully-qualified names in source code (use imports)
- never use hardcoded user-facing strings (no string resources concept here, but copy that surfaces in CLI/MCP responses should still go through a constant or formatter)
- no comments unless absolutely necessary; prefer self-describing code, no inline side-comments
- prefer `.orEmpty()` over `?: ""`
- no debug logs unless actively debugging
- never auto-commit

### Repositories

- repositories must **never** throw exceptions callers need to handle — see [error-contracts.md](docs/error-contracts.md)
- return `null`, an empty collection, `false`, or a typed sealed result instead
- log with structured context (node id, branch, path); never silently suppress
- handle every expected failure inside the repository; do not leak infrastructure exceptions across `core/data` → `core/domain`

### Naming

- data-layer models are named `*DataModel`, not `*Dto`
- implementations are prefixed `PersonalGraph*` (`PersonalGraphVaultRepository`, `PersonalGraphIndexFirstNodeSearchService`, …), not `*Impl`

### DI

- inject `DispatcherProvider`, never `Dispatchers.*`
- use constructor injection only
- annotate stateful collaborators with `@AppScope`; leave cheap stateless factories unscoped
- library modules expose abstract `*Component` interfaces; entry-point modules compose them — see [di-scope-guidelines.md](docs/di-scope-guidelines.md)

### Tests

- kotest funspec
- mockk **without** `relaxed = true`; `relaxUnitFun = true` is acceptable when needed
- shared fixtures live in `:core:testing`
- when implementing a feature, leave tests for the last, after the feature is complete and running. Do not run tests after each subtask.
- do not run formatting / `./gradlew check` unless explicitly asked

### Repositories of state-derived data

- do not use `init { loadX() }` patterns to seed state from a repository
- create state directly from the source of data, e.g. `repository.observeX().map { … }`

## Vault-specific invariants

- `VaultPolicy` is the source of truth for branch read access. Every search, listing, traversal, and indexer must go through it.
- `people/` and `staging/sensitive/` are **hard-excluded** from the graph index regardless of `VaultPolicy` defaults; blocked ids must never appear in returned entries, snippets, links, alias maps, or backlinks.
- the in-process graph index caches by `(file_size, file_modified_at)` plus branch root mtime; cache invalidation also fires on repository write/delete/move.
- the token estimator is a pure-Kotlin deterministic approximator; not a real model tokenizer. Do not promise model-token parity.

## Working with feature specs

The repo uses the `bill-feature-implement` workflow. Specs for in-progress work live under `.feature-specs/<ISSUE-KEY>-<feature-name>/` (e.g. `.feature-specs/PG-6-complete-mcp-graph-retrieval-optimization/`). Large features are decomposed into ordered subtask specs (`spec_subtask_1_*.md`, `spec_subtask_2_*.md`, …) and run as separate sessions in dependency order.

## Configuration

- no `local.properties` is required for normal development
- the JVM toolchain is auto-resolved at Java 17
- there are no flavors, signing keys, or external service credentials in this repo
