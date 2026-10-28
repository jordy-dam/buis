# Extending `.agents/` — add on trigger, not "just in case"

This repo ships with a **minimal core**: `onboarding/`, `handoff/`, `decisions/`.
Everything else is **trigger-activated** — you add it the moment a real need shows up, never before.
An empty folder waiting for content is bloat. A named trigger is a decision rule.

## The trigger table

| Add this | When (the trigger) | Why not by default |
|---|---|---|
| `.agents/memory/` | You work this project across **more than one AI tool** (e.g. Windsurf + Claude + Gemini) and need shared memory none of them owns | A single-tool project should use that tool's native memory — duplicating it here just drifts |
| `.agents/skills/{name}/` | You have a **repeatable task** worth encoding once and reusing | A skill with no repeatable task is ceremony |
| `.agents/agents/{name}.md` | You **delegate** a chunk of work to a scoped persona (reviewer, test-writer) | Most repos never define custom subagents |
| `docs/spec/`, `docs/wiki/` | The project has **real specs / architecture** worth writing down | Small projects don't have these yet |
| Split a file into a folder | A single file crosses **~200 lines** or clearly spans sub-topics | Flat is simpler until it isn't |

Rule for all of them: name the trigger, wait for it, then add. Update AGENTS.md's router when you do.

---

## Pattern: cross-tool memory (`.agents/memory/`)

**Trigger:** more than one AI tool on this project.

Create `.agents/memory/instructions.md` as the router, plus focused files:
- `long-term.md` — durable facts that span sessions
- `troubleshooting.md` — solved gotchas worth keeping
- `.local/` — actual sensitive values; **gitignored, never committed**

Read/Write contract to paste into `instructions.md`:
```
## Read / Write contract
- Read: when a task needs durable context — load the matching file.
- Write: when you learn something that matters next session.
- When: at the moment of learning, while fresh.
- How: prune. Memory is a tended garden, not a log. Sensitive values → .local/ only.
```
Add to `.gitignore`: `.agents/memory/.local/` (keep a `.gitkeep`).
Add a router row to AGENTS.md: `What to remember across sessions | .agents/memory/instructions.md`.

---

## Pattern: skills (`.agents/skills/{name}/`)

**Trigger:** a repeatable task. One folder per skill, `SKILL.md` as a thin router; heavy detail in `references/`.

```
.agents/skills/{name}/
  SKILL.md            # frontmatter: name + description; when-to-use; steps
  references/         # long checklists/specs, loaded only when a step points there
```
Multi-tool note: skills here are the source of truth, loaded **on demand** via AGENTS.md — this works
in every tool. To make one *auto-fire* in a specific tool, mirror it into that tool's discovery dir
(Claude Code: `.claude/skills/`) — opt-in, per tool, never the source of truth. See onboarding.

Read/Write contract for the SKILL.md:
```
## Read / Write contract
- Read: load when a task matches the description; pull references/ only when a step says to.
- Write: improve deliberately, between tasks — not as a side effect of other work.
```

---

## Pattern: agents (`.agents/agents/{name}.md`)

**Trigger:** you delegate to a scoped persona. One file per agent.

```
---
name: {name}
description: scoped persona the main agent delegates to
tools: [Read, Grep]   # least-privilege
---
# {name}
## Role / When to invoke / Prompt
```
Same multi-tool rule as skills: definition lives here, mirror into a tool's dir only to make it
natively dispatchable. Edit deliberately, between tasks.

---

## Pattern: splitting a file that got big

**Trigger:** file > ~200 lines or spans distinct sub-topics.

1. Turn `foo.md` into a folder `foo/`.
2. Add `foo/instructions.md` (behavior) or `foo/overview.md` (knowledge) as the router.
3. Move detail into small focused subfiles the router points to.
4. Keep the router thin; push detail to leaves.

---

## The one rule behind all of these
`.agents/` describes **how the agent behaves**; `docs/` holds **what's true about the project**.
Add a folder only when there's real content and a real trigger. Name the trigger; don't pre-build.
