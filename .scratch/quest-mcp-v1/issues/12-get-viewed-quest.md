# 12: `get_viewed_quest` — the quest currently open in the quest book

**What to build:** A player says "help me with this quest" while looking at a quest in FTB Quests, and the agent identifies that quest without asking for its name or ID.

**Blocked by:** 06

**Status:** done

- [x] Add `get_viewed_quest(lang?)`, distinct from actionable progression in `get_current_quests`
- [x] Read the active FTB Quests screen on the game thread; return the viewed quest's ID, title, chapter and status, with guidance to call `get_quest`
- [x] Closing the quest details or the quest book must stop reporting that quest; unrelated screens and a chapter overview return an explicit no-viewed-quest state
- [x] Apply the existing visibility and language rules; edit mode must not bypass `revealHiddenQuests`
- [x] World/quest-book unavailability returns the existing readable tool errors
- [x] Endpoint tests cover viewed quest changes, absent/stale selection, hidden filtering, language, schema and discovery
- [x] Update spec and developer examples; compile/build and push
- [ ] Manual: open/switch/close quests and compare via MCP Inspector — delegated to the user, per existing request

## Comments

- This exposes only the live quest-details selection, never a quest merely hovered, pinned, actionable or previously viewed.
- Public APIs only: FTB Library's `ClientUtils.getCurrentGuiAs(QuestScreen.class)` checks the active Minecraft screen, then `QuestScreen.getViewedQuest()` reads its details panel. The cached `ClientQuestFile.getQuestScreen()` is deliberately not used because it can outlive the active screen. FTB's `ViewQuestPanel.onClosed()` clears its selection. These paths were inspected with jar-source-reader; no reflection or dependency additions.
- The book and viewed ID are captured in the same game-thread operation. QuestBookView resolves the selected ID through existing visibility and language rules. Missing/stale/hidden selections have the same successful `Viewed quest: none` result with no leaked identity; reveal-on output marks hidden quests/chapters. A valid result provides the quest ID, title, chapter, status and `get_quest` follow-up guidance.
- `ViewedQuestTest` adds eight endpoint tests: metadata, live changes/closing, stale IDs, hidden selection/chapter handling, game language/override/fallback, readiness errors, schema/discovery and game-thread timeout. Tool discovery now advertises 13 tools.
- `./gradlew --offline build` passed with all 191 tests; `git diff --check` passed. Spec user story/tool list and dev/README.md usage/manual checks were updated. No in-game verification was run; it remains delegated to the user as requested.
