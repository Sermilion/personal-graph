# personal-graph stage tracker

Source of truth for cross-stage progress. The full vision lives in [`/.feature-spec/spec.md`](../.feature-spec/spec.md); each per-stage extract lives under `.feature-specs/<issue-key>-stage-<n>-<slug>/spec.md` and tracks its own status independently.

| Stage | Issue | Per-stage spec | Status |
| ----- | ----- | -------------- | ------ |
| 1 — Vault + capture (MVP) | GRP-1 | [`GRP-1-stage-1-vault-and-capture/spec.md`](./GRP-1-stage-1-vault-and-capture/spec.md) | Complete |
| 2 — Consolidation | GRP-2 | [`GRP-2-consolidation/spec.md`](./GRP-2-consolidation/spec.md) | Complete |
| 3 — Session-start retrieval | TBD | _not started_ | Not started |
| 4 — Proactive surfacing | TBD | _not started_ | Not started |

Stage 1 ships the directory scaffolder, the seven Stage 1 MCP capture tools, and the markdown + frontmatter round-trip layer used by every later stage. Stage 2 adds the manual consolidation CLI that promotes repeated staged observations, merges equivalent staged duplicates, extracts pattern hubs, and annotates contradictions while skipping `staging/sensitive/` and `people/` by default. Stages 3-4 build on that storage layer; their per-stage specs will be drafted as each stage starts.
