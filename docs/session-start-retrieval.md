# Session-Start Retrieval

Stage 3 gives agents a deterministic way to load useful prior context before a
conversation starts.

## Protocol

At session start:

1. Load `Braian.md` first.
2. Classify the first substantive user message.
3. Load the matching domain subtree.
4. Load pattern hubs linked from the loaded nodes.
5. Report the classification, loaded branches, loaded nodes, skipped branches,
   and audit reasons.

The default retrieval policy skips `people/`, skips `staging/` including
`staging/sensitive/`, and excludes `emotional-states/` unless the first message
explicitly asks about emotional context or self-reflection.

## CLI Wrapper

Non-MCP tools can call the CLI before sending the first turn to the model:

```bash
personal-graph-cli session-start --vault /absolute/path/to/vault "first user message"
```

Use the report as the session preamble. Include the `root`, `node`, and `audit`
lines that are relevant to the conversation; do not load skipped branches.

## Non-MCP Prompt Snippet

```text
Before answering the user, run:

personal-graph-cli session-start --vault <vault> "<first substantive user message>"

Read Braian.md first from the report, then the loaded domain nodes, then linked
pattern nodes. Treat the audit lines as the explanation of why each branch was
loaded. Do not read people/, staging/, or staging/sensitive/. Do not read
emotional-states/ unless the report says emotional_context=true.
```
