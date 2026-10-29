# docs — project knowledge index

Project knowledge: what's true about *this project*. Humans and agents both read here.
(Contrast with `.agents/`, which holds agent *behavior* — how the agent should operate.)

- Build spec: stack, Xtream API, design principles, phases → `spec/build-prompt.md`
- Dated task plans → `plans/` (e.g. `2026-11-02-phase-1-plan.md`)
- External links registry → `resources.md`
- Open idea list (features, integrations; not a plan) → `brainstorm.md`

Trigger-activated (add when the project has it, see `.agents/GUIDE.md`):
- Architecture & domain knowledge → `wiki/`

## Read / Write contract
- **Read:** when a task needs project facts (requirements, architecture, plans).
- **Write:** when the project itself changes — new requirement, design shift, plan completed.
- **When:** as the project evolves. Unlike `.agents/` (stable behavior), `docs/` is expected to churn.
- **How:** plans are dated and append-style; the spec is a living doc you edit in place. Keep `resources.md` links alive — prune dead ones.
