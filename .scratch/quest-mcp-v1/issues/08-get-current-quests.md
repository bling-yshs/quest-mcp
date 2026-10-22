# 08: `get_current_quests`

**What to build:** A player asks "what should I do next?" and the agent gets the quests currently available or in progress, plus locked quests that are one step away. See spec "Tools".

**Blocked by:** 06

**Status:** done

- [x] `get_current_quests(limit?, offset?, lang?)` lists `IN_PROGRESS` then `AVAILABLE` quests, then locked quests whose every incomplete prerequisite is itself available or in progress
- [x] Each entry shows quest ID, title, chapter, status; the "one step away" entries name the blocking prerequisites
- [x] Paging per spec conventions with `N total, showing x–y` header
- [x] Hidden quests excluded when `revealHiddenQuests = false`
- [x] Endpoint tests with a fake `QuestSource` covering ordering, the one-step rule, hidden filtering, paging
- [ ] Manual: verify against real progress in `runClient` (complete a quest, re-query) — delegated to the user, per request

## Comments

- Preserves book order within each priority group. Locked quests with missing/unreadable prerequisites, no prerequisites, or no incomplete prerequisite are excluded: their one-step availability cannot be established from readable quest data.
- Reuses the same bounded paging schema and range header as search; language and visibility remain in QuestBookView.
- Three endpoint tests cover ordering, multiple blockers/completed dependencies, chained locks, hidden chapters/quests, reveal, language, paging and invalid bounds, game state and an all-completed book.
- `./gradlew --offline build` passed: 170 tests. No new dependencies or reflection. In-game verification remains with the user.
