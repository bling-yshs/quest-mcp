package com.yshs.questmcp.core.tool;

import com.yshs.questmcp.core.source.IngredientKind;
import com.yshs.questmcp.core.source.RecipeSource.Amount;
import com.yshs.questmcp.core.source.RecipeSource.RecipePage;
import com.yshs.questmcp.core.source.RecipeSource.Role;
import java.util.List;

/** Compact recipe output shared by ingredient and category lookups. */
final class RecipeMarkdown {
    /** Prevents construction of the renderer. */
    private RecipeMarkdown() {
    }

    /**
     * Renders counts, alternatives, catalysts, fluids and optional shaped grids.
     * @param page paged recipe snapshot
     * @return Markdown recipe list
     */
    static String render(RecipePage page) {
        StringBuilder out = new StringBuilder();
        int start = Math.min(page.offset(), page.total());
        Paging.appendHeader(out, page.total(), start, start + page.recipes().size());
        for (var category : page.categories()) {
            out.append("- ").append(category.title()).append(" (`").append(category.id()).append("`): ")
                    .append(category.count()).append('\n');
        }
        if (page.recipes().isEmpty()) {
            return out.append(page.total() == 0 ? "\nNo matching recipes.\n" : "\nNo results at this offset.\n")
                    .toString();
        }
        int number = start;
        for (var recipe : page.recipes()) {
            out.append("\n## ").append(++number).append(". ").append(recipe.categoryTitle()).append(" (`")
                    .append(recipe.categoryId()).append("`)\n\n");
            for (Role role : Role.values()) {
                var slots = recipe.slots().stream().filter(slot -> slot.role() == role).toList();
                out.append(role == Role.CATALYST ? "Catalysts:\n" : role == Role.INPUT ? "Inputs:\n" : "Outputs:\n");
                if (slots.isEmpty()) {
                    out.append("- None.\n");
                }
                for (var slot : slots) {
                    if (slot.alternatives().isEmpty() && slot.grid().isPresent()) {
                        continue;
                    }
                    out.append("- ");
                    if (slot.tag().isPresent()) {
                        out.append("tag #").append(slot.tag().get()).append(" (e.g. ");
                        appendAmounts(out, slot.alternatives().stream().limit(3).toList());
                        out.append(')');
                    } else {
                        if (slot.alternatives().size() > 1) {
                            out.append("any of ");
                        }
                        appendAmounts(out, slot.alternatives().stream().limit(10).toList());
                        if (slot.alternatives().size() > 10) {
                            out.append(" and ").append(slot.alternatives().size() - 10).append(" more");
                        }
                    }
                    out.append('\n');
                }
            }
            if (recipe.slots().stream().anyMatch(slot -> slot.grid().isPresent())) {
                String[][] cells = new String[3][3];
                for (String[] row : cells) {
                    java.util.Arrays.fill(row, "");
                }
                for (var slot : recipe.slots()) {
                    if (slot.grid().isPresent()) {
                        var position = slot.grid().get();
                        if (position.row() >= 0 && position.row() < 3 && position.column() >= 0 && position.column() < 3) {
                            cells[position.row()][position.column()] = slot.tag().map(tag -> "`#" + tag + "`")
                                    .orElseGet(() -> slot.alternatives().isEmpty() ? "" : "`"
                                            + slot.alternatives().get(0).ingredient().id() + "`");
                        }
                    }
                }
                out.append("\nCrafting grid (tag/first alternative; see inputs for choices):\n\n")
                        .append("| 1 | 2 | 3 |\n|---|---|---|\n");
                for (String[] row : cells) {
                    out.append("| ").append(String.join(" | ", row)).append(" |\n");
                }
            }
        }
        return out.toString();
    }

    /**
     * Appends counted ingredient choices with fluid amounts in millibuckets.
     * @param out Markdown output
     * @param amounts displayed alternatives
     */
    static void appendAmounts(StringBuilder out, List<Amount> amounts) {
        if (amounts.isEmpty()) {
            out.append("No item/fluid ingredient exposed by JEI.");
        }
        for (int i = 0; i < amounts.size(); i++) {
            var amount = amounts.get(i);
            var item = amount.ingredient();
            out.append(i > 0 ? ", " : "").append(amount.amount() < 0 ? "unspecified" : amount.amount())
                    .append(item.kind() == IngredientKind.FLUID ? " mB × " : " × ")
                    .append(item.name()).append(" (`").append(item.id()).append("`)");
        }
    }
}
