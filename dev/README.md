# Development files

Not part of the mod. Used to check Quest MCP by hand in `./gradlew runClient`.

## Sample quest book (`sample-quests/`)

A small FTB Quests 2001.3 quest book for Minecraft 1.20.1 (SNBT, with English titles and descriptions stored directly in the quest files):

| Chapter | ID | Quests |
|---|---|---|
| Getting Started | `1000000000000001` | `…01` Gather Wood (no prerequisites); `…02` Make Snowballs (needs `…01`; its description explains how to get snowballs and has the rich text: see below); `…03` Build a Snow Golem (needs `…02`, **hidden until `…02` is complete**; its two tasks **must be done in order**); `…04` no title, so FTB names it after its task ("16x Torch"); also a checkmark task with a custom title, an XP reward and an **invisible** XP reward; `…05` Secret Stash (**invisible until completed**); `…06` Bake a Pumpkin Pie (needs `…01`, **description hidden until completed**) |
| Into the Nether | `1000000000000002` | `…11` Light a Portal (needs `…01` from the other chapter; **details hidden until it can be started**); `…12` Blaze Rods (needs `…11`; an item task and a 1000 mB lava task). English sample text |
| Developer Room | `1000000000000003` | **always invisible** chapter with `…21` Debug Quest |

Quest IDs are `20000000000000xx`. Tasks and rewards are vanilla items unless noted.

The description of Make Snowballs (`2000000000000002`) covers the rich text `get_quest` turns into Markdown: `&` colour and format codes, empty lines between paragraphs, a raw JSON line with a quest link to Gather Wood (as FTB's editor inserts it) and one to the invisible Secret Stash, a raw JSON line with a web link, FTB Library's `{open_url:... text:...}` web link, a `{@pagebreak}` and an `{image:...}` line.

Install it into the dev client, replacing any quest book there:

```sh
./gradlew installSampleQuests   # syncs dev/sample-quests/quests to run/config/ftbquests/quests
```

FTB Quests rewrites the copy in `run/` in its own format when it saves; run the task again to reset it. Edit the files here, not in `run/`.

Useful in-game commands (in a world with cheats on, e.g. one made with "Create Test World"):

- `/ftbquests change_progress @p complete 2000000000000001` (or `reset`) to change progress;
- `/give @p minecraft:snowball 5` to put an item task in progress (e.g. `12/16`);
- `/ftbquests open_book` to compare with what the quest book shows;
- `/ftbquests reload` to load the book again after `./gradlew installSampleQuests` without leaving the world.

The game language is `lang:` in `run/options.txt`. FTB Quests 2001.3 stores titles and descriptions inline, so the literal English sample text stays English. Item names and translatable components follow the current game language. This version does not read the newer quest-book `lang/` tables.

With the MCP Inspector CLI, quote all-digit IDs as JSON strings, or it sends them as numbers, which the tools reject: `--tool-arg 'chapter_id="1000000000000001"'`, `--tool-arg 'quest_id="2000000000000002"'`. IDs are strings on purpose: FTB IDs are 64-bit and do not fit a JSON number without losing digits.

## Quest search (`search_quests`)

Connect MCP Inspector to `http://127.0.0.1:25599/mcp` with Streamable HTTP and call `search_quests`:

- `{"query":"shovel"}` finds Make Snowballs through its description, with a snippet explaining how to gather snowballs.
- `{"query":"minecraft:snowball"}` finds its item task. Item and fluid display names use the current game language; literal quest text remains as written.
- `{"query":"雪球"}` finds the snowball item task after switching the game language to Chinese.
- `{"query":"a","limit":1,"offset":1}` shows the second matching quest in book order. The first line gives the total and displayed range; an empty page uses `showing 0–0`.
- `{"query":"Debug Quest"}` has no results with reveal off. `{"query":"Secret Stash"}` can find the readable rumour in Make Snowballs, but must not return the hidden Secret Stash quest. With `revealHiddenQuests = true`, the hidden quests are also found and marked hidden.
- With reveal off and the prerequisites incomplete, `minecraft:carved_pumpkin` must not find the hidden golem quest, `minecraft:obsidian` must not match Light a Portal's hidden details, and `sugar` must not match Bake a Pumpkin Pie's hidden description. Reveal on makes these fields searchable.

Search is literal and ignores case. It includes readable quest text and item/fluid references in readable tasks and rewards; reward-table contents, image paths and non-displayed link destinations are not searched. A quest appears once even when several fields match. Use `get_quest` with a result's quest ID to read the full entry.

## Viewed quest (`get_viewed_quest`)

Keep a quest's details open in FTB Quests, then call `get_viewed_quest` with `{}`. The result gives the active quest ID, title, chapter and status. Ask your agent "help me with this quest"; it can call this tool and then `get_quest` with the returned ID.

Verify that switching to another quest changes the result immediately. Closing the details panel, closing the quest book or opening an unrelated screen must give `Viewed quest: none`, with no previously viewed quest ID. A chapter overview, a hovered icon or a pinned quest does not count as open details. With reveal off, a hidden quest opened in edit mode must give the same none result; with reveal on it is identified and marked hidden.

This tool reads the current selection when called. It does not send selection-change notifications to the agent.

## Current quests and JEI tools

Call these from MCP Inspector while in a world. All paged tools default to 10 entries, accept a maximum limit of 50, and begin with the total and displayed range.

| Tool | Example arguments | Check |
|---|---|---|
| `get_current_quests` | `{}` | In-progress quests precede available ones, then one-step-away locked quests with blockers. Complete Gather Wood and query again; completed quests disappear |
| `search_items` | `{"query":"雪球"}` | Item name, registry ID, kind and mod in the current game language; registry-ID queries work in any language |
| `get_item_info` | `{"id":"minecraft:snowball"}` | Name, tooltip, tags and JEI information pages; empty information is normal when no mod supplies a page |
| `get_item_info` | `{"id":"minecraft:water","kind":"fluid"}` | Fluid identity and tooltip, independent of item IDs |
| `get_recipes` | `{"id":"minecraft:iron_ingot"}` | Producing recipes, complete counts by category, slot amounts and catalysts |
| `get_uses` | `{"id":"minecraft:iron_ingot","limit":1,"offset":1}` | Second consuming recipe; total and per-category counts stay complete |
| `get_recipes` | `{"id":"minecraft:snowball"}` | An empty result is valid if the pack provides no producing recipe |
| `list_categories` | `{}` | Category UIDs, titles, registered crafting stations and visible recipe counts |
| `get_category_recipes` | `{"category":"minecraft:smelting","limit":1}` | Furnace recipes, with the same rendering as ingredient lookups |

Use a UID returned by `list_categories` to browse one modded machine category and to filter `get_recipes`/`get_uses` with `category`. Compare ingredients and amounts with JEI. Tag slots show at most three examples; other alternatives show at most ten with an omitted count. Standard shaped crafting adds a 3×3 grid; blanks remain blank, and grid cells show a tag or the first alternative.

Recipe categories, recipes and crafting stations honor JEI's default visibility filtering. Item registry IDs select a representative JEI variant for details; recipe lookups focus all known variants of that ID. Custom ingredient types outside item/fluid scope have no fabricated identity. Energy, time, chances and other category-drawn text are outside the read model.

Item names, tooltips and other translatable components use Minecraft's current language. Literal or already-flattened mod text remains as supplied; the tools do not accept a separate language override.

All JEI tools should give a readable error in the main menu or while JEI is unavailable. Use `get_status` to check readiness. Requests with unknown IDs/categories or invalid paging should return `isError`.

Run Gradle with Java 21 or newer; the build uses a Java 17 toolchain to target Minecraft 1.20.1. Build using `./gradlew build` (or `./gradlew --offline build` after dependencies are cached). The reobfuscated distributable is `build/libs/questmcp-1.20.1-0.1.0.jar`. Install it with Forge 47.3.19 or newer in the 47 line, FTB Quests 2001.3.1 or newer in the 2001 line (including its FTB Library, FTB Teams and Architectury dependencies), and JEI 15.0.0.12 or newer in the 15 line. The Forge port passes a clean build with 196 automated tests; the distributable uses Java 17 bytecode and SRG mappings. The in-game checks above remain manual validation steps.

FTB Quests 2001.3.0 removed the Item Filters requirement but was a Beta; [2001.3.1](https://www.curseforge.com/minecraft/mc-mods/ftb-quests-forge/files/5045167) is the first subsequent Release. Item Filters and FTB XMod Compat are not required by Quest MCP. The compile dependencies and development runtime use this minimum baseline.
