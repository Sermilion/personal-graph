# Personal Graph Error Contract Pattern

This document defines how expected failures should cross module boundaries in Personal Graph.

## Goal

Make failure handling explicit at the call site without leaking infrastructure exceptions (filesystem errors, parser errors, MCP transport errors) into higher layers.

## Core rule

**Expected failures should cross boundaries as typed return values, not as exceptions and never as `kotlin.Result`.**

That means:

- repositories must not throw — return `Boolean`, nullable objects, empty collections, or sealed result contracts depending on what the caller needs
- domain services map filesystem / codec / serialization exceptions into typed values before returning
- MCP tool handlers translate domain results into structured tool responses; raw `Throwable` instances must never reach the MCP transport layer
- `kotlin.Result<T>` is **forbidden** by project convention — define a project-owned sealed contract instead

## Preferred shapes

### 1. Plain return values for fail-graceful repository calls

When the caller only needs to know "was the operation successful" or "did the lookup find something", use a concrete return type. This is the default for repositories.

```kotlin
interface VaultRepository {
  suspend fun findNode(id: NodeId): VaultNode?
  suspend fun listMapNodesInBranch(branch: VaultBranch): List<MapNodeEntry>
  suspend fun writeNode(node: VaultNode): Boolean
}
```

Rules:

- a missing item returns `null`, never throws
- a query with zero matches returns an empty collection
- a write that failed for an expected reason (e.g. read-blocked branch) returns `false` and logs the cause; only programmer errors crash

This pattern fits the project rule: **repositories should never throw exceptions that callers need to handle**.

### 2. Sealed result contract when the caller needs to branch on cause

When the caller has to do different work depending on *why* something failed, return a sealed contract.

```kotlin
sealed interface CaptureResult {
  data class Saved(val nodeId: NodeId) : CaptureResult
  data object Duplicate : CaptureResult
  data class Rejected(val reason: RejectionReason) : CaptureResult
  data class Failed(val cause: CaptureFailure) : CaptureResult
}

sealed interface CaptureFailure {
  data object PolicyBlocked : CaptureFailure
  data class Codec(val message: String) : CaptureFailure
  data class Io(val message: String) : CaptureFailure
}
```

Rules:

- name the contract after the operation (`CaptureResult`, `SearchResult`, `RetrievalResult`)
- carry typed failure causes — not strings, not raw exceptions
- collapse genuinely unexpected failures into a single `Unknown` / `Failed` arm at the boundary that observed them

### 3. Streaming or progressive contracts

If a future operation streams progress (e.g. a long indexing pass), reuse the same typed error family across the stream and the final result.

## What counts as an expected failure

These are expected failures and should be mapped into typed contracts:

- a node id was not found in the vault
- a branch is read-blocked by `VaultPolicy` (`people/`, `staging/sensitive/`)
- frontmatter is malformed
- a referenced wikilink target does not exist
- the index cache is stale and must be rebuilt
- an MCP tool argument fails validation
- the filesystem reports a missing path or a permission error

## What still uses exceptions

Exceptions are appropriate for:

- programmer errors and broken invariants (`require(...)`, `check(...)`)
- impossible states inside DI bootstrap
- bugs in the codec/parser surface that should crash tests rather than silently corrupt the graph
- low-level platform failures *before* they are mapped at the boundary

For new boundaries, do not add exception-based APIs unless the caller genuinely benefits from exception handling over a typed contract.

## Mapping rules

### `core/data`

- catch raw `IOException`, `JsonParseException`, codec errors, etc.
- log with structured context (node id, branch, file path) — never silently suppress
- map into the domain-facing contract before returning
- never re-throw infrastructure exceptions across the module boundary

### `core/domain`

- consume typed results from the data layer
- enrich with domain meaning if needed (e.g. wrap `Codec(...)` failures from multiple sources into a single domain-relevant `IndexBuildFailure`)
- expose results to consumers (MCP handlers, CLI commands) as typed contracts

### Entry points (`mcp-server`, `cli`)

- translate typed domain results into the surface protocol (MCP JSON, CLI exit codes + stderr)
- format human-readable messages here, not in the domain or data layers
- only at this boundary may an unexpected exception be caught-and-collapsed into a generic protocol error response

## Naming guidance

- `*Result` for the operation outcome envelope
- `*Failure` or `*Error` for the typed failure cause carried inside
- `Unknown(reason: String?)` instead of swallowing detail into a success-shaped fallback
- avoid feature-prefixing transport categories (`NetworkError`, `IoError`) when a shared cause type would do

## Repository patterns to follow

The project rule is: **repositories never throw**. To stay consistent:

1. Wrap every I/O call site in a `try/catch` inside the repository implementation.
2. Log with a stable tag and meaningful identifiers (node id, branch, path).
3. Return one of: `null`, an empty collection, `false`, or a sealed `*Result.Failure(...)` value.
4. Never use `runCatching { … }` followed by `.getOrNull()` as a substitute for explicit handling — it hides the cause from logs.
5. Never bubble a raw exception out of an `interface` declared in `core/domain`.

## Anti-patterns

- exposing raw `Throwable` or `Exception` subclasses across module boundaries
- using `kotlin.Result<T>` (forbidden — see global standards)
- broad `catch (e: Exception) { return null }` blocks with no logging
- returning a string "error message" via a success-shaped contract
- different feature contracts that all redefine the same `Io` / `NotFound` arms instead of sharing a base type

## Migration guidance

When modernizing an existing boundary:

1. identify the expected failure categories the caller actually needs to branch on
2. decide between plain return values (option 1) or a sealed contract (option 2)
3. move the `try/catch` mapping into the repository implementation
4. delete any `throws` annotations or `@Throws(...)` on the interface side
5. update callers to branch on the typed result; remove ad-hoc catch sites in higher layers
