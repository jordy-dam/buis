# AGENTS.md — root context for this repo

> This is the entry point. Read this file fully. Do **not** proactively read all of `.agents/`.

## What this repo is
A personal IPTV app for Google TV (Android TV OS): Xtream Codes login, Live TV / Films / Series,
favourites, household profiles and on-device recommendations. Sideloaded, one household, never published.

The full build spec — stack, Xtream API, design principles and the three phases with their
done-criteria — lives in `docs/spec/build-prompt.md`. Read it before any feature work.

## Golden rule: lazy loading
`.agents/` is a small knowledge base split by domain. **Load only what the current task needs.**

When a task involves one of these areas, first read that area's `instructions.md`, then load *only*
the one subfile the router points you to:

| If the task is about... | Start here |
|---|---|
| Getting oriented / first time in repo | `.agents/onboarding/instructions.md` |
| Handing work off (to a human or another agent) | `.agents/handoff/instructions.md` |
| Why something was decided | `.agents/decisions/instructions.md` |

These three are the **core**. Other areas (cross-tool memory, skills, agents) are
**trigger-activated**: they don't exist until a real need adds them. See `.agents/GUIDE.md` for
the trigger table and patterns. When you add one, add its row here too:

| Trigger-activated (add when needed) | Lives at |
|---|---|
| Shared memory across multiple AI tools | `.agents/memory/instructions.md` |
| A reusable capability | `.agents/skills/{name}/SKILL.md` |
| A scoped persona to delegate to | `.agents/agents/{name}.md` |

Do **not** read every file in a folder. The `instructions.md` is a router — follow it to the single
relevant subfile.

### Keeping context alive (read AND write)
These files are only useful if they stay current. Each folder's `instructions.md` (and `docs/overview.md`)
carries a **Read / Write contract** — read it when you enter that domain. The write-discipline differs by file type:

- **Current-state files** (`handoff/`) → *overwrite*. They describe now; stale lines get replaced.
- **History files** (`decisions/`) → *append-only*. Never edit the past; supersede with a new entry.
- **Tended files** (`memory/`, when present) → *prune*. Fix or remove facts that became false.
- **Stable config** (`skills/`, `agents/`, `onboarding/`) → *edit deliberately, between tasks*, not as a side effect.

Rule of thumb: if a session changed the state a file tracks, update that file before finishing.
A context file older than the work it describes is a bug, not just untidy.

## Project knowledge lives in `docs/`, not `.agents/`
`.agents/` = how the agent should **behave**. `docs/` = what's true about the **project**.
Same lazy-loading rule applies — load only what the task needs:

| If the task needs... | Read |
|---|---|
| What to build, stack, phases, done-criteria | `docs/spec/build-prompt.md` |
| A dated task plan | `docs/plans/{date}-{slug}.md` |
| External links (API docs, design guidelines) | `docs/resources.md` |
| Feature ideas and integrations (not yet planned) | `docs/brainstorm.md` |

Start at `docs/overview.md` if unsure.

## Project conventions
- Kotlin, Jetpack Compose for TV (`androidx.tv`), Media3 ExoPlayer, Room, Hilt, Coil. No Leanback,
  no WebView UI, no cross-platform framework. Don't swap the stack without logging a decision.
- Build & test: `./gradlew assembleDebug`, `./gradlew test`, `./gradlew lint`.
  Install on the tv: `./gradlew installDebug` (with `adb` connected, see onboarding).
- Build **only the current phase** from the spec. End each phase with a report: what works, how it
  was tested, known limitations, what Jordy must test on the real tv.
- Every screen must work with the d-pad only. Check focus and Back behaviour before calling a screen done.
- Never commit Xtream credentials, server URLs or real playlists. Develop against the mock Xtream server.
- No analytics, tracking, third-party crash reporting or cloud backend. Viewing history stays on the device.
- UI strings in Dutch; code, comments and commit messages in English.

## When a file gets big
If any single file here grows past ~200 lines, split it: turn it into a folder with its own
index file (`instructions.md` under `.agents/`, `overview.md` under `docs/`) + small focused subfiles.
