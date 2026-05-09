# Personal Graph Build Logic Guide

This document explains the Gradle convention plugins in `build-logic/convention`, what each one owns, and what should stay in module-local `build.gradle.kts` files.

## Goals

- keep module build files small and predictable
- centralize defaults that are correct for every JVM module
- avoid repeating KSP, test, and quality-gate wiring
- make it obvious which plugin to apply for a new module

## Selection guide

| Module type | Apply | Example |
| --- | --- | --- |
| Plain JVM library (Kotlin only) | `personalgraph.jvm.library` | `core/common`, `core/domain`, `core/data`, `core/testing` |
| Runnable JVM application (CLI / server entry point) | `personalgraph.application` | `cli`, `mcp-server` |

There is intentionally no Android, KMP, or Compose convention. Personal Graph is a pure JVM project (Kotlin 17 target) — the convention catalog stays small until a real second platform target is needed.

## Primary conventions

### `personalgraph.jvm.library`

Use for every Kotlin-only library module.

It owns:

- `org.jetbrains.kotlin.jvm`
- `com.google.devtools.ksp`
- `personalgraph.detekt`
- `personalgraph.spotless`
- `personalgraph.jacoco`
- Java toolchain at JVM 17 (source + target)
- `JvmTarget.JVM_17` and `allWarningsAsErrors = true` on the Kotlin compiler
- `useJUnitPlatform()` on every `Test` task

It intentionally does **not** own:

- module-specific runtime dependencies
- KSP processor wiring (e.g. `kotlin-inject` compiler) — modules opt in by adding `ksp(libs.kotlin.inject.compiler)` themselves
- `kotlinx.serialization` plugin — apply per-module when needed

### `personalgraph.application`

Use for runnable JVM entry points.

It owns:

- `personalgraph.jvm.library` (transitively brings detekt, spotless, jacoco, ksp, JVM 17)
- the Gradle `application` plugin
- relaxes `allWarningsAsErrors` to `false` for application modules so generated entry-point code does not block the warning gate

Module scripts still own:

- `application.mainClass` and `application.applicationName`
- runtime dependencies (e.g. `kotlinx.coroutines`, logging, MCP SDK)
- KSP processor for `kotlin-inject`

### `personalgraph.detekt`

Standalone static-analysis convention applied transitively by `personalgraph.jvm.library`. Owns Detekt configuration; module scripts should not configure Detekt directly.

### `personalgraph.spotless`

Formatter convention. Owns Spotless + the project-wide formatter (ktfmt). Apply transitively via `personalgraph.jvm.library`.

### `personalgraph.jacoco`

Coverage convention. Wires Jacoco against the standard `test` task. Apply transitively via `personalgraph.jvm.library`.

## What stays in module build files

Convention plugins should own defaults. Module scripts should own the parts that are truly local:

- module dependencies (`implementation`, `api`, `testImplementation`)
- KSP processor wiring for codegen frameworks the module uses (e.g. `ksp(libs.kotlin.inject.compiler)`)
- `kotlinx.serialization` plugin when the module needs JSON serialization
- application entry-point configuration
- module-specific test libraries

## Current examples

- `core/common/build.gradle.kts`: minimal JVM library with `kotlin-inject` runtime + KSP
- `core/domain/build.gradle.kts`: pure-Kotlin domain contracts and use cases, no framework deps
- `core/data/build.gradle.kts`: I/O-bearing implementations (filesystem, frontmatter codec, search/index services)
- `core/testing/build.gradle.kts`: shared test fixtures
- `mcp-server/build.gradle.kts`: `personalgraph.application` + MCP SDK + serialization + clikt
- `cli/build.gradle.kts`: `personalgraph.application` + clikt + `kotlin-inject`

## Anti-patterns to avoid

- re-declaring `useJUnitPlatform()` in module scripts
- redeclaring `JvmTarget.JVM_17` or the Java toolchain in module scripts
- applying `personalgraph.detekt`, `personalgraph.spotless`, or `personalgraph.jacoco` directly when the module already applies `personalgraph.jvm.library`
- keeping module-local copies of formatter/Detekt configuration
- adding fresh Android/KMP/Compose convention plugins before a real platform target exists

## Quality gate

`./gradlew check` runs the full gate (tests + Detekt + Spotless + Jacoco). All warnings are errors on library modules; fix at the root rather than suppressing.
