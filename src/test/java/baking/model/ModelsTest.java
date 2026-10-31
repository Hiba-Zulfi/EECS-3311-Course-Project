package baking.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModelsTest {
    @Test
    void ingredientUpdatesAndValidatesQuantity() {
        Ingredient flour = new Ingredient("flour", 2, "cups");
        flour.updateQuantity(1.5);
        assertEquals(3, flour.getQuantity());
        assertThrows(IllegalArgumentException.class, () -> flour.updateQuantity(-1));
    }

    @Test
    void recipeCalculatesTimeScalesAndProtectsItsLists() {
        Recipe recipe = recipe();
        assertEquals(45, recipe.getTotalTime());
        Recipe doubled = recipe.scaleRecipe(recipe, 8);
        assertEquals(8, doubled.getServings());
        assertEquals(4, doubled.getIngredients().getFirst().getQuantity());
        assertEquals(4, recipe.getServings());
        assertThrows(UnsupportedOperationException.class,
                () -> recipe.getIngredients().add(new Ingredient("salt", 1, "tsp")));
    }

    @Test
    void shoppingListContainsOnlyIngredientDeficits() {
        Recipe recipe = recipe();
        List<Ingredient> available = List.of(new Ingredient("FLOUR", 1, "cups"));
        List<Ingredient> missing = ShoppingList.findMissingItems(recipe, available);
        assertEquals(2, missing.size());
        assertEquals("flour", missing.getFirst().getName());
        assertEquals(1, missing.getFirst().getQuantity());
        assertEquals("sugar", missing.get(1).getName());
        assertEquals(2, ShoppingList.generate(recipe, available).getItems().size());
    }

    @Test
    void bakingRequestAndPreferencesExposeExpectedValues() {
        BakingRequest request = new BakingRequest("cookies", 6, 40,
                List.of(new Ingredient("flour", 1, "cup")), List.of("vegan"), "less sweet");
        assertEquals(6, request.getDesiredServings());
        assertEquals(List.of("vegan"), request.getDietaryRestrictions());

        UserPreferences preferences = new UserPreferences(4);
        preferences.addPreference("citrus");
        preferences.addPreference("citrus");
        preferences.addDietaryRestriction("vegan");
        preferences.removePreference("citrus");
        assertTrue(preferences.getPreference().isEmpty());
        assertEquals(List.of("vegan"), preferences.getDietaryRestrictions());
        assertEquals(4, preferences.getPreferredServingSize());
    }

    private static Recipe recipe() {
        return new Recipe(1, "Test loaf", "A simple test", 4, 15, 30,
                List.of(new Ingredient("flour", 2, "cups"), new Ingredient("sugar", 1, "cup")),
                List.of("Mix", "Bake"), List.of("vegetarian"));
    }
}
