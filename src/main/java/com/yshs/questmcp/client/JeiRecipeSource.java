package com.yshs.questmcp.client;

import com.yshs.questmcp.core.source.IngredientKind;
import com.yshs.questmcp.core.source.RecipeSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import java.util.List;
import java.util.Optional;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.fluids.FluidStack;

/** JEI's public read APIs adapted to immutable, Minecraft-free data on the game thread. */
final class JeiRecipeSource implements RecipeSource {
    /**
     * Lists item and fluid identities using JEI's ingredient manager.
     * @return stable identities, deduplicated within each kind
     */
    @Override
    public List<Ingredient> ingredients() {
        IJeiRuntime runtime = QuestMcpJeiPlugin.runtime();
        if (runtime == null) {
            throw new IllegalStateException("JEI runtime became unavailable.");
        }
        var manager = runtime.getIngredientManager();
        LinkedHashMap<String, Ingredient> identities = new LinkedHashMap<>();
        for (ItemStack stack : manager.getAllIngredients(VanillaTypes.ITEM_STACK)) {
            Ingredient item = ingredient(runtime, VanillaTypes.ITEM_STACK, stack, IngredientKind.ITEM);
            identities.putIfAbsent("item:" + item.id(), item);
        }
        for (FluidStack stack : manager.getAllIngredients(NeoForgeTypes.FLUID_STACK)) {
            Ingredient fluid = ingredient(runtime, NeoForgeTypes.FLUID_STACK, stack, IngredientKind.FLUID);
            identities.putIfAbsent("fluid:" + fluid.id(), fluid);
        }
        return List.copyOf(identities.values());
    }

    /**
     * Reads a representative JEI ingredient, its tooltip, tags and focused information pages.
     * @param id registry ID
     * @param kind item or fluid
     * @return details when JEI knows this identity
     */
    @Override
    public Optional<IngredientInfo> ingredientInfo(String id, IngredientKind kind) {
        IJeiRuntime runtime = QuestMcpJeiPlugin.runtime();
        if (runtime == null) {
            throw new IllegalStateException("JEI runtime became unavailable.");
        }
        return kind == IngredientKind.ITEM
                ? info(runtime, VanillaTypes.ITEM_STACK, id, kind)
                : info(runtime, NeoForgeTypes.FLUID_STACK, id, kind);
    }

    /**
     * Looks up focused recipe counts and builds layouts only for the requested page.
     * @param id ingredient registry ID
     * @param kind item or fluid
     * @param uses input lookup when true
     * @param category optional category filter
     * @param limit page size
     * @param offset entries to skip
     * @return the recipe page, or empty for an unknown identity/filter
     */
    @Override
    public Optional<RecipePage> recipes(String id, IngredientKind kind, boolean uses, String category,
            int limit, int offset) {
        IJeiRuntime runtime = QuestMcpJeiPlugin.runtime();
        if (runtime == null) {
            throw new IllegalStateException("JEI runtime became unavailable.");
        }
        RecipeIngredientRole role = uses ? RecipeIngredientRole.INPUT : RecipeIngredientRole.OUTPUT;
        List<IFocus<?>> focuses = kind == IngredientKind.ITEM
                ? focuses(runtime, VanillaTypes.ITEM_STACK, id, role)
                : focuses(runtime, NeoForgeTypes.FLUID_STACK, id, role);
        if (focuses.isEmpty()) {
            return Optional.empty();
        }
        var manager = runtime.getRecipeManager();
        if (!category.isEmpty() && manager.createRecipeCategoryLookup().get()
                .noneMatch(value -> value.getRecipeType().getUid().toString().equals(category))) {
            return Optional.empty();
        }
        var categories = manager.createRecipeCategoryLookup().limitFocus(focuses).get()
                .filter(value -> category.isEmpty() || value.getRecipeType().getUid().toString().equals(category))
                .toList();
        return Optional.of(page(runtime, categories, focuses, limit, offset));
    }

    /**
     * Lists visible categories and reads catalysts/counts only for the requested page.
     * @param limit page size
     * @param offset categories to skip
     * @return immutable category page
     */
    @Override
    public CategoryPage categories(int limit, int offset) {
        IJeiRuntime runtime = QuestMcpJeiPlugin.runtime();
        if (runtime == null) {
            throw new IllegalStateException("JEI runtime became unavailable.");
        }
        List<IRecipeCategory<?>> categories = runtime.getRecipeManager().createRecipeCategoryLookup().get().toList();
        int start = Math.min(offset, categories.size());
        int end = start + Math.min(limit, categories.size() - start);
        List<Category> out = new ArrayList<>();
        for (IRecipeCategory<?> category : categories.subList(start, end)) {
            List<Amount> catalysts = runtime.getRecipeManager().createCraftingStationLookup(category.getRecipeType())
                    .get().map(value -> amount(runtime, value)).flatMap(Optional::stream).distinct().toList();
            int count = Math.toIntExact(runtime.getRecipeManager().createRecipeLookup(category.getRecipeType())
                    .get().distinct().count());
            out.add(new Category(category.getRecipeType().getUid().toString(), plain(category.getTitle()),
                    catalysts, count));
        }
        return new CategoryPage(categories.size(), offset, out);
    }

    /**
     * Browses a visible category using the same layout extraction as ingredient lookups.
     * @param category category UID
     * @param limit page size
     * @param offset recipes to skip
     * @return recipe page, or empty for an unknown category
     */
    @Override
    public Optional<RecipePage> categoryRecipes(String category, int limit, int offset) {
        IJeiRuntime runtime = QuestMcpJeiPlugin.runtime();
        if (runtime == null) {
            throw new IllegalStateException("JEI runtime became unavailable.");
        }
        var found = runtime.getRecipeManager().createRecipeCategoryLookup().get()
                .filter(value -> value.getRecipeType().getUid().toString().equals(category)).findFirst();
        if (found.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(page(runtime, List.of(found.get()), List.of(), limit, offset));
    }

    /**
     * Builds focuses for every JEI variant of a registry identity.
     * @param runtime JEI runtime
     * @param type ingredient type
     * @param id registry identity
     * @param role input or output
     * @param <T> ingredient value type
     * @return all matching focuses
     */
    private static <T> List<IFocus<?>> focuses(IJeiRuntime runtime, IIngredientType<T> type, String id,
            RecipeIngredientRole role) {
        var manager = runtime.getIngredientManager();
        var helper = manager.getIngredientHelper(type);
        List<IFocus<?>> result = new ArrayList<>();
        for (T value : manager.getAllIngredients(type)) {
            if (helper.getIdentifier(value).toString().equals(id)) {
                result.add(runtime.getJeiHelpers().getFocusFactory().createFocus(role, type, value));
            }
        }
        return result;
    }

    /**
     * Reads complete counts while extracting only the requested layouts.
     * @param runtime JEI runtime
     * @param categories matching categories
     * @param focuses ingredient filters, or empty for all recipes
     * @param limit page size
     * @param offset entries to skip across categories
     * @return immutable paged recipe data
     */
    private static RecipePage page(IJeiRuntime runtime, List<IRecipeCategory<?>> categories,
            List<IFocus<?>> focuses, int limit, int offset) {
        List<CategoryCount> counts = new ArrayList<>();
        List<Recipe> recipes = new ArrayList<>();
        int total = 0;
        for (IRecipeCategory<?> category : categories) {
            total += appendCategory(runtime, category, focuses, Math.max(0, offset - total),
                    limit - recipes.size(), counts, recipes);
        }
        return new RecipePage(total, offset, counts, recipes);
    }

    /**
     * Counts one category and reads its selected recipe layouts.
     * @param runtime JEI runtime
     * @param category the recipe category
     * @param focuses ingredient filters
     * @param skip entries to skip within this category
     * @param limit remaining page size
     * @param counts category count destination
     * @param out recipe destination
     * @param <T> category recipe type
     * @return matching recipe count
     */
    private static <T> int appendCategory(IJeiRuntime runtime, IRecipeCategory<T> category, List<IFocus<?>> focuses,
            int skip, int limit, List<CategoryCount> counts, List<Recipe> out) {
        var lookup = runtime.getRecipeManager().createRecipeLookup(category.getRecipeType());
        if (!focuses.isEmpty()) {
            lookup.limitFocus(focuses);
        }
        List<T> matches = lookup.get().distinct().toList();
        String id = category.getRecipeType().getUid().toString();
        String title = plain(category.getTitle());
        counts.add(new CategoryCount(id, title, matches.size()));
        int start = Math.min(skip, matches.size());
        int end = start + Math.min(limit, matches.size() - start);
        if (start == end) {
            return matches.size();
        }
        List<Slot> catalysts = runtime.getRecipeManager().createCraftingStationLookup(category.getRecipeType())
                .get().map(value -> new Slot(Role.CATALYST, amount(runtime, value).stream().toList(),
                        Optional.empty(), Optional.empty())).toList();
        var emptyFocus = runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup();
        for (T match : matches.subList(start, end)) {
            var layout = runtime.getRecipeManager().createRecipeLayoutDrawable(category, match, emptyFocus)
                    .orElseThrow(() -> new IllegalStateException("JEI could not expose recipe slots for " + id));
            List<IRecipeSlotView> views = layout.getRecipeSlotsView().getSlotViews();
            boolean shaped = match instanceof RecipeHolder<?> holder && holder.value() instanceof ShapedRecipe;
            List<Integer> xs = shaped ? views.stream().filter(view -> view.getRole() == RecipeIngredientRole.INPUT)
                    .filter(view -> view instanceof IRecipeSlotDrawable)
                    .map(view -> ((IRecipeSlotDrawable) view).getAreaIncludingBackground().getX())
                    .distinct().sorted().toList() : List.of();
            List<Integer> ys = shaped ? views.stream().filter(view -> view.getRole() == RecipeIngredientRole.INPUT)
                    .filter(view -> view instanceof IRecipeSlotDrawable)
                    .map(view -> ((IRecipeSlotDrawable) view).getAreaIncludingBackground().getY())
                    .distinct().sorted().toList() : List.of();
            List<Slot> slots = new ArrayList<>();
            for (IRecipeSlotView view : views) {
                Role role = switch (view.getRole()) {
                    case INPUT -> Role.INPUT;
                    case OUTPUT -> Role.OUTPUT;
                    case CRAFTING_STATION -> Role.CATALYST;
                    default -> null;
                };
                if (role == null) {
                    continue;
                }
                List<Amount> alternatives = view.getAllIngredients()
                        .map(value -> amount(runtime, value)).flatMap(Optional::stream).distinct().toList();
                Optional<GridPosition> grid = Optional.empty();
                if (shaped && role == Role.INPUT && xs.size() <= 3 && ys.size() <= 3
                        && view instanceof IRecipeSlotDrawable drawable) {
                    var area = drawable.getAreaIncludingBackground();
                    grid = Optional.of(new GridPosition(ys.indexOf(area.getY()), xs.indexOf(area.getX())));
                }
                Optional<String> tag;
                try {
                    tag = view.getTagKey().map(key -> key.location().toString());
                } catch (NoSuchMethodError olderJei) {
                    // JEI before 29.28 exposes tag equivalence through ingredient helpers.
                    var itemChoices = view.getIngredients(VanillaTypes.ITEM_STACK).toList();
                    var fluidChoices = view.getIngredients(NeoForgeTypes.FLUID_STACK).toList();
                    tag = !itemChoices.isEmpty() && fluidChoices.isEmpty()
                            ? runtime.getIngredientManager().getIngredientHelper(VanillaTypes.ITEM_STACK)
                                    .getTagKeyEquivalent(itemChoices).map(key -> key.location().toString())
                            : itemChoices.isEmpty() && !fluidChoices.isEmpty()
                                    ? runtime.getIngredientManager().getIngredientHelper(NeoForgeTypes.FLUID_STACK)
                                            .getTagKeyEquivalent(fluidChoices).map(key -> key.location().toString())
                                    : Optional.empty();
                }
                slots.add(new Slot(role, alternatives, tag, grid));
            }
            for (Slot catalyst : catalysts) {
                if (!slots.contains(catalyst)) {
                    slots.add(catalyst);
                }
            }
            out.add(new Recipe(id, title, slots));
        }
        return matches.size();
    }

    /**
     * Converts item/fluid stacks in a generic JEI slot to counted neutral ingredients.
     * @param runtime JEI runtime
     * @param typed the typed slot ingredient
     * @param <T> ingredient value type
     * @return a counted identity, or empty for types outside item/fluid scope
     */
    private static <T> Optional<Amount> amount(IJeiRuntime runtime, ITypedIngredient<T> typed) {
        T value = typed.getIngredient();
        IngredientKind kind = value instanceof ItemStack ? IngredientKind.ITEM
                : value instanceof FluidStack ? IngredientKind.FLUID : null;
        if (kind == null) {
            return Optional.empty();
        }
        return Optional.of(new Amount(ingredient(runtime, typed.getType(), value, kind),
                runtime.getIngredientManager().getIngredientHelper(typed.getType()).getAmount(value)));
    }

    /**
     * Converts a typed ingredient to an identity and current-language name.
     * @param runtime JEI runtime
     * @param type JEI ingredient type
     * @param value the ingredient
     * @param kind neutral ingredient kind
     * @param <T> ingredient value type
     * @return neutral searchable identity
     */
    private static <T> Ingredient ingredient(IJeiRuntime runtime, IIngredientType<T> type, T value,
            IngredientKind kind) {
        var helper = runtime.getIngredientManager().getIngredientHelper(type);
        Component name = value instanceof ItemStack stack ? stack.getHoverName()
                : value instanceof FluidStack fluid ? fluid.getHoverName() : Component.literal(helper.getDisplayName(value));
        return new Ingredient(helper.getIdentifier(value).toString(), kind, plain(name), helper.getDisplayModId(value));
    }

    /**
     * Extracts tooltip and information text for a known typed ingredient.
     * @param runtime JEI runtime
     * @param type ingredient type
     * @param id registry ID
     * @param kind neutral kind
     * @param <T> ingredient value type
     * @return detail snapshot if the identity exists
     */
    @SuppressWarnings("removal")
    private static <T> Optional<IngredientInfo> info(IJeiRuntime runtime, IIngredientType<T> type, String id,
            IngredientKind kind) {
        var manager = runtime.getIngredientManager();
        var helper = manager.getIngredientHelper(type);
        Optional<T> found = manager.getAllIngredients(type).stream()
                .filter(value -> helper.getIdentifier(value).toString().equals(id)).findFirst();
        if (found.isEmpty()) {
            return Optional.empty();
        }
        T value = found.get();
        List<String> tooltip = manager.getIngredientRenderer(type).getTooltip(value, TooltipFlag.NORMAL).stream()
                .map(line -> plain(line)).toList();
        List<String> tags = helper.getTagStream(value).map(Object::toString).sorted().toList();
        var focus = runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.INPUT, type, value);
        List<String> information = runtime.getRecipeManager().createRecipeLookup(RecipeTypes.INFORMATION)
                .limitFocus(List.of(focus)).get()
                .map(recipe -> String.join("\n", recipe.getDescription().stream()
                        .map(line -> plain(line)).toList())).toList();
        return Optional.of(new IngredientInfo(ingredient(runtime, type, value, kind),
                tooltip, tags, information));
    }

    /**
     * Resolves displayed text using Minecraft's already-loaded current language.
     * @param text formatted game or mod text
     * @return plain text without formatting codes
     */
    private static String plain(FormattedText text) {
        String plain = ChatFormatting.stripFormatting(text.getString());
        return plain == null ? "" : plain.strip();
    }
}
