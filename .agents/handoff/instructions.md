# Handoff — index

Use this folder to hand work cleanly between sessions, agents and Jordy.

- Feature work and the current phase → `feature-workflows.md`
- What's been tested (emulator and real tv) → `qa-handoff.md`

Do NOT load both unless your task needs both. Choose the one relevant to your current task.

## Read / Write contract
- **Read:** at the start of any task that continues prior work — load the one subfile matching your task.
- **Write:** before you finish or hand off — update that subfile so the next person/agent isn't lost.
- **When:** every session that changes state. A handoff file older than the work it describes is a bug.
- **How:** overwrite the live state (TL;DR, what's working/not, open questions). This file is *current state*, not history — stale lines get replaced, not appended.
