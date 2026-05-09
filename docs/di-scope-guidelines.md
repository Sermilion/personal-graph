# Dependency Injection Guidelines

This document describes how Dependency Injection works in Personal Graph using **kotlin-inject**.

## Architecture overview

Personal Graph is a JVM project with no UI and no user-session concept. The DI graph is intentionally flat:

```
McpServerComponent (AppScope)        CliComponent (AppScope)
   composes                              composes
   - CoreComponent                       - CoreComponent
   - DataComponent                       - DataComponent
```

There is one runtime scope (`@AppScope`) and one runtime component per entry point (`McpServerComponent` for the MCP server, `CliComponent` for the CLI). Library modules (`core/common`, `core/domain`, `core/data`) expose abstract `*Component` interfaces that the entry-point modules implement.

### Component ownership

| Component | Owner | Lifetime |
| --- | --- | --- |
| `CoreComponent` | `core/common` | merged into entry-point components |
| `DataComponent` | `core/data` | merged into entry-point components |
| `McpServerComponent` | `mcp-server` | MCP server process |
| `CliComponent` | `cli` | CLI process |

Entry-point components extend the library `*Component` interfaces and add their own bindings + the public API surface (the abstract properties consumers read).

## The single scope: `@AppScope`

```kotlin
@Scope
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class AppScope
```

Use `@AppScope` for:

- repositories (`PersonalGraphVaultRepository`, `PersonalGraphGraphIndexRepository`, …)
- services that must keep in-process state across calls (`PersonalGraphVaultCaptureService`, `PersonalGraphSessionStartRetrievalService`, the search/index services)
- the `DispatcherProvider`
- frontmatter codec, path resolver, scaffolder
- domain helpers that are stateless but expensive to construct

Anything that is cheap to construct and stateless can be left as plain `@Inject` without a scope annotation; kotlin-inject will create it on demand.

There is intentionally **no** `UserScope`, `ScreenScope`, or session-scoped subgraph. Personal Graph has no user session lifecycle and no UI — all dependencies either live for the process or are constructed per call.

## Component composition pattern

Each library module exposes an abstract component interface that the entry-point components implement.

```kotlin
// core/common
@AppScope
@Component
abstract class CoreComponent {
  abstract val dispatcherProvider: DispatcherProvider
  // …shared-bindings as abstract properties
}

// core/data
interface DataComponent {
  // bindings provided by core/data are exposed here
}

// mcp-server
@AppScope
@Component
abstract class McpServerComponent(@get:Provides val vaultRoot: Path) :
  CoreComponent,
  DataComponent {
  abstract val dispatcherProvider: DispatcherProvider
  abstract val vaultRepository: VaultRepository
  abstract val vaultMcpTools: VaultMcpTools
  abstract val nodeSearchService: NodeSearchService
  abstract val branchListingService: BranchListingService
  // …public surface
}
```

Rules:

1. Library modules **must not** declare a concrete `@Component`. They expose abstract interfaces that callers compose.
2. Entry-point modules combine library components and add the bindings unique to that runtime (e.g. `vaultRoot` is provided by the entry point, not by the library).
3. Public surface (the things callers read after merging the component) is declared as abstract `val` properties on the entry-point component.

## Provides bindings

Use `@get:Provides` on constructor parameters to seed runtime values into the graph:

```kotlin
@AppScope
@Component
abstract class McpServerComponent(@get:Provides val vaultRoot: Path) : …
```

Inside library `*Component` interfaces, factory functions can be declared as `@Provides` extensions or as abstract `@Provides` members on a companion object.

## Inject sites

Constructor injection is the default and the only style we use:

```kotlin
@Inject
@AppScope
class PersonalGraphVaultRepository(
  private val vaultRoot: Path,
  private val codec: MarkdownFrontmatterCodec,
  private val pathResolver: VaultPathResolver,
  private val dispatcherProvider: DispatcherProvider,
) : VaultRepository
```

Field injection, JSR-330 `Provider<T>`, and lazy lookups are not used. If a dependency is genuinely optional, model it as a sealed type or a default fake — not as a nullable injection target.

## Coroutines and dispatchers

Never inject `Dispatchers.*` directly. Inject the project-wide `DispatcherProvider` and call `dispatcherProvider.io`, `dispatcherProvider.default`, or `dispatcherProvider.main` at the call site:

```kotlin
@Inject
@AppScope
class PersonalGraphIndexFirstNodeSearchService(
  private val dispatcherProvider: DispatcherProvider,
  // …
) : NodeSearchService {
  override suspend fun search(query: String): SearchResult =
    withContext(dispatcherProvider.io) { … }
}
```

`DispatcherProvider` exists precisely so tests can swap in a deterministic dispatcher. Injecting raw `Dispatchers.*` defeats that.

Long-lived `CoroutineScope` instances are not part of the DI graph today. If one becomes necessary (e.g. to host a long-running watcher), add a single `@AppScope` provider that wraps `SupervisorJob() + dispatcherProvider.default` so it is overridable in tests.

## Testing patterns

Personal Graph does not need a parallel test DI graph. Tests construct the unit under test directly, with kotest funspec + mockk:

```kotlin
class PersonalGraphIndexFirstNodeSearchServiceTest : FunSpec({
  val repository = mockk<VaultRepository>()
  val tokenEstimator = mockk<TokenEstimator>()
  val dispatcherProvider = TestDispatcherProvider()

  val service = PersonalGraphIndexFirstNodeSearchService(
    repository = repository,
    tokenEstimator = tokenEstimator,
    dispatcherProvider = dispatcherProvider,
  )

  test("returns ranked matches for a literal id query") {
    every { repository.findIndexEntries(any()) } returns fixture.entries
    service.search(query = "PG-3-onboarding").matches shouldBe expected
  }
})
```

Rules:

- never use `relaxed = true` (use `relaxUnitFun = true` if a unit-returning method needs to be silent)
- prefer constructing the real class with mocked collaborators over building a fake DI graph
- shared fixtures and helpers live in `core/testing`
- tests run via JUnit Platform; kotest provides the runner via `libs.kotest.runner.junit5`

## Scope rules summary

1. Tag long-lived stateful collaborators with `@AppScope` + `@Inject`.
2. Leave stateless cheap factories without a scope annotation.
3. Prefer constructor injection. No field injection, no `Provider<T>`.
4. Inject `DispatcherProvider`, never `Dispatchers.*`.
5. Library modules expose abstract `*Component` interfaces; entry-point modules compose them.
6. Tests construct components directly with mocks — no test DI graph.

## Troubleshooting

### "No matching binding"

Ensure the type is reachable from the entry-point component. If a binding lives in `core/data`, the entry-point component must implement `DataComponent` (and the binding must be declared on `DataComponent`).

### "Cannot find @Inject constructor"

The class needs `@Inject` on its primary constructor. KSP regenerates the kotlin-inject component code on every build — a stale generated file is rare but can be cleared with `./gradlew :module:clean`.

### Stateful instance is being recreated each call

Add `@AppScope` to the class. Without a scope annotation kotlin-inject creates a new instance per request.

## Further reading

- [kotlin-inject documentation](https://github.com/evant/kotlin-inject)
- [Build logic](./build-logic.md)
