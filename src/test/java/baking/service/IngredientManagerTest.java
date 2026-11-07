package baking.service;

import baking.model.Ingredient;
import baking.model.Recipe;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IngredientManagerTest {
    private final IngredientManager manager = new IngredientManager();
    private final Recipe recipe = new Recipe(1, "Bread", "Test", 4, 10, 20,
            List.of(new Ingredient("Flour", 3, "cups"), new Ingredient("Salt", 1, "tsp")),
            List.of("Bake"));

    @Test
    void emptyPantryMeansEveryIngredientIsMissing() {
        List<Ingredient> missing = manager.checkAvailability(recipe);
        assertEquals(2, missing.size());
        assertEquals(3, missing.getFirst().getQuantity());
        assertEquals(1, missing.get(1).getQuantity());
    }

    @Test
    void matchesNamesWithoutCaseAndReportsPartialQuantityShortfall() {
        List<Ingredient> missing = manager.compareIngredients(recipe.getIngredients(),
                List.of(new Ingredient("fLoUr", 2, "cups"), new Ingredient("salt", 1, "tsp")));
        assertEquals(1, missing.size());
        assertEquals("Flour", missing.getFirst().getName());
        assertEquals(1, missing.getFirst().getQuantity());
    }

    @Test
    void combinesMatchingAvailableAmountsAndReportsOnlyShortages() {
        List<Ingredient> missing = manager.compareIngredients(recipe.getIngredients(),
                List.of(new Ingredient("flour", 1, "cups"), new Ingredient("FLOUR", 2, "cups")));
        assertEquals(1, missing.size());
        assertEquals("Salt", missing.getFirst().getName());
    }

    @Test
    void configuredPantryIsUsedByFindMissingIngredients() {
        IngredientManager withPantry = new IngredientManager(List.of(new Ingredient("flour", 3, "cups")));
        assertEquals(1, withPantry.findMissingIngredients(recipe).size());
    }

    @Test
    void matchesPluralNamesAndIgnoresQualifiers() {
        Recipe qualified = new Recipe(2, "Egg loaf", "", 2, 1, 1,
                List.of(new Ingredient("all-purpose flour", 1, "cup"), new Ingredient("egg", 2, "large")), List.of());
        assertTrue(manager.compareIngredients(qualified.getIngredients(), List.of(
                new Ingredient("FlOuR", 1, "cup"), new Ingredient("eggs", 2, "count"))).isEmpty());
    }

    @Test
    void convertsCompatibleUnitsBeforeComparing() {
        Recipe oneCup = new Recipe(3, "Test", "", 1, 1, 1,
                List.of(new Ingredient("butter", 1, "cup")), List.of());
        assertTrue(manager.compareIngredients(oneCup.getIngredients(),
                List.of(new Ingredient("butter", 16, "tbsp"))).isEmpty());
        List<Ingredient> missing = manager.compareIngredients(oneCup.getIngredients(),
                List.of(new Ingredient("butter", 8, "tbsp")));
        assertEquals(0.5, missing.getFirst().getQuantity(), 0.0001);
    }

    @Test
    void pantryMergeUpdatesSameNormalizedNameAndAddsNewEntries() {
        List<Ingredient> merged = manager.mergeIngredients(
                List.of(new Ingredient("all-purpose flour", 2, "cup")),
                List.of(new Ingredient("flour", 3, "cup"), new Ingredient("eggs", 2, "count")));
        assertEquals(2, merged.size());
        assertEquals(3, merged.getFirst().getQuantity());
    }
}
