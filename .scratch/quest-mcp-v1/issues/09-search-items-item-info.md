# 09: Items read path — `search_items` + `get_item_info`

**What to build:** An AI agent can turn a vague name ("雪球", "snowball") into a registry ID and read an item's details, including mod-provided JEI information pages. See spec "Domain model + sources", "Adapters", "Tools".

**Blocked by:** 02

**Status:** done

- [x] `RecipeSource` interface with neutral records for ingredients (kind item|fluid, ID, display name, mod) and ingredient info (tooltip lines, tags, JEI information-page text)
- [x] JEI adapter captures the JEI runtime via a JEI plugin (available/unavailable callbacks) and uses JEI's ingredient manager; reports `JEI_NOT_READY` when the runtime isn't available
- [x] `search_items(query, limit?, offset?, lang?)` matches localized name in the requested language, English name, and registry ID; each hit shows ID, kind, display name, mod; paging header per spec
- [x] `get_item_info(id, kind?, lang?)`: name, mod, tooltip (plain text), tags, JEI info pages; `kind` defaults to `item`; unknown ID → `isError`
- [x] Recipe/item tools return `isError` with an explanation when not in a world / JEI not ready
- [x] Endpoint tests with a fake `RecipeSource`: Chinese and English name search, ID search, same-name disambiguation, fluid kind, paging, unknown ID, JEI-not-ready
- [ ] Manual: search "雪球" with game language zh_cn and read `minecraft:snowball` info via MCP Inspector — delegated to the user

## Comments

- Added the neutral RecipeSource seam and a game-thread RecipeReader. Quest readiness does not block JEI tools; world and JEI readiness do.
- JeiRecipeSource uses the existing plugin runtime, JEI ingredient helper/renderer, tag stream and a focused lookup of RecipeTypes.INFORMATION. IDs are deduplicated by kind; a registry ID selects the first representative variant, since the tool schema does not accept item components/NBT.
- Requested-language and English names come from separate ClientLanguage resource-pack snapshots. Component translations are resolved without changing the player's language. Literal mod text and already-flattened information-page text remain as supplied; tooltip styles and colour codes are dropped.
- Public API only; no reflection or new dependencies. Sources of the existing JEI 29.43.0.107 API were inspected with jar-source-reader.
- Four endpoint tests cover all listed acceptance paths, default item kind, fluid disambiguation, empty fields, literal search, locale override/fallback and paging errors. `./gradlew --offline build` passed: 174 tests. In-game adapter checks remain with the user.
