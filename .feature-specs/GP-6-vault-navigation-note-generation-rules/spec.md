# GP-6: vault-navigation-note-generation-rules

Status: Complete

## Sources

- User-provided feature brief in the Codex session on 2026-04-30.
- Vault audit findings for `/Users/sermilion/Documents/Documents - Braian's Mac mini/Obsidian/Braian LLM`.

## Consolidated Spec

Resolve navigation and note-generation issues found in the Obsidian personal-graph vault, then update the personal-graph runtime so future note creation accounts for those findings.

The vault is generally well organized, with `Braian.md`, `domains/`, `state/`, `timeline/`, `staging/`, `outdated/`, and `people/`.

Current findings:

- Major domains lack per-domain index/readme notes.
- `domains/personal-graph` and `domains/work/personal-graph` are confusingly ambiguous.
- `timeline/` notes can duplicate search results, but should remain backlink-only stubs.
- Runtime generated slugs can be very long.
- Two subject notes self-link.
- `state/knowledge` can become a noisy everything-important bucket and should bias toward durable reusable knowledge instead of event summaries.

## Acceptance Criteria

1. Clean current vault navigation issues: add domain indexes, remove subject self-links, and reduce confusing domain ambiguity.
2. Keep `timeline/` as backlink-only index content, not duplicated event content.
3. Update personal-graph runtime so future notes avoid long slugs where the runtime generates IDs.
4. Prevent future subject hubs from self-linking.
5. Bias `state/knowledge` toward durable reusable knowledge; event-like observations should become episodes or staging candidates instead of permanent knowledge notes.
6. Add tests covering the new runtime behavior.
7. Validate the vault and run repo checks.

## Non-Goals

- No sensitive or `people/` content inspection.
- No broad redesign of the vault schema.
- No GitHub PR or commit unless explicitly requested later.

## Rollout

No feature flag or rollout gate is needed.
