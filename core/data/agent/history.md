# core/data — history

## [2026-04-30] map-first-retrieval-engine (PG-5)
Areas: core/domain (retrieval DTOs + repository preview contract), core/data (session-start retrieval, codec, repository previews), mcp-server (schema/formatter contract), cli, core/testing
- Default `session_start` is now genuinely map-first: planned broad state/domain branches return compact map entries and leave `loadedNodes` empty; `FullLoading` remains the explicit opt-in path for full node bodies
- Repository now exposes `listMapNodesInBranch(branchPath, bodyWordLimit)` and `MarkdownFrontmatterCodec.decodePreview(...)` so map-first retrieval can derive summaries/links from bounded body previews instead of hydrating complete branch bodies
- Compact map entries and suggested reads carry rich navigation metadata: domain/category/scope/scopes, created/updated/date, summary/excerpt, aliases/terms, links/pattern links, and cheap backlink counts computed from the mapped node set (not repeated full-vault backlink scans)
- Suggestion scoring is deterministic: subject hubs rank ahead of raw events by default, recent/evidence/timeline prompts can lift events, global state remains eligible across domains, and scoped state is limited to matching classified domains
- MCP JSON omits absent optional metadata instead of serializing fake defaults; CLI prints the rich map/suggested-read fields; tests lock map shape, budget, full-loading opt-in, scoped filtering, ranking, audit reasons, and preview-body bounds
Feature flag: N/A
Acceptance criteria: 9/9 implemented

## [2026-04-30] contract-and-scoped-state-foundation (PG-5)
Areas: core/domain (retrieval + capture contracts), core/data (retrieval, capture, codec, mappers), mcp-server (schemas/parsers/formatters), cli, core/testing
- `SessionStartRetrievalService` now has an explicit `SessionStartRetrievalMode`: default `MapFirst` keeps full-body context to `Braian.md` only, while `FullLoading` is the opt-in path for loaded node bodies; compatibility fields remain while new map-first fields are introduced
- Active retrieval domains expanded to `work/capmo`, `work/skill-bill`, `work/readian`, `work/context-app`, `creative/music`, `personal`, and `general`; Capmo matching is now product-specific so generic `work` does not steal explicit project/domain prompts
- `StateNode` carries optional `scope` plus plural `scopes`; broad state branch retrieval includes global state and filters scoped state by classified domain, with `General` excluding scoped state
- State frontmatter uses state-specific YAML encoding with absent scope metadata omitted, preserving existing files while round-tripping singular/plural scoped metadata
- Capture and MCP write paths now persist scoped state metadata; malformed `scope`, `scopes`, and `retrieval_mode` JSON shapes reject instead of silently defaulting
Feature flag: N/A
Acceptance criteria: 7/7 implemented

## [2026-04-27] cohesive-subject-hub-vault-organization (PG-4)
Areas: core/data (subject hubs, capture, consolidation, repository), core/domain (layout/contracts), cli (consolidate reporting), mcp-server (tool schema/formatters), docs/tests
- Added first-class `SubjectNode` markdown/frontmatter support plus canonical `domains/.../subjects/` routing so related feature/work context can accumulate in reusable hub notes with dated evidence instead of one-off files
- `PersonalGraphVaultCaptureService` now upserts subject hubs on episode capture, appends evidence before creating siblings, and keeps timeline entries as index stubs that link out to the episode + hub; compatibility pitfall: timeline ids must stay keyed by topic slug, not episode id suffix
- Consolidation now migrates legacy `domains/.../notes/...` content into canonical subject hubs and reports migrated-note counts through the CLI/MCP surfaces (reusable migration pattern)
- Followed existing boundary pattern: `VaultLayout` remains the source of truth for branch paths, repository/path safety stays in `core/data`, and CLI/MCP stay thin over domain/data behavior
Feature flag: N/A
Acceptance criteria: 5/5 implemented

## [2026-04-25] capture-retrieval-classifier-fixes (PG-1)
Areas: core/data (retrieval classifier + capture id rejection), mcp-server (schema description sweep, `ToolSchemaProperties.description` optional param), core/data tests
- `PersonalGraphSessionStartRetrievalService.classify` now picks the highest-match-count domain across Work/Personal/Creative; `maxByOrNull` keeps the first tied candidate, so list order Work>Personal>Creative is the deterministic tiebreak (was first-non-empty, which silently lost recall when multiple domains had matches)
- Term-boundary regex is `(?i)(?<![a-z0-9_-])$escaped(?![a-z0-9_-])` — `-` and `_` are now part of a token, so compound names like `personal-graph` don't leak constituent matches; intentional trade-off: hyphenated discrete words (`co-design`) classify as `General` (revert reintroduces the leak)
- `branchPlanFor` always loads `state/preferences` and `state/roles`; `state/knowledge` only on `General`; declarative concatenation with named `REASON_*` constants — no mutation (reusable for future durable-state branches)
- `PersonalGraphVaultCaptureService.rejectSingularStatePrefix` runs unconditionally before sensitive routing, trims+lowercases ids, returns `CaptureResult.InvalidInput.expected` carrying the canonical plural form (reuses the existing `expected` slot — same shape as payload-kind mismatch in `validateExistingForFlag`)
- `SINGULAR_STATE_PREFIX_REJECTIONS` is **derived from `StateCategory.entries`** so future categories cannot drift; `Knowledge` is auto-filtered (singular == canonical); `Fact` maps to `state/knowledge/` (reusable pattern for enum-derived rejection sets)
- `ToolSchemaProperties.string()/boolean()/stringArray()/enum()` now accept an optional `description` param; new `DESC_FIELD_*` constants in `ToolSchemas` carry per-field rules — split `DESC_FIELD_STATE_ID` vs `DESC_FIELD_NODE_ID` so each tool's id description reflects its own validation behavior (reusable pattern for any future schema field)
- Schema descriptions must stay faithful to runtime behavior — `DESC_FIELD_LINKS` was reworded to disclose silent-drop in `toNodeIds()` rather than overpromise canonical enforcement; pitfall: don't aspire in description text
Feature flag: N/A
Acceptance criteria: 8/8 implemented

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
