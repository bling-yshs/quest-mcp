# 05: Quests read path — `list_chapters` + `get_chapter`

**What to build:** An AI agent can list the quest book's chapters with the player's completion percentage and list the quests in a chapter with their status — reading the live FTB Quests data synced to the client, respecting what the player is allowed to see. See spec "Domain model + sources", "Adapters", "Visibility rule", "Tools".

**Blocked by:** 02

**Status:** done

- [x] `QuestSource` interface with neutral, Minecraft-free records for chapters and quests (ID as FTB Quests hex string, chapter, title, subtitle, visibility, status `LOCKED` / `AVAILABLE` / `IN_PROGRESS` / `COMPLETED`, dependency IDs)
- [x] FTB Quests adapter reads the client quest file and the local team's synced progress; reports `QUESTS_NOT_LOADED` when unavailable
- [x] `list_chapters`: chapter ID, title, quest count, completion %
- [x] `get_chapter(chapter_id)`: quests with ID, title, status; unknown chapter → `isError`
- [x] Config `revealHiddenQuests` (default `false`): when false, chapters/quests not visible in the quest book are excluded; when true they are included and marked hidden; `get_status` reports the real value
- [x] Text in the player's current game language, falling back to `en_us`; optional `lang` argument overrides
- [x] Quest tools return `isError` with an explanation when not in a world / quests not loaded
- [x] Endpoint tests with a fake `QuestSource`: listing, status rendering, hidden filtering on/off, language fallback, unknown IDs, game-state errors
- [x] Manual: `runClient` with a sample quest book (including a hidden quest), verify via MCP Inspector

## Comments

- Model (`core.source`, no Minecraft types):
  - `QuestSource.questBook(Set<String> languages)` returns a `QuestBook` snapshot of the whole book, hidden parts included. It is called on the game thread only.
  - `QuestBook(List<Chapter>)` with nested records `QuestBook.Chapter(id, title, visible, quests)` and `QuestBook.Quest(id, chapterId, title, subtitle, visible, status, dependencyIds)`. They are nested so that the adapter can import FTB's own `Chapter`/`Quest` without name clashes. 06 can add tasks, rewards and the description to `Quest` (or as further nested records).
  - IDs are FTB's 16-digit upper-case hex strings (`getCodeString()`). `dependencyIds` holds only quest dependencies; FTB also allows other objects (e.g. chapters) as dependencies, and those are left out.
  - `QuestStatus` is `LOCKED` / `AVAILABLE` / `IN_PROGRESS` / `COMPLETED`.
  - `Localized<T>(Map<String, T> byLanguage, T displayed)` holds text per language plus what the game displays now. `in(lang)` picks `lang`, else `en_us`, else `displayed`. This fallback logic lives in core. The `displayed` value covers text FTB derives itself, e.g. an untitled quest is named after its first task ("16x Torch"). That derived text is always in the game language, even when `lang` asks for another one. `Localized` is generic so that 06 can use it for the rich-text description.
- Tool layer (`core.tool`):
  - `QuestBookReader` does one game-thread read per call: game status, then (only when in a world with the quest book loaded) `questBook({lang, en_us})`. Quest tools need only `inWorld && questsLoaded`; a JEI that is not ready does not block them.
  - `QuestBookView` is the only way tools see chapters and quests. It applies the visibility rule, resolves the language, matches IDs ignoring case, surrounding spaces and a leading `#`, and computes `progress()`.
  - `QuestMarkdown.appendNotes` adds the shared notes: one when no text exists in the requested language, one when hidden content is revealed.
  - 06–08 should add their lookups (quest by ID, follow-ups) to `QuestBookView`, so that hidden filtering stays in one place.
- Visibility rule (`revealHiddenQuests = false`):
  - A chapter is shown iff `chapter.hasAnyVisibleChildren() && chapter.isVisible(selfTeamData)`. This is exactly what the quest book's chapter list uses outside edit mode (`ChapterGroup.getVisibleChapters`).
  - A quest is shown iff `quest.isVisible(selfTeamData)` (as in `QuestButton.shouldDraw`) and its chapter is shown.
  - So these FTB flags make content hidden, all through FTB's own logic: chapter `always_invisible`; chapters whose quests are all invisible; quest `invisible` (until completed, or until `invisible_until_tasks` tasks are done); `hide_until_deps_complete` and `hide_until_deps_visible` (per quest, or the chapter defaults `hide_quest_until_deps_*`); `hide_excluded_quests` together with an exclusive quest line; and a quest whose dependencies are all invisible.
  - Edit mode (where FTB shows everything) is ignored on purpose.
  - The detail flags `hide_details_until_startable` and `hide_text_until_complete` do not hide a quest, so they are not applied here; 06 should honour them for `get_quest`.
  - Hidden content is never hinted at: there is no "N hidden" count, and a hidden chapter's ID gets the same "not found" message as an unknown one.
  - With `revealHiddenQuests = true`, everything is listed, and hidden chapters and quests carry `(hidden)` / `, hidden`. A quest in a hidden chapter counts as hidden even if its own flag says visible.
- Status: `COMPLETED` if `isCompleted`; else `LOCKED` if `!canStartTasks` (the quest book's lock colour); else `IN_PROGRESS` if `isStarted`; else `AVAILABLE`.
- Completion %: the number of completed quests among the listed quests, rounded down. Hidden quests count only when revealed. FTB itself shows no chapter percentage in the book; its `getRelativeProgress` would count hidden and partial progress, so it is not used.
- Language findings (FTB 26.1.2.9):
  - On join the server sends the client the translation tables for `en_us`, the book's `fallback_locale` and the player's language (`TranslationManager.sendTranslationsToPlayer`). It sends a new one when the player switches language (a mixin on client information).
  - There is no public way to list which tables the client has. The adapter reads exact-locale entries through the public `TranslationManager.getEntry(object, locale, key)` and turns them into plain text with `TextUtils.parseRawText(...).getString()` and `ChatFormatting.stripFormatting`.
  - So `lang` works for the game language and `en_us` (the main case: reading the English original). Any other language falls back to English, and the output then says `No quest text in `xx` is available … so text is shown in English`.
  - `lang` is normalised (`zh-CN` → `zh_cn`).
  - FTB's own `RequestTranslationTableMessage` could fetch other tables, but it is an asynchronous packet to the server, so it was not used (see the question in the report).
- Config: `revealHiddenQuests` (default `false`) in `config/questmcp-client.toml`. It is read on every call, and NeoForge's config watcher picks up edits while the game runs (verified). `get_status` reports the real value with a short explanation.
- `ClientGameSource` now also requires `ClientQuestFile.getInstance().selfTeamData.isValid()` for "quests loaded", since FTB will not open the book before team data arrives.
- Out of scope / not done:
  - quests linked into a chapter from other chapters (`QuestLink`) are not listed by `get_chapter`;
  - chapter groups are not shown, but chapters follow the book order (default group first, then each group);
  - no paging: the spec's table gives these two tools no `limit`/`offset`.
- Tests: `QuestChaptersTest` (20 tests) with `FakeQuestSource`. Like the adapter, the fake returns only the asked-for languages. The tests cover listing and order, completion counts, status and subtitle rendering, hidden filtering off and on (including a visible quest in a hidden chapter), unknown and hidden chapter IDs, ID normalisation, a missing `chapter_id`, game language, `lang` override, `de_de` fallback with its note, untitled quests, `NOT_IN_WORLD`, quests not loaded, no JEI needed, and `get_status` with reveal on and off. Two mutation checks (dropping the hidden filter; dropping the language fallback) each made several tests fail. `./gradlew build`: 123 tests pass.
- Sample quest book: `dev/sample-quests/quests` (hand-written Json5 with comments, `en_us` + `zh_cn`). `dev/README.md` describes it. `./gradlew installSampleQuests` syncs it to `run/config/ftbquests/quests`. FTB loaded it without errors: `Loaded 1 chapter groups, 3 chapters, 8 quests`, with `en_us` (19 entries) and `zh_cn` (13) tables. FTB rewrites the `run/` copy in its own format when it saves; rerun the task to reset it.
- Manual check under xvfb with `runClient`, using a "Create Test World" world with commands on, curl and the MCP Inspector CLI:
  - In the main menu, `list_chapters` gave the "not in a world" error.
  - en_us, reveal off:
    - `list_chapters` showed Getting Started (3 quests) and Into the Nether (2); Developer Room was missing.
    - `get_chapter 1000000000000001` showed Gather Wood (AVAILABLE), Make Snowballs (LOCKED) and "16x Torch" (LOCKED). The golem quest (hidden until deps complete) and Secret Stash (invisible) were not listed.
    - `get_chapter 1000000000000003` gave the "No chapter with ID" error.
    - `/ftbquests open_book` showed the same chapters and quests.
  - Setting `revealHiddenQuests = true` while the game ran took effect at once:
    - `get_status` reported `true`;
    - 3 chapters, with Developer Room (hidden);
    - Getting Started showed 5 quests, with Build a Snow Golem (LOCKED, hidden) and Secret Stash (AVAILABLE, hidden);
    - Developer Room showed Debug Quest (AVAILABLE, hidden).
  - Progress changes:
    - `/ftbquests change_progress @p complete 2000000000000001` made Gather Wood COMPLETED, made Make Snowballs, 16x Torch and Light a Portal AVAILABLE, and gave 1 completed (20%).
    - `/give @p minecraft:snowball 5` made Make Snowballs IN_PROGRESS.
    - 11 more snowballs made it COMPLETED and the golem quest AVAILABLE and visible, even with reveal off.
  - zh_cn game language (`lang:zh_cn`, same world):
    - chapters `入门` and `前往下界`;
    - quests `收集木头 (COMPLETED): 一切从一棵树开始`, `制作雪球`, `建造雪傀儡`;
    - the Nether quests fell back to English;
    - `lang=en_us` gave the English text;
    - `lang=de_de` gave English with the note.
  - In an en_us game, `lang=zh_cn` gave English with the note, because the server never sent the zh_cn table.
  - MCP Inspector CLI: `tools/list` showed both tools and their schemas, and `list_chapters` and `get_chapter` (with `--tool-arg 'chapter_id="1000000000000001"'`) worked.
  - Afterwards `run/options.txt` was back to `lang:en_us`, `run/config/questmcp-client.toml` was restored (`revealHiddenQuests = false`) and the sample book was reinstalled.
- Not seen in game: the `QUESTS_NOT_LOADED` error. The quest book was always loaded by the time a call could be made, so it is covered by the tests only.
- Inspector quirk: the CLI turns an all-digit `--tool-arg` value into a JSON number, which the schema check rejects (`must be a string`). Quote it as above. Real FTB IDs are random hex and nearly always contain letters.
