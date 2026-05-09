# Personal Graph Capability Signaling Pattern

This document defines how prerequisites — the conditions that decide whether a tool, repository call, or retrieval surface is allowed to run — should be represented and consumed.

## Goal

Make prerequisites visible in code instead of hiding them in scattered booleans, comments, or silent no-op behavior.

In Personal Graph, a **capability** is any condition that controls whether a vault read, write, retrieval, or MCP tool call may proceed. Examples that exist in the project today:

- whether a branch is read-allowed under `VaultPolicy` (`people/` and `staging/sensitive/` are excluded)
- whether the in-process graph index is fresh for a given file (`(file_size, file_modified_at)` matches)
- whether a node id is resolvable in the current vault
- whether the current MCP request was opened with the bindings the tool needs

## Core rule

**Capabilities should be exposed as typed state or typed providers at the boundary that owns them.**

Prefer:

- a domain-level interface in `core/domain` that names the capability (`VaultPolicy.isReadAllowed(branch)`, `GraphIndexRepository.entryFor(id)`)
- typed enums or sealed types for multi-state capabilities
- typed denial as a result-contract arm when the operation can't run because a capability is missing

Avoid:

- raw booleans passed as the source-of-truth capability model
- duplicating the same `if (branch == "people") return …` check in every caller
- silent early returns when a prerequisite is missing
- inferring capability from unrelated fields (e.g. a path string)

## Capability categories in Personal Graph

### 1. Read-policy capability

`VaultPolicy` decides whether a branch / path is allowed to be read by retrieval surfaces. It must be the single source of truth — every search, listing, traversal, and index-builder consults it before returning a node.

Preferred shape:

```kotlin
interface VaultPolicy {
  fun isReadAllowed(branch: VaultBranch): Boolean
  fun isReadBlocked(path: VaultPath): Boolean
}
```

Rules:

- index entries must be filtered through `VaultPolicy` *plus* the hard exclusion of `people/` and `staging/sensitive/`
- blocked ids must never appear in returned entries, snippets, links, alias maps, or backlink targets
- callers should not re-implement the same allow/block logic locally

### 2. Index-freshness capability

The in-process `GraphIndexRepository` cache decides whether a file's index entry is up to date. The freshness check is part of the repository — callers should never inspect mtimes themselves.

Preferred shape: the repository exposes an entry for an id; if the cached entry is stale, the repository rebuilds and returns the fresh one transparently. Callers consume entries, not freshness booleans.

### 3. Resolution capability

Whether a wikilink, alias, or subject reference resolves to a real node is a capability owned by the index. Callers branch on `null` (not found) vs `entry` (resolved) rather than re-walking the vault.

## Preferred shapes

### A. Provider for queryable capability

Use a `*Provider` / `*Repository` interface when callers ask questions on demand and there is no observable change stream.

```kotlin
interface GraphIndexRepository {
  suspend fun entryFor(id: NodeId): GraphIndexEntry?
  suspend fun entriesIn(branch: VaultBranch, filter: IndexFilter = IndexFilter.None): List<GraphIndexEntry>
  suspend fun lookupByAlias(alias: String): List<GraphIndexEntry>
}
```

This is the dominant shape in this project — Personal Graph has no long-running observable session state today.

### B. Monitor for continuous capability (only when observation is real)

If a future capability needs to be observed over time (e.g. a vault file watcher or a long-running background indexer), expose it as a flow:

```kotlin
interface ExampleCapabilityMonitor {
  val state: Flow<ExampleCapabilityState>
}
```

Do **not** introduce a `*Monitor` for a value that is only ever read once per request — use a `*Provider` instead.

### C. Typed state, not naked booleans

At the capability source, prefer typed state over loose booleans:

```kotlin
sealed interface BranchAccess {
  data object ReadAllowed : BranchAccess
  data class ReadBlocked(val reason: BlockReason) : BranchAccess
}

enum class BlockReason { Policy, HardExcluded, NotInVault }
```

…rather than three independent flags `isAllowed`, `isPolicyBlocked`, `isHardExcluded` that callers have to re-correlate.

Derived booleans are fine inside a single consumer (e.g. a tool handler computing `canTraverse`) once the source capability has been normalized.

### D. Typed denial at operation boundaries

If an operation requires a capability and that capability may be unavailable at runtime, return a typed failure instead of silently doing nothing. This pairs with [error-contracts.md](./error-contracts.md):

- a `*Result.Rejected(reason: …)` arm
- a `Failed(PolicyBlocked)` arm

Do **not** hide a missing capability behind:

- a silent `return`
- a `Boolean false` with no context
- a logged-only outcome that the caller cannot inspect

## Ownership rules

### `core/domain`

- defines capability contracts (`VaultPolicy`, repository interfaces)
- defines typed state and typed denial results
- contains no infrastructure logic

### `core/data`

- implements capability sources (filesystem walks, frontmatter reads, the index cache)
- maps platform errors into the domain capability shape
- never leaks `IOException`, codec errors, or path-string heuristics out

### Entry points (`mcp-server`, `cli`)

- consume capability state via injected domain contracts
- translate typed denial into MCP error responses or CLI exit codes
- never reach into `core/data` to re-check things the domain interface already exposes

## Naming guidance

- `*Repository` for queryable, persistence-backed capability (fetch, lookup)
- `*Policy` for allow/block decisions (`VaultPolicy`)
- `*Provider` for queryable capability that is not persistence-backed
- `*Monitor` reserved for future observable streams — none today
- `*State` or sealed interface for the actual capability value
- `Rejected(...)`, `PolicyBlocked`, `NotResolvable` for operation-level denial reasons

## Anti-patterns to avoid

- passing raw booleans around as the capability model (`isPeople`, `isStagingSensitive`, `isPolicyAllowed` all separately)
- duplicating `VaultPolicy` checks in every search / traversal / index-builder
- inspecting path strings or branch names directly to enforce policy
- silently dropping nodes that are read-blocked instead of returning a typed denial
- introducing a `Flow`-shaped monitor when a single suspending lookup is all the caller actually needs

## Migration guidance

When modernizing a boundary:

1. identify the prerequisite being checked repeatedly (e.g. "is this branch allowed for read?")
2. confirm whether it is queryable (`*Repository`/`*Provider`/`*Policy`) or genuinely observable (`*Monitor`)
3. define or reuse a typed contract in `core/domain`
4. move the source-of-truth check into the contract's implementation in `core/data`
5. for operation-level denial, return a typed result rather than a silent no-op
6. delete duplicated local checks at every call site
