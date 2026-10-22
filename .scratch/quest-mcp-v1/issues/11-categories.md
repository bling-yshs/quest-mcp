# 11: `list_categories` + `get_category_recipes`

**What to build:** An AI agent can answer "what can this machine do?" by browsing JEI recipe categories and the recipes inside one. See spec "Tools".

**Blocked by:** 10

**Status:** done

- [x] `list_categories(limit?, offset?)`: category UID, title, catalysts, recipe count; paging header per spec
- [x] `get_category_recipes(category, limit?, offset?)`: recipes in that category rendered exactly like `get_recipes`; unknown category → `isError`
- [x] Endpoint tests with a fake `RecipeSource`: listing, paging, recipe rendering reuse, unknown category
- [ ] Manual: list categories in `runClient` and browse the furnace and one modded category via MCP Inspector — delegated to the user

## Comments

- Lists visible JEI categories in JEI order, with visible recipe counts and registered item/fluid crafting stations. Counts and catalysts are read only for categories on the requested page.
- Category recipe browsing reuses ticket 10's paged layout extraction and RecipeMarkdown renderer, without an ingredient focus. Unknown/hidden category UIDs are not found; readiness and paging validation match the other JEI tools.
- Four endpoint tests cover metadata/catalysts, empty and 55-category paging, selected category paging, identical rendering to ingredient lookup, unknown IDs, invalid bounds, world/JEI readiness and registration of all 12 tools.
- `./gradlew --offline build` passed: 183 tests. No added dependencies or reflection. In-game checks remain with the user; examples are in dev/README.md.
