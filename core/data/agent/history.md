# core/data — history

## [2026-04-25] session-start-retrieval (GRP-3)
Areas: core/domain (SessionStartRetrievalService contract), core/data (retrieval engine), cli (session-start command), mcp-server (session_start tool), docs
- `SessionStartRetrievalService` owns Stage 3 retrieval: loads `Braian.md` first, classifies the first substantive message deterministically, then returns loaded branches/nodes/skips/audit as the shared CLI/MCP report contract (reusable retrieval boundary)
- `PersonalGraphSessionStartRetrievalService` uses existing `VaultPathResolver` containment and repository branch reads; retrieval policy is stricter than general reads: skip `people/`, skip all `staging/`, and exclude `emotional-states/` unless emotional/self-reflection terms match
- Classification terms route to `domains/work/capmo`, `domains/personal`, `domains/creative`, or durable state branches for `general`; emotional terms add `emotional-states/` without broadening other branches
- Pattern expansion follows wikilinks and `pattern_links` only under `patterns/`, de-dupes recursively, caps traversal at 64 pattern hubs, and audits missing/non-pattern links instead of failing retrieval
- CLI `session-start` and MCP `session_start` are thin adapters over the domain service; non-MCP usage is documented in `docs/session-start-retrieval.md`
Feature flag: N/A
Acceptance criteria: 8/8 implemented

## [2026-04-25] consolidation (GRP-2)
Areas: core/domain (ConsolidationService report contract, VaultRepository staging API, node metadata), core/data (consolidation engine, repository scan hardening, frontmatter mappers), cli (manual consolidate command), core/testing
- `PersonalGraphVaultConsolidationService` owns Stage 2 Tier-3 promotion: scans `staging/observations/`, groups by normalized claim/context, graduates repeated sightings, merges equivalent staged duplicates, and deletes only processed staged sources (reusable consolidation boundary)
- Pattern promotion uses >=2 domains or >=3 occurrences; include matched durable-node domains/counts when evaluating staged sightings, and resolve existing pattern ids with `repository.findNode()` before writes so capped scans cannot overwrite pattern metadata (pitfalls fixed)
- Contradictions annotate the durable target (`contradicted_by` + body note), report the changed durable node while keeping staged source ids, stay idempotent on repeat runs, and block contradictory staged sources from promotion (reusable report semantics)
- Repository branch scans now skip symlinked files before decode/read; `listStagedObservations()` only reads `staging/observations/`, and write/move/delete enforce `VaultPolicy.isWriteAllowed` to preserve Stage 1 path rules (reusable policy guard)
- Consolidation metadata (`occurrence_count`, `source_ids`, `pattern_links`, `contradicted_by`) round-trips through all node schemas; CLI reports counts plus changed ids via the production `personalGraphCli()` command tree
Feature flag: N/A
Acceptance criteria: 11/11 implemented

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
