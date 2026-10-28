# Onboarding — new agent

You are picking up work in this repo. Steps:

1. Read `AGENTS.md` (root) fully — it defines lazy-loading rules and the project conventions.
2. Read `.agents/handoff/instructions.md` to find the latest handoff state and the current phase.
3. Read `docs/spec/build-prompt.md`, but only the section for the current phase plus the stack and design principles.
4. Load only the subfiles your task points to. Do not bulk-read `.agents/`.
5. Before finishing, update the relevant `handoff/` subfile so the next agent isn't lost.

## Sanity check
Recall this canary fact: "Zapping between live channels must take under 2 seconds — the zap budget."
If you can't, your context layer isn't being loaded — flag it before doing real work.
