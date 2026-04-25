# core/data — history

## [2026-04-25] stage-1-vault-and-capture (GRP-1)
Areas: core/domain (VaultLayout, VaultPolicy, VaultCaptureService, VaultScaffolder), core/data (codec, repository, scaffolder, capture impl, mappers), core/testing (TestDispatcherProvider, four node fixtures), mcp-server (7 stdio tools, ToolSchemaBuilder, runtime), cli (init command)
- Vault scaffolder + Braian.md seed wired through `personal-graph init --vault`; idempotent (Files.notExists guard preserves existing seed content) (reusable)
- Per-type `*FrontmatterDataModel` (kaml @SerialName mapping spec snake_case keys: `episode_type`, `evidence_count`, `last_observed`, `domains_seen_in`, `contradicted_by`, `trigger_hypothesis`) + `MarkdownFrontmatterCodec` encode/decode; never throws (returns null on malformed) (reusable for all future read/write paths)
- `VaultLayout` + `VaultPolicy` (core/domain) are the single source of truth for branch names and read/write authz; scaffolder, VaultBranches, ToolSchemas all derive their constants from VaultLayout (reusable; will own future Stage 2/3 layout knowledge)
- `VaultCaptureService` (core/domain) + `PersonalGraphVaultCaptureService` (core/data) own capture orchestration (id construction, slug, payload-kind validation, sensitive routing via `repository.moveNode`, timeline backlink construction); MCP transport is now a thin JSON adapter — CLI capture path can reuse the same service (reusable)
- `PersonalGraphVaultRepository`: atomic temp + Files.move(ATOMIC_MOVE) with AtomicMoveNotSupportedException fallback; POSIX 0600/0700 perms when supported; symlink-aware vault containment via `VaultPathResolver` (refuses any tail segment that is a symlink); bounded listBacklinks/listNodesInBranch with maxDepth + result cap + currentCoroutineContext().ensureActive(); read-blocked branches (people/) defended at the repository tier (reusable)
- MCP tools advertise typed `ToolSchema(properties, required, enum)` per tool via `ToolSchemaBuilder`; `STATUS_OK` is the only success status surfaced to MCP clients (every other status routes through `CallToolResult.error`) — pattern reusable for future tool additions
- `list_pending_sensitive` returns id-only by default; `include_excerpts=true` requires a `staging/sensitive/.consent` marker file (consent flow reusable for any future sensitive-data tool)
- Pattern + emotional-state node *write* tools deferred (schemas preserved in domain models); consolidation, session-start retrieval, and proactive surfacing are out-of-scope for this stage (Stages 2/3/4)
Feature flag: N/A
Acceptance criteria: 11/11 implemented
