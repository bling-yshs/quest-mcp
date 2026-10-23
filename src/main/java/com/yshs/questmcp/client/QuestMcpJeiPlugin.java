package com.yshs.questmcp.client;

import com.yshs.questmcp.QuestMcp;
import java.util.Collection;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import javax.annotation.Nullable;

/** Captures the JEI runtime while JEI has one, i.e. while the player is in a world and JEI has loaded. */
@JeiPlugin
public final class QuestMcpJeiPlugin implements IModPlugin, IIngredientManager.IIngredientListener {
    private static volatile @Nullable IJeiRuntime runtime;

    /** The JEI runtime, or {@code null} while JEI is not ready. */
    public static @Nullable IJeiRuntime runtime() {
        return runtime;
    }

    /**
     * Identifies this plugin in JEI's plugin registry.
     * @return stable Quest MCP plugin ID
     */
    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(QuestMcp.MOD_ID, "main");
    }

    /**
     * Captures JEI and registers one index invalidation listener for its lifetime.
     * @param jeiRuntime newly available runtime
     */
    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        if (runtime == jeiRuntime) {
            return;
        }
        runtime = jeiRuntime;
        JeiRecipeSource.invalidateIngredientIndex();
        jeiRuntime.getIngredientManager().registerIngredientListener(this);
    }

    /** Releases the current runtime and search index when JEI unloads. */
    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
        JeiRecipeSource.invalidateIngredientIndex();
    }

    /**
     * Invalidates search data after JEI adds or replaces ingredients.
     * @param ingredientHelper helper for the changed ingredient type
     * @param ingredients newly registered ingredients
     * @param <V> JEI ingredient value type
     */
    @Override
    public <V> void onIngredientsAdded(IIngredientHelper<V> ingredientHelper,
            Collection<ITypedIngredient<V>> ingredients) {
        JeiRecipeSource.invalidateIngredientIndex();
    }

    /**
     * Invalidates search data after JEI removes ingredients, including representative variants.
     * @param ingredientHelper helper for the changed ingredient type
     * @param ingredients removed ingredients
     * @param <V> JEI ingredient value type
     */
    @Override
    public <V> void onIngredientsRemoved(IIngredientHelper<V> ingredientHelper,
            Collection<ITypedIngredient<V>> ingredients) {
        JeiRecipeSource.invalidateIngredientIndex();
    }

    /**
     * Invalidates search data after JEI refreshes ingredient names or other search metadata.
     * @param ingredientHelper helper for the changed ingredient type
     * @param ingredients updated ingredients
     * @param <V> JEI ingredient value type
     */
    @Override
    public <V> void onIngredientsUpdated(IIngredientHelper<V> ingredientHelper,
            Collection<ITypedIngredient<V>> ingredients) {
        JeiRecipeSource.invalidateIngredientIndex();
    }
}
