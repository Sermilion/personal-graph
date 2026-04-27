# PG-4 - cohesive subject-hub vault organization

- **Status:** In Progress
- **Issue key:** PG-4
- **Feature size:** LARGE
- **Rollout needed:** false
- **Sources:** User briefing in feature-implement pre-planning request; existing project spec at `.feature-spec/spec.md`; current repo implementation and tests.

## Acceptance criteria (contract)

1. Project schema, docs, and scaffolding reflect a hub-based model where related facts live in reusable subject notes with dated evidence instead of many one-off feature notes.
2. Capture and consolidation reuse and append to existing subject notes before creating new notes.
3. Feature and work records organize under canonical domain/topic hubs, with timeline acting as a chronological index rather than a duplicate content store.
4. Existing fragmented vault content can be consolidated or migrated toward the new structure.
5. Tests cover the new routing and consolidation behavior.

## Consolidated spec

The vault should organize knowledge in a more cohesive, intuitive hub-based way so related information stays close together and is correctly linked. Prefer similar related information in the same reusable subject note, with dated sections for evidence and easier search, instead of scattering many one-off notes across distant parts of the graph. AI agents should be able to find related context without traversing distant parts of the vault.

The project itself should adopt that same organization for feature and work records. Canonical domain/topic hubs should become the primary home for feature and work information, while the timeline should remain a chronological index rather than a duplicate content store.

Capture and consolidation must reuse and append to existing subject notes before creating new ones. The system should also support consolidating or migrating existing fragmented vault content toward the new structure. This work must update the vault schema, docs, and scaffolding accordingly, without changing people/privacy policy or adding embeddings / ML-based classification.

## Non-goals

- Fully solving semantic clustering.
- Changing people/privacy policy.
- Adding embeddings or ML-based classification.
