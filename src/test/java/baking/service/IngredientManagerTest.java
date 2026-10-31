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
}
