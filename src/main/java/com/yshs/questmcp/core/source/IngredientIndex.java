package com.yshs.questmcp.core.source;

import com.yshs.questmcp.core.source.RecipeSource.Ingredient;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Immutable substring candidate index containing only neutral ingredient data, safe for concurrent searches. */
public final class IngredientIndex {
    private static final int MAX_GRAM_LENGTH = 3;

    private final Map<String, List<Ingredient>> postings;

    /**
     * Indexes one-, two- and three-code-point fragments of names and IDs in source order.
     * The first ingredient for each kind and registry ID remains the representative variant.
     * @param ingredients current-language ingredient snapshots in display order
     */
    public IngredientIndex(Collection<Ingredient> ingredients) {
        Map<String, List<Ingredient>> entries = new HashMap<>();
        Set<String> identities = new HashSet<>();
        for (Ingredient ingredient : ingredients) {
            if (!identities.add(ingredient.kind() + ":" + ingredient.id())) {
                continue;
            }
            Set<String> fragments = new HashSet<>();
            for (String field : List.of(ingredient.id(), ingredient.name())) {
                int[] points = fold(field);
                for (int start = 0; start < points.length; start++) {
                    for (int length = 1; length <= Math.min(MAX_GRAM_LENGTH, points.length - start); length++) {
                        fragments.add(new String(points, start, length));
                    }
                }
            }
            for (String fragment : fragments) {
                entries.computeIfAbsent(fragment, ignored -> new ArrayList<>()).add(ingredient);
            }
        }
        entries.replaceAll((fragment, values) -> List.copyOf(values));
        postings = Map.copyOf(entries);
    }

    /**
     * Searches the smallest fragment posting and verifies the complete literal query against each candidate.
     * Keeps Unicode case-insensitive matching and source ordering without retaining query results.
     * @param query literal name or registry-ID substring
     * @return matching representative ingredients in source order, or empty for an empty query
     */
    public List<Ingredient> search(String query) {
        int[] points = fold(query);
        if (points.length == 0) {
            return List.of();
        }
        int length = Math.min(MAX_GRAM_LENGTH, points.length);
        List<Ingredient> candidates = List.of();
        for (int start = 0; start <= points.length - length; start++) {
            List<Ingredient> matches = postings.get(new String(points, start, length));
            if (matches == null) {
                return List.of();
            }
            if (candidates.isEmpty() || matches.size() < candidates.size()) {
                candidates = matches;
            }
        }
        Pattern pattern = Pattern.compile(Pattern.quote(query), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return candidates.stream()
                .filter(item -> pattern.matcher(item.id()).find() || pattern.matcher(item.name()).find())
                .toList();
    }

    /**
     * Folds Unicode code points using simple case equivalence for the case-insensitive candidate index.
     * @param text original name, registry ID or query
     * @return folded code points, preserving supplementary characters and avoiding multi-character expansions
     */
    private static int[] fold(String text) {
        return text.codePoints().map(point -> Character.toLowerCase(Character.toUpperCase(point))).toArray();
    }
}
