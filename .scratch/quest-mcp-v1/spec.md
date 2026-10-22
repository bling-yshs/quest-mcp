Status: ready-for-agent

# Quest MCP v1

## Problem Statement

Players of large modpacks constantly lose information: a quest description explains how to obtain a key item (e.g. that snowballs come from a particular recipe or mechanic), but the player skimmed past it, can't remember which chapter it was in, or never opened that quest. Recipe lookup in JEI is also manual — the player has to know the item's name, page through dozens of recipe categories, and mentally piece together multi-step chains.

More and more players already keep an AI agent (Claude Desktop, Cherry Studio, Cursor, etc.) open next to the game, but that agent knows nothing about *this* modpack: not its quest book, not the player's progress, not the recipes the pack actually ships (which differ from the internet's because of datapacks/KubeJS). So the agent either refuses or hallucinates.

## Solution

A client-side Forge mod, **Quest MCP** (`questmcp`), for Minecraft 1.20.1 that runs a standard MCP server inside the game process on `http://127.0.0.1:25599/mcp` (Streamable HTTP). The player adds that URL to their AI client once. From then on, whenever the player asks "how do I make a snowball?" or "what should I do next?", the agent calls read-only tools that look up the live FTB Quests quest book (with the player's own team progress) and the JEI recipe database, and answers from real pack data.

The agent only sees what the player could see in the quest book; pack authors can opt in to revealing hidden quests. The mod never changes game state.

## User Stories

1. As a modpack player, I want to ask my AI agent "how do I make a snowball?" and have it find the quest whose description explains it, so that I don't have to dig through the quest book myself.
2. As a modpack player, I want my agent to search quest titles, subtitles and descriptions by keyword, so that information I skimmed past can be recovered.
3. As a modpack player, I want my agent to search quests by item (e.g. every quest that asks for or rewards `minecraft:snowball`), so that I learn where an item matters in the progression.
4. As a modpack player, I want my agent to tell me which quests are currently available or in progress, so that I know what to do next.
5. As a modpack player, I want my agent to tell me which locked quests are only one step away from unlocking, so that I can plan ahead.
6. As a modpack player, I want my agent to list all chapters with my completion percentage, so that I get an overview of the pack's progression.
7. As a modpack player, I want my agent to list the quests in a chapter with their status (locked / available / in progress / completed), so that I can see where I am in that chapter.
8. As a modpack player, I want my agent to read one quest in full — description, tasks with my current progress, rewards, prerequisites and follow-up quests — so that it can explain exactly what is required.
9. As a modpack player, I want quest text returned exactly as the game currently displays it, so that the agent sees the same text I see.
10. As a modpack player, I want item and quest tools to follow my current game language, so that queries reuse the translations already loaded by the game.
11. As a modpack player, I want quest links inside descriptions to be returned as references to quest IDs, so that my agent can follow them with another call.
12. As a modpack player, I want formatting codes and colours stripped from quest text, so that the agent receives clean, readable text.
13. As a modpack player, I want images in quest descriptions to appear as placeholders, so that the agent knows an image was there even though it can't see it.
14. As a modpack player, I want my agent to search items by current display name or by registry ID, so that a vague question like "雪球" resolves to `minecraft:snowball`.
15. As a modpack player, I want search results to show the item's ID, display name and source mod, so that the agent can disambiguate items with the same name.
16. As a modpack player, I want my agent to get every recipe that produces an item across all JEI categories (crafting table, furnace, modded machines…), so that it can tell me how to make it.
17. As a modpack player, I want my agent to get every recipe that uses an item as input, so that it can tell me what an item is good for.
18. As a modpack player, I want recipes to show inputs, outputs and the machine/workstation (catalyst) with amounts, so that the agent's explanation is actionable.
19. As a modpack player, I want shaped crafting recipes to include the 3×3 grid layout, so that I can place items correctly.
20. As a modpack player, I want tag ingredients shown as the tag plus a few example items, so that the agent knows any of them is acceptable.
21. As a modpack player, I want fluid ingredients and outputs with amounts, so that machine recipes involving fluids are complete.
22. As a modpack player, I want my agent to read an item's details — name, mod, tooltip, tags and JEI information pages — so that mod-provided explanations are also available.
23. As a modpack player, I want my agent to list JEI recipe categories and browse the recipes of one category, so that it can answer "what can this machine do?".
24. As a modpack player, I want long recipe lists paged (default 10) with a total count and per-category breakdown, so that the agent's context doesn't overflow and it can narrow down by category.
25. As a modpack player, I want my agent to be able to filter recipe results by category, so that it can drill into the relevant machine.
26. As a modpack player, I want my agent to be able to check status (in world? quests loaded? JEI ready?), so that it can tell me why it can't answer yet.
27. As a modpack player, I want tools called while I'm in the main menu to return a clear "not in a world" message instead of failing, so that the agent can explain the situation.
28. As a modpack player, I want the MCP server to be reachable as soon as the game has started, so that my agent stays connected across world joins and leaves.
29. As a modpack player, I want a chat message with the MCP URL when I join a world, where clicking copies `http://127.0.0.1:25599/mcp`, so that setting up my AI client is trivial.
30. As a modpack player, I want a `/questmcp` client command showing status and the click-to-copy URL, so that I can find it again any time.
31. As a modpack player, I want the URL logged at startup, so that I can find it in the log if the chat message scrolled away.
32. As a modpack player, I want to change the port in config, so that I can avoid conflicts with other software.
33. As a modpack player, I want a clear message when the port is already in use and a `/questmcp restart` command, so that I can retry the same configured URL after the port becomes available.
34. As a modpack player, I want the server bound to 127.0.0.1 only, so that nobody on my network can read my game.
35. As a modpack player, I want requests from web pages with a foreign Origin rejected, so that a malicious website can't read my game via DNS rebinding.
36. As a cautious player, I want to optionally set a bearer token in config, so that other local programs can't query the endpoint.
37. As a modpack author, I want the agent by default to see only quests the player can see in the quest book, so that hidden/secret progression isn't spoiled.
38. As a modpack author, I want a single `revealHiddenQuests` config switch, so that I can choose to expose the whole quest book for my pack.
39. As a modpack author, I want locked-but-visible quests to be readable by the agent, so that behaviour matches what players can already click into in FTB Quests.
40. As a modpack author, I want the mod to be strictly read-only, so that it can't be used to cheat quest completion or rewards.
41. As a modpack author, I want the mod to be client-only, so that I don't need to install anything extra on my server.
42. As an AI agent, I want a standard MCP Streamable HTTP endpoint with `initialize`, `tools/list`, `tools/call` and `ping`, so that any compliant client can connect without a bridge.
43. As an AI agent, I want tools with clear names, descriptions and JSON input schemas, so that I know when and how to call each one.
44. As an AI agent, I want compact Markdown output that includes the IDs needed for follow-up calls, so that I can chain calls with minimal tokens.
45. As an AI agent, I want invalid arguments and unknown IDs to come back as tool errors with a helpful message, so that I can correct myself.
46. As a small local model, I want few, simple tools with flat arguments, so that I can call them reliably.
47. As a mod developer, I want FTB Quests and JEI access isolated behind adapter interfaces, so that porting to 26.2/26.3 only touches the adapters.
48. As a mod developer, I want the MCP endpoint testable with in-memory fake data, so that the whole protocol and tool behaviour is covered without launching Minecraft.
49. As a modpack player, I want to ask my agent about "this quest" while its details are open in the quest book, so that the agent can identify the quest and read its requirements without asking for its name or ID.
50. As a modpack player, I want a failed MCP server to retry once when I join a world, so that it can recover on its fixed configured port after another game instance exits.

## Implementation Decisions

### Platform & packaging
- Minecraft 1.20.1, Forge 47.4.x, ModDevGradle Legacy Forge, Java 17 bytecode (Gradle runs on the environment-provided JDK 21). Official Mojang mappings, no Parchment.
- Mod ID `questmcp`, display name "Quest MCP", package `com.yshs.questmcp`, licence GPL-3.0.
- Client-only endpoint, guarded with Forge DistExecutor; the mod is ignored for server compatibility checks. Hard dependencies: FTB Quests (2001.3.1 line, which brings FTB Library, FTB Teams and Architectury) and JEI (15.0.x line for 1.20.1). Compile against their APIs from maven.ftb.dev / maven.blamejared.com; nothing of theirs is shaded.
- Zero additional runtime dependencies: the MCP layer is hand-written on the JDK's built-in HTTP server and Gson (bundled with Minecraft). The official MCP Java SDK is not used.
- Only 1.20.1 Forge is targeted on the `1.20.1` branch. Portability comes from the adapter interfaces, not from a multi-version build.

### Module layout (deep modules, one seam)
1. **MCP transport** — HTTP endpoint on `127.0.0.1:<port>/mcp`.
   - `POST` only; `GET`/`DELETE` → 405 (no SSE stream, no sessions, no `Mcp-Session-Id`).
   - Origin check: if an `Origin` header is present and its host is not `localhost`/`127.0.0.1`/`[::1]` → 403.
   - Optional bearer token: when configured, missing/wrong `Authorization: Bearer` → 401.
   - Request bodies that are JSON-RPC notifications (no `id`) → 202 with no body; requests → 200 `application/json` with a single JSON-RPC response.
   - Runs on its own small thread pool; never on the game thread.
2. **JSON-RPC / MCP dispatcher** — pure Java, no Minecraft types.
   - Methods: `initialize`, `notifications/initialized`, `ping`, `tools/list`, `tools/call`. Anything else → `-32601`.
   - Protocol version negotiation: echo the client's `protocolVersion` if supported, otherwise answer with the newest supported version.
   - `initialize` advertises only the `tools` capability, server name `questmcp` and mod version.
   - Malformed JSON → `-32700`; invalid request → `-32600`; unknown tool / malformed `tools/call` params → `-32602`. Arguments that violate the tool's input schema are reported as a tool result with `isError: true` (MCP 2025-11-25, SEP-1303), so the model sees the problem and can correct its call.
   - Errors during tool execution (unknown quest ID, not in world, timeout…) → successful JSON-RPC result with `isError: true` and an explanatory text — so the model sees and can react to them.
3. **Tool layer** — pure Java. A registry of tools, each with name, English description, JSON Schema `inputSchema`, and a handler that reads from the two sources and renders Markdown. Output is a single `text` content block; no `structuredContent`.
4. **Domain model + sources (the seam)** — two interfaces returning neutral, Minecraft-free records:
   - **`QuestSource`**: game state, chapters, quests, full-text-searchable text, item references. Each quest carries: ID (FTB Quests hex ID as string), chapter ID, title, subtitle, description as a neutral rich-text tree, tasks (type, title, referenced item/fluid IDs with counts, player progress), rewards (type, title, item IDs with counts), dependency IDs, **visibility** (visible-in-book vs hidden), and **status** (`LOCKED` / `AVAILABLE` / `IN_PROGRESS` / `COMPLETED`) for the local player's team. Text comes from the current displayed FTB components. Reads are scoped to overview, one chapter, one quest with related summaries, full search, or the viewed quest summary.
   - **`RecipeSource`**: game state, ingredient search (items and fluids, matched on current display name and registry ID), ingredient info (name, mod, tooltip lines, tags, JEI information-page text), categories (UID, title, catalysts, recipe count), and recipe lookups by output / by input / by category. A recipe is: category UID + title, and slots with role `INPUT` / `OUTPUT` / `CATALYST`, each slot holding one or more alternative ingredients (kind item|fluid, ID, amount), an optional tag ID, and optional grid position for shaped crafting.
   - Neutral rich text: nodes for plain text, paragraph/line break, quest link (target quest ID), URL link, image (path). Styles/colours are discarded at the adapter.
5. **Adapters** (below the seam, Minecraft-dependent):
   - **FTB Quests adapter** reads the client quest file (`FTBQuestsAPI.api().getQuestFile(true)` / `ClientQuestFile`) and the local team's synced progress (`selfTeamData`), translating FTB Quests objects into the domain model, and quest text components into neutral rich text.
   - Quest overview/chapter/viewed reads skip descriptions, submission materials and rewards. Single-quest details use FTB's public ID lookup and direct prerequisite/dependant APIs, plus readable description link targets; related quests carry summaries. Full search continues to read searchable descriptions and materials.
   - **JEI adapter** captures `IJeiRuntime` via an `IModPlugin`'s `onRuntimeAvailable`/`onRuntimeUnavailable`, uses the ingredient manager for search/info, the recipe manager for lookups, and the generic recipe slot layout (`IRecipeSlotsView`) to extract slots for **any** category. Category-specific drawn text (energy, time) is not extracted.
   - Adapters use public FTB Quests / JEI APIs where they exist. Where a requirement can only be met cleanly via reflection, the implementer asks the user before using it rather than either silently using it or building a roundabout workaround (see the Reflection rule in `AGENTS.md`).
   - All adapter calls execute on the client main thread: the tool layer submits work to the Minecraft client executor and waits with a timeout (≈5 s); on timeout the tool returns an `isError` result.
6. **Bootstrap / client UX** — mod entry point, config, lifecycle, `/questmcp` status command, `/questmcp restart`, world-join chat message, logging. Startup failures retry once on each world join. Restart stops this instance's endpoint and starts it on the configured port with the configured token; successful endpoints continue across world joins. The port stays fixed and failures display retry guidance.

### Configuration (client config file)
- `port` (default `25599`).
- `token` (default empty = disabled).
- `revealHiddenQuests` (default `false`).
- Bind address is not configurable: always `127.0.0.1`.

### Lifecycle
- HTTP server starts once client setup completes and stays up until the game exits; it is independent of world join/leave.
- Game state reported by sources: `NOT_IN_WORLD`, `QUESTS_NOT_LOADED`, `JEI_NOT_READY`, `READY`. Quest tools need quests loaded; recipe tools need JEI ready; otherwise they return an `isError` result with a human-readable explanation.
- If the port is taken, the server is not started; the failure is logged, shown by `/questmcp`, and the world-join chat message reports the failure instead of the URL.
- On world join, a chat message shows the URL; clicking copies exactly the URL string. `/questmcp` shows status + the same clickable URL. The URL is logged at startup.

### Visibility rule
- `revealHiddenQuests = false`: quests (and chapters) the player cannot see in the quest book are excluded from every tool — listing, search, `get_quest` (reported as "not found"), dependency/follow-up lists, `get_current_quests`, and `get_viewed_quest` (reported as no readable selection). Locked-but-visible quests are fully readable.
- `revealHiddenQuests = true`: all quests are exposed; hidden ones are marked as hidden in output.

### Tools (13)
All IDs in output are wrapped in backticks. All list tools accept `limit` (default 10, max 50) and `offset` and begin their output with a header like `N total, showing x–y` (plus per-category counts for recipe tools). All text uses the current game language. Tools have no language override; the adapters resolve existing components without loading additional language resources.

| Tool | Arguments | Returns |
|---|---|---|
| `get_status` | — | mod version, game state, language, quest/JEI readiness, `revealHiddenQuests` |
| `search_items` | `query`, paging | matching ingredients: ID, kind, display name, mod |
| `list_chapters` | — | chapters with ID, title, quest count, completion % |
| `get_chapter` | `chapter_id` | quests in chapter with ID, title, status |
| `get_quest` | `quest_id` | title, subtitle, Markdown description, tasks + progress, rewards, prerequisites, follow-ups, status |
| `get_viewed_quest` | — | quest ID, title, chapter and status from the currently open quest-details panel, or an explicit none; follow with `get_quest` |
| `search_quests` | `query` (keyword or item ID), paging | matching quests: ID, title, chapter, status, snippet around the match |
| `get_current_quests` | paging | available / in-progress quests, then locked quests whose every incomplete prerequisite is itself available or in progress |
| `get_recipes` | `id`, `kind` (item\|fluid, default item), `category`, paging | recipes producing the ingredient |
| `get_uses` | `id`, `kind`, `category`, paging | recipes consuming the ingredient |
| `get_item_info` | `id`, `kind` | name, mod, tooltip, tags, JEI info pages |
| `list_categories` | paging | category UID, title, catalysts, recipe count |
| `get_category_recipes` | `category`, paging | recipes in that category |

### Viewed quest context
- `QuestSource.viewedQuestId()` reads the quest-details selection in the active FTB Quests screen. The book and selection are captured on the game thread in one operation, and the selected ID is resolved through the same visibility view as other quest tools.
- `get_viewed_quest` is a live read for "this quest". Hovered, pinned, progression-available and historically viewed quests are not a viewed selection. A closed quest book, chapter overview, unrelated screen, stale quest ID or disallowed hidden selection returns the same successful no-readable-selection result without leaking an ID.
- The result identifies the quest and tells the agent to call `get_quest` for full details. It does not open a GUI or automatically notify the AI client when the selection changes. World/quest-book unavailability and timeout remain tool errors.

### Rich text → Markdown rules
- Plain text kept; formatting/colour codes removed.
- Quest link → `[<target title>](quest:<id>)` (title resolved in the same language; if the target is hidden and not revealed, render plain text only).
- URL link → `[text](url)`.
- Image → `[image: <path>]`.
- Paragraphs separated by blank lines.

### Recipe rendering
- Header line: category title + UID, catalysts.
- Inputs / outputs as lists `amount × name (`id`)`; tag slots as `tag #<tag> (e.g. a, b, c)` with up to 3 examples; fluid amounts in mB.
- Shaped crafting additionally renders the grid as a small 3×3 table of item IDs/blank.

## Testing Decisions

- **Single automated seam: the MCP endpoint.** Tests start the real HTTP transport on an ephemeral port, wired to in-memory fake `QuestSource` and `RecipeSource` implementations, and send real JSON-RPC requests with the JDK HTTP client. Assertions are on HTTP status codes, JSON-RPC envelopes, and the Markdown text returned — never on internal classes.
- A good test describes externally visible behaviour, e.g. "searching for 'snowball' returns the quest whose description mentions it, with a snippet", "a hidden quest is not found when revealHiddenQuests is false", "get_uses with limit 10 reports the total and per-category counts", "a request with Origin http://evil.example is rejected with 403", "tools called while NOT_IN_WORLD return isError with an explanation".
- Coverage at this seam: handshake & version negotiation, notifications, method/param errors, Origin and token checks, every tool's argument validation and output, visibility filtering, current display text, rejected language overrides, source read scopes, rich-text → Markdown, paging, game-state errors.
- Framework: JUnit 5. The domain model, dispatcher and tool layer must not import Minecraft/Forge/FTB/JEI classes so tests run without a game.
- **Not automated:** FTB Quests adapter, JEI adapter, main-thread dispatch, command, chat message. Verified manually by `runClient` with a sample quest book, connecting MCP Inspector (and curl) to the endpoint and exercising each tool.
- Prior art: none in this repo yet (greenfield). Protocol conformance is cross-checked against MCP Inspector.

## Out of Scope

- Any write/action tools (completing tasks, claiming rewards, opening GUIs, pinning quests).
- Server-side component, recipe sync for dedicated servers without JEI installed server-side (assumed pack authors install JEI on the server).
- REI, EMI, Heracles or other quest/recipe mods; vanilla recipe-manager fallback.
- Minecraft versions other than 1.20.1; multi-version builds.
- SSE streaming, sessions, stdio transport, MCP resources, prompts, sampling.
- Listening on non-loopback interfaces / LAN access.
- Other teams' progress.
- Category-specific recipe metadata (energy, processing time, chances).
- Image contents; quest authoring/editing.
- Copying a full MCP client config JSON (only the URL is copied).

## Further Notes

- JEI on a dedicated server without JEI server-side falls back to jar-bundled recipes; the mod does not detect or warn about this (explicit decision).
- Licensing: the mod is GPL-3.0 and only compiles against FTB Quests / JEI APIs without bundling them; check FTB's licence before public distribution.
- Tool descriptions are in English (best LLM comprehension); returned content follows the game language.
- Prior art for reference: west3436/ftbquests-mcp (1.21.1, quest authoring, HTTP bridge), cuspymd/mcp-server-mod (in-game Streamable HTTP endpoint).
- FTB Quests 2001.3 stores quest data as SNBT with titles and descriptions inline; irrelevant to us since we read the synced in-memory quest file.
