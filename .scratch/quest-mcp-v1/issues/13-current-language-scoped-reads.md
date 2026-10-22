# 13: Current game language and scoped quest reads

**Status:** done

**Problem:** Item and recipe requests repeatedly load complete ClientLanguage resource tables. Quest tools rebuild every quest's detailed snapshot, including item-filter alternatives, even when only one quest or a chapter summary is requested.

- [x] All text uses the currently displayed Minecraft/FTB/JEI components
- [x] Remove language override arguments, separate English names, manual fallback and additional language-resource loads
- [x] Read chapter and quest IDs through FTB's public lookup methods
- [x] Overview, chapter and viewed-selection reads omit detailed descriptions, materials and rewards
- [x] Single-quest reads include only the target's readable details and related quest summaries
- [x] Preserve prerequisites, follow-ups, description links, book ordering, visibility and progress
- [x] Keep full quest search semantics, including readable submission materials
- [x] Keep FTB's built-in matching for requested submission materials and the existing game-thread executor
- [x] Update endpoint tests and specification; ./gradlew build passes with 194 tests
- [ ] Compare identical MCP queries and render-thread profiles in the user's modpack

## Implementation

QuestSource declares OVERVIEW, CHAPTER, QUEST, SEARCH and VIEWED read scopes. FtbQuestSource uses getChapter/getQuest for requested identities, streamDependencies/getDependants for related quests, and current displayed description components for linked quest targets. Related snapshots are ordered as in the quest book and contain only basic fields. Detailed materials are captured only for readable QUEST/SEARCH data; sequentially unlisted tasks skip matching until revealed.

JeiRecipeSource resolves existing FormattedText/Component values with getString. RecipeSource and the quest model contain only current display text. Source readiness and game-thread dispatch remain unchanged. Endpoint regressions cover scope/identity forwarding, visibility settings, live viewed selection, readiness, language-argument rejection and refreshed display text. Adapter runtime performance still requires in-game profiling.
