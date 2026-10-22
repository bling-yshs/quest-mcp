# 07: `search_quests` full-text search

**What to build:** A player asks "how do I make a snowball?" and the agent finds the quest whose description explains it. The agent can search quests by keyword or by item ID. See spec "Tools" and user stories 1–3.

**Blocked by:** 06

**Status:** done

- [x] `search_quests(query, limit?, offset?, lang?)` matches case-insensitively against title, subtitle and description plain text, and against item IDs and item display names referenced by tasks and rewards
- [x] A query that is an item ID (e.g. `minecraft:snowball`) finds quests whose tasks or rewards reference it
- [x] Each hit shows quest ID, title, chapter, status and a short snippet around the match
- [x] Paging: `limit` default 10, max 50, `offset`; output starts with `N total, showing x–y`
- [x] Hidden quests are never matched when `revealHiddenQuests = false`
- [x] Endpoint tests: description keyword hit (snowball case), item ID hit, snippet content, paging header and bounds, hidden filtering, no results
- [ ] Manual: search the sample quest book via MCP Inspector — delegated to the user by request on 2026-10-14; automated verification only for this implementation.

## Comments

- Carry-over from 06: search through `QuestBookView.description/tasks/rewards(quest)`, which already apply the book's hiding rules. The "No quest with ID" message of `get_quest` currently only points to `get_chapter`; mention `search_quests` there too. Reward tables (random/choice/loot) are only type + title, so "quests that reward X" won't find items inside tables.
- Implemented `SearchQuestsTool`, registered on the real MCP server. Literal Unicode-aware case-insensitive search over the selected-language title/subtitle/description and readable task/reward item/fluid IDs and game-language display names. Each quest appears once, in chapter/quest book order.
- Search reads only through `QuestBookView`: hidden chapters/quests, details/text hidden until progression allows them, sequential tasks not yet listed and invisible rewards cannot generate hits unless revealed. Rich-text matching uses displayed link labels, resolves readable quest-link titles and leaves hidden targets as their written label; image paths and non-displayed URL destinations are excluded. A readable rumour about a secret can match the visible quest without returning the hidden quest or its ID.
- Results begin with `N total, showing x–y`; default limit 10, valid limits 1–50, offset 0–2147483647. Empty matches and offsets beyond the end use `showing 0–0` with distinct explanations. Blank queries and invalid paging return tool errors. Snippets keep at most 160 Unicode code points plus omission marks, without splitting surrogate pairs.
- `get_quest` discovery guidance now mentions `search_quests`. `dev/README.md` includes sample-book queries for description/item search, Chinese, pagination and hidden fields.
- Verification: `./gradlew --offline build` passed with 167 tests, including 20 new endpoint cases for field matching, snippets, literal punctuation, Chinese/English fallback, fluids, hidden content/links, paging boundaries, argument errors, tool discovery and unavailable game state. Before changes, the 147 tests for tickets 01–06 were rerun successfully; `git diff --check` passed.
- Manual validation was not run in this environment: Xvfb, xdotool and MCP Inspector are absent. The user explicitly selected automated verification only and will perform the in-game check. No dependencies were added.
