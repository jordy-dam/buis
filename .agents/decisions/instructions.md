# Decisions — index

Why we chose what we chose. Each subfile is a running ADR-style log for one domain.

- Architecture / tech trade-offs → `architecture.md`
- Product / scope / UX → `product.md`

Do NOT load both files. Read only the domain your task touches.

## Entry format (append, never rewrite history)
```
## {date} — {short title}
**Decision:** {what we decided}
**Why:** {the reasoning / trade-off}
**Alternatives rejected:** {what we didn't do and why}
**Revisit if:** {condition that would reopen this}
```

## Read / Write contract
- **Read:** when a task touches a choice that might already be decided — check the relevant domain file first to avoid re-litigating.
- **Write:** the moment a non-trivial decision is made (architecture, scope). Append a new dated entry — never edit or delete past ones.
- **When:** at decision time, not "later". A decision not logged within the session is a decision lost.
- **How:** append-only, using the entry format above. History is immutable; if a decision is reversed, add a NEW entry that supersedes it and note it in the old one's "Revisit if".
