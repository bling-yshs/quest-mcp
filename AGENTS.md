## Coding rules

### Reflection

When a requirement can only be done cleanly with reflection (e.g. reading a private field in FTB Quests or JEI that has no public API), **stop and ask the user before using it**. State what is being accessed, why the public API isn't enough, and how fragile it is across versions.

Don't contort the code to avoid reflection either: when reflection is the right tool, propose it plainly and ask; don't build a roundabout workaround to dodge the question.

## Agent skills

### Issue tracker

Issues and specs live as local markdown under `.scratch/<feature>/`. See `docs/agents/issue-tracker.md`.

### Domain docs

Single-context: one `GLOSSARY.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.
