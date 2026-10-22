# 10: `get_recipes` + `get_uses`

**What to build:** An AI agent can find every recipe that produces an item and every recipe that consumes it, across all JEI categories including modded machines, rendered compactly with paging and per-category breakdown. See spec "Recipe rendering" and "Tools".

**Blocked by:** 09

**Status:** done

- [x] Domain recipe record: category UID + title; slots with role `INPUT` / `OUTPUT` / `CATALYST`, each with alternative ingredients (kind, ID, amount), optional tag ID, optional grid position for shaped crafting
- [x] JEI adapter extracts slots generically for any category via JEI's recipe slot layout; catalysts from the category's registered catalysts; category-specific drawn text is ignored
- [x] `get_recipes(id, kind?, category?, limit?, offset?)` and `get_uses(...)` with `limit` default 10 / max 50
- [x] Output header: `N total, showing x–y` plus per-category counts; `category` filters to one category
- [x] Rendering: category title + UID, catalysts; `amount × name (`id`)`; tag slots as `tag #<tag> (e.g. a, b, c)` with ≤3 examples; fluid amounts in mB; shaped crafting adds a 3×3 grid table
- [x] Endpoint tests with a fake `RecipeSource`: shaped/shapeless crafting, tag slot, fluid slot, machine with catalyst, multi-category counts, category filter, paging bounds, unknown ID
- [ ] Manual: snowball, iron ingot (`get_uses`, many recipes) and one modded machine recipe via MCP Inspector — delegated to the user

## Comments

- Added immutable recipe/slot/amount/grid/count/page records. Two lookup modes share one tool implementation and one renderer.
- The adapter focuses every registered JEI variant of the requested item/fluid registry ID, respects JEI's default hidden-category/recipe filtering, counts matches and builds layouts only for the requested page. Empty pages preserve complete counts; unknown IDs/categories are errors.
- Generic slots use public IRecipeSlotsView APIs; category crafting stations become catalysts. JEI's public slot tag key is used on current versions; older JEI versions fall back to the public ingredient helper's tag-equivalence API. No reflection.
- Standard shaped RecipeHolder recipes get grid coordinates from the public drawable slot areas. Custom category layouts remain generic ingredient lists unless they expose a standard shaped recipe.
- Alternatives retain individual amounts. Tags show at most three examples; other alternative lists at most ten plus the omitted count. Unknown JEI counts are explicitly unspecified. Ingredient types outside the spec's item/fluid scope are not fabricated, and slots with no supported identity say so.
- Five endpoint tests cover the acceptance paths and unspecified quantities. `./gradlew --offline build` passed: 179 tests. The real JEI slot implementation and vanilla crafting recipe type were verified through jar-source-reader. No dependencies added. In-game adapter checks remain with the user.
