# core/data — history

## [2026-05-08] scoped-session-start-map (PG-7)
Areas: core/data (session-start retrieval + graph index repository), core/domain (graph index contract), cli tests
- `session_start` MapFirst now classifies project prompts into scoped branch plans and builds the default map from bounded graph-index entries instead of hydrating full branch bodies.
- Added `GraphIndexBranchQuery` with per-branch limits and preferred relative prefixes so hot paths can request top-K previews without relying on repository-wide caps.
- Scoped state maps reserve essential global preferences before branch quotas, preventing large project-specific state sets from evicting cross-project defaults.
- Full-loading keeps the prior body/linked-pattern behavior as the explicit opt-in path; follow-up actions still point agents to `search_nodes`, `list_branch(mode=index)`, and `read_node`.
- Regression tests cover crowded event folders, scoped preference overflow, bounded query usage, token reduction versus full-loading, and symlink rejection inside planned branches.
Feature flag: N/A
Acceptance criteria: 5/5 implemented

## [2026-05-04] session-start actions and token accounting (PG-6 subtask 3/4)
Areas: core/data (session-start retrieval service + suggestion/token helpers), core/domain (session-start report contract), mcp-server (session_start formatter + schema descriptions), cli/docs
- `session_start` now emits `suggested_actions` alongside `suggested_reads`; identifier-like prompts route to `search_nodes` first, while broader prompts also suggest `list_branch(mode=index)` before any full-body branch read (reusable search-first retrieval pattern)
- Response token accounting is now surfaced as `estimated_tokens` with response_total/metadata/body/pruned_body counts, computed deterministically from the loaded contexts, map entries, actions, and audit trail (reusable for any future response-shaped retrieval surface)
- CLI output and docs now explain the search-first path and SKILL-33-style example, keeping the default retrieval workflow aligned with the MCP contract
Feature flag: N/A
Acceptance criteria: pending final validation

## [2026-05-09] traversal-foundation-finalized (PG-6 subtask 1)
Areas: core/domain/search, core/data/search, core/data/di, core/domain/graph, core/domain/tokens
- `TraverseGraphService` is the reusable domain/data boundary for traversal; MCP/session_start/read_node wiring stays intentionally out of scope for this subtask.
- Exact path lookups are branch-warm/retry aware and score as exact even when start ids already fill the entrypoint cap; future path-style retrieval should not let side-map coldness or entrypoint caps hide exact nodes.
- Backlinks are opt-in by default and built from warmed `GraphIndexEntry.links`, not per-node `VaultRepository.listBacklinks`; explicit backlink traversal warms scoped branches once and reuses the reverse map.
- Token budgeting accounts for diagnostics (`entrypoints`, `pruned`, `suggestedReads`) as well as nodes/edges; body hydration is gated by indexed body estimates before reading full bodies.
- Tests now lock service-level direct-evidence ranking, concrete edge weights, policy-hidden outputs, path cold-start behavior, generated DI binding, and body/budget pruning.
Feature flag: N/A
Acceptance criteria: 7/7 implemented

## [2026-05-04] traversal-foundation (PG-6 subtask 1)
Areas: core/domain (TraverseGraphService + TraverseGraphQuery/Outcome models), core/data (PersonalGraphTraverseGraphService + traversal helpers + DataSearchComponent binding)
- `traverse_graph` foundation is domain/data only: request accepts query/startIds/branches/edgeTypes/maxDepth/maxNodes/budgetTokens/includeBodies/rankBy, result returns entrypoints, scored nodes, weighted/labeled edges, pruned, suggestedReads, and estimatedTokens; MCP parser/formatter wiring intentionally remains deferred.
- Traversal uses warmed `GraphIndexRepository.listEntriesInBranch` as the policy/source-of-truth boundary: allowed entries are resolved from warmed branch entries, `VaultPolicy.isReadAllowed && !isIndexExcluded` gates branches/ids/links before output, and `people/` + `staging/sensitive/` never reach nodes/edges/pruned/suggested/token accounting.
- Backlinks are derived from a one-pass reverse-link map over warmed index entries only when `Backlink` is requested and `maxDepth > 0`; avoid per-node `VaultRepository.listBacklinks` scans in retrieval hot paths.
- Budgeting is edge-aware and body-aware: select metadata candidates first, hydrate bodies only for included nodes, then trim with endpoint-indexed edge costs so final `estimatedTokens` stays within `budgetTokens` without O(nodes*edges) rescans.
- Detekt forced traversal helpers into focused support files (`TraverseGraphCandidateSupport`, `TraversalSelectionBuilder`, token/ranking/service support); keep future traversal growth split by concern instead of expanding the service class.
Feature flag: N/A
Acceptance criteria: 7/7 implemented

## [2026-05-03] search-and-list-branch-upgrade (PG-6 subtask 2 of 4)
Areas: core/domain (NodeSearchService + BranchListingService contracts, SearchModels with SearchRankingTier + SearchRecency triggers), core/data (PersonalGraphIndexFirstNodeSearchService + PersonalGraphBranchListingService impls), mcp-server (search_nodes added end-to-end; list_branch extended with mode/filter/limit/include_links/include_body)
- Index-first `search_nodes` consults `GraphIndexRepository` for id/path/alias/title/subject/topic/hypothesis tiers before any body decode; body fallback runs only when metadata matches are empty AND `body_fallback=true` AND `body` is in `search_fields` (default `[id,metadata,body]`); cold-branch caveat from subtask 1 handled by `effectiveBranches` warming via `listEntriesInBranch` before per-tier lookups (reusable: any future tool wanting metadata-only retrieval can compose this same warming pattern)
- Ranking lives behind `SearchRankingTier` (ExactFullIdOrPath=100, LeafIdOrSlug=80, SubjectTopicAliasHypothesis=60, DomainOrBranchRelevance=40, BodyMention=20) so a tier reshuffle is a single-file edit; `SearchRecency.TRIGGERS = {recent, latest, today, merged, opened, status}` adds +5 boost AND drives `stripRecencyTriggers(query)` so id-lookups still hit when the query embeds a trigger word (pitfall the original impl tripped on: `editor-indent latest` would not id-match without stripping)
- New `BranchListingService` owns the `list_branch` mode-aware workflow (full vs index entries, substring filter, limit, includeLinks/includeBody, compact-entry projection, token accounting via `TokenEstimator`); MCP tool layer is now thin (parse → permission gate → service → format), with NO per-result VaultPolicy re-filtering — the service is the single source of truth for `isReadBlocked`/`isIndexExcluded` (reusable pattern: keep policy enforcement in the domain service, never duplicate it in the transport handler)
- Backward-compat for default `list_branch(branch=...)` invocation is locked at the parser: `ListBranchArgs.legacyShape=true` when no new keys are present, and the formatter returns the byte-identical legacy `{status, nodes}` shape; presence of `mode` (even `mode=full`) or any other new key flips to the extended `{status, mode, nodes, estimated_tokens}` schema (pitfall to avoid: do NOT inspect raw `JsonObject` keys from the tool layer — keep the legacy decision in the parser so the tool never sees the original args)
- Schema descriptions are tool-specific: `DESC_FIELD_SEARCH_BRANCHES/SEARCH_LIMIT/SEARCH_INCLUDE_BODY` for `search_nodes`, `DESC_FIELD_LIST_*` for `list_branch` — reusing list_branch descriptors for search teaches AI clients the wrong defaults (subtask-1 history flagged "descriptions must reflect runtime, not aspirational"; this entry locks that as a per-tool concern, not just per-field)
- Permission_denied symmetry: `searchNodesGate` mirrors `listBranchGate` — explicit caller-supplied branches that hit `isReadBlocked` or `isIndexExcluded` return `permission_denied` up front; empty branches list (caller didn't specify) flows to defaults without rejection (reusable: every retrieval tool that takes `branches: string[]` should run this gate)
- Negative `limit` rejected at the parser via `nonNegativeIntArgument` (returns `Parsed.Failure(KEY_LIMIT, REASON_INVALID)`) so `List.take(-1)` can never crash either tool — generalize this helper for any future int-bounded MCP arg
- Detekt friction recurred (TooManyFunctions=20 + LongParameterList + LargeClass): split tool helpers into `VaultMcpJsonArgs` / `VaultMcpListBranchFormatters` / `VaultMcpRetrievalFormatters` / `VaultMcpSearchArgsParsers`; split `DataComponent` interface into `DataComponent` + `DataSearchComponent`; split `VaultMcpToolsTest` into a dedicated `VaultMcpToolsSearchAndListBranchTest`; introduced `StateEntrySpec` data class to collapse a 7-arg test helper — repeat this split-by-domain pattern when a tool layer grows past the limit
Feature flag: N/A
Acceptance criteria: 9/9 implemented

## [2026-05-03] graph-index-and-token-foundation (PG-6 subtask 1 of 4)
Areas: core/domain (GraphIndexRepository + GraphIndexInvalidator contracts, GraphIndexEntry model, TokenEstimator, VaultPolicy index guard), core/data (PersonalGraphGraphIndexRepository + PersonalGraphVaultRepository invalidation hooks + DataComponent providers), core/testing (NoOpGraphIndexInvalidator), mcp-server (DI exposure only — no tool surface change)
- New `GraphIndexRepository` is the compact, lazily-built index over the vault: `listEntriesInBranch` / `findEntry` / `findEntryByAlias|Title|Path` populate per-branch state on demand, never throw, return null/empty on miss; cold lookups against a never-listed branch return null by design (documented in KDoc) — callers needing exhaustive coverage must list-first (reusable for subtask 2's `search_nodes` index-first path)
- `GraphIndexInvalidator` is a separate domain interface so `PersonalGraphVaultRepository` depends on the invalidation contract, not on the data-layer index impl — breaks the bidirectional dep cleanly (reusable pattern when wiring caches that observe writes)
- `PersonalGraphGraphIndexRepository` is `@AppScope` and implements both contracts so kotlin-inject yields a single shared instance for both `@Provides` bindings (DI identity invariant)
- Cache invariants: per-file key is `(fileSize, fileModifiedAtMillis)`; branch-root mtime drop runs at the start of `listEntriesInBranch` and prunes only entries whose `branch == prefix || startsWith("$prefix/")` (proper prefix match, not raw `startsWith`); `invalidate(id)` removes from main cache and prunes alias/title/path side maps; side-map mutations are guarded by `synchronized(sideMapLock)` so concurrent finders never observe a transiently-empty map
- Lazy build reuses `MarkdownFrontmatterCodec.decodePreview` with `BODY_PREVIEW_WORD_LIMIT=64` — no full-body decode for the index path; snippets capped at `MAX_SNIPPET_LENGTH=200` chars; oversized files (>1 MiB) are skipped at debug log so the reader can tell why a known node is missing (reusable bounded-decode pattern)
- Safety: `VaultPolicy.INDEX_HARD_EXCLUDED_BRANCH_PREFIXES = {VaultLayout.BRANCH_PEOPLE, VaultLayout.BRANCH_STAGING_SENSITIVE}` (constants from `VaultLayout`, no hardcoded strings); `isIndexExcluded` is independent of `isReadAllowed`/`isReadBlocked` so even if `VaultPolicy` later opens `staging/` reads for some path, the index still hard-excludes `staging/sensitive/`; checks fire at branch entry, per-file, and per-link layers; `linkCount` reflects the post-filter count so blocked link targets never leak via metadata
- Title-key fallback to leaf segment is intentionally NOT used — `titleIndex` is populated only when an entry has `subject` or `topic`, avoiding leaf-name collisions across branches (e.g. `state/preferences/sample` vs `state/roles/sample`)
- Move-hook in `PersonalGraphVaultRepository` invalidates **both** the original and the post-move id (`movedNodeId` reconstructs the new id; explicit try/catch returning nullable, NOT `runCatching` — `kotlin.Result` is banned project-wide); invalidation fires only on `WriteOutcome.Applied` for write/move/delete (pitfall: do NOT invalidate on Failed/NotFound/Conflict — would thrash the cache)
- `TokenEstimator` is a stateless `object` (no `@Inject` since core/domain has no kotlin-inject annotations on existing helpers) wired via explicit `@Provides fun provideTokenEstimator()`; deterministic ceiling-divide with documented `CHARS_PER_TOKEN=4`; `estimateMetadata` and `estimateBody` are typed wrappers around `estimateString` so future callers can specialize without churn (reusable for every MCP response that needs `estimated_tokens`)
- Detekt friction (foundation pattern for index-style helpers): `ReturnCount=3` and `TooManyFunctions=20` force aggressive helper extraction + elvis chains; refactored `buildOrCacheFromFile` into `eligibleNodeIdOrNull` / `statOrNull` / `readRawOrNull` / `decodeAndCache`; collapsed try/catch returns into a `when` expression; reused this shape elsewhere — reusable when adding any other multi-step nullable pipeline in this module
- Test scaffolding: `NoOpGraphIndexInvalidator` lives in `core/testing` so any service test that constructs `PersonalGraphVaultRepository` directly can satisfy the new ctor param without re-declaring an anonymous object (pitfall: forgetting this breaks four pre-existing tests at compile time)
- mcp-server consumers are NOT wired this subtask — `McpServerComponent` exposes `graphIndexRepository` and `tokenEstimator` as `abstract val` so subtask 2 (`search_nodes` index-first / `list_branch(mode=index)`) can wire MCP tools without re-touching DI
Feature flag: N/A
Acceptance criteria: 7/7 implemented (subtask AC1-AC6 plus AC7 `./gradlew check` pass)

## [2026-05-03] search-first-graph-traversal-mcp
Areas: core/domain (repository contract), core/data (ranked search), mcp-server schemas/tools, README/docs/tests
- Added `search_nodes` so agents can find exact ids, metadata, and body snippets without using `list_branch` as a branch-wide grep
- Added `traverse_graph` so agents can start from a query or explicit node ids and collect a bounded link/backlink subgraph before deciding which full bodies to read
- Search and traversal responses default to compact metadata/snippets/links, keep full bodies behind `include_body=true`, and cap limits/depth to keep prompt use bounded
Feature flag: N/A
Acceptance criteria: search-first graph lookup path implemented

## [2026-05-03] session-start-index-only-map-first
Areas: core/data (retrieval map/suggestions), mcp-server schemas, docs/tests
- Default `MapFirst` retrieval now returns `available_map` as branch index entries only; node summaries, aliases, links, and node-level map entries are reserved for explicit `FullLoading`
- Default `suggested_reads` can point to branch ids so agents can call `list_branch` only when they need deeper context
- Map-first no longer expands linked pattern hubs during initial load; pattern expansion remains on the explicit full-loading path
- MCP `session_start` no longer emits duplicate `audit_entries`; `audit` is the canonical audit array
- Documentation and MCP schema text now describe the default response as index-only to keep initial prompt/token use bounded
Feature flag: N/A
Acceptance criteria: initial load token reduction implemented

## [2026-04-30] vault-navigation-note-generation-rules (GP-6)
Areas: core/domain (layout), core/data (scaffold/capture/tests), mcp-server schemas, README, external Obsidian vault
- Vault scaffolding now creates idempotent `domains/**/index.md` navigation notes from a central active-domain list while preserving user-authored index files.
- Capture ID construction now uses a central slug policy: generated observation IDs, subject hubs, and timeline backlink leaves stay word-bounded; bare caller leaves are slugified without word bounding; explicit canonical state/episode paths remain caller-owned.
- Episode subject hubs filter their own hub id out of link metadata on create/append, and timeline backlink bodies remain link-only stubs to avoid duplicated event prose.
- Candidate capture now stages event-like observations (`decision`/`fix`/`regression`/`chose`/`implemented`) unless they have durable reusable state signals or complete episode shape.
Feature flag: N/A
Acceptance criteria: 7/7 implemented

## [2026-04-30] capture-replacement-archive (PG-5 follow-up)
Areas: core/domain (layout/policy/capture result), core/data (capture archive/scaffold/tests), mcp-server (write result JSON/schema docs), README
- Capture writes now preserve previous versions when replacing the same graph path by writing an archive copy under `outdated/resolved/<original-path>/<timestamp-hash>` before overwriting the active node
- MCP write results expose `archived_paths` so agents can see which stale memory versions were resolved by the new write
- `outdated/resolved/` is scaffolded and read/write allowed, but `staging/sensitive/` replacements are intentionally not copied there to avoid moving sensitive content into a readable archive branch
- Archive bodies keep the original content with a short header naming the original path, archive time, and superseding path; the active graph path still contains only the new body
Feature flag: N/A
Acceptance criteria: follow-up implemented

## [2026-04-30] map-first-adapter-contract-tests (PG-5)
Areas: core/domain (retrieval report), core/data (map-first output), mcp-server (session_start JSON/schema), cli (session-start output), docs/tests
- `SessionStartRetrievalReport` now exposes canonical `loadedContext`, `availableMap`, and `suggestedReads`; old loaded-branch/node fields remain in-process compatibility only, while MCP default output omits broad body fields
- `PersonalGraphSessionStartRetrievalService` builds relevance-ranked `availableMap` before applying the 80-entry cap so classified subject hubs/scoped state survive crowded global state; `suggestedReads` stays capped at 8
- Default map entries filter `people/` and all `staging/` link targets to avoid leaking blocked ids through compact metadata; full bodies stay behind explicit `read_node`, `list_branch`, or `full-loading`
- Repository preview support from the map-first engine branch adds `listMapNodesInBranch(...)` and `decodePreview(...)`, so future map construction can derive bounded summaries/links without hydrating full branch bodies
- CLI `session-start` now has labeled sections and prints bounded `loaded_context` bodies between begin/end markers for non-MCP prompt preambles
- Split retrieval mapping/suggestion helpers out of the service file to keep detekt thresholds satisfied as map metadata grows
Feature flag: N/A
Acceptance criteria: 6/6 implemented

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
