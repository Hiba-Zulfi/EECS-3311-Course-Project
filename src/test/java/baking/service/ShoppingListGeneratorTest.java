package baking.service;

import baking.model.Ingredient;
import baking.model.Recipe;
import baking.model.ShoppingList;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ShoppingListGeneratorTest {
    private final Recipe recipe = new Recipe(1, "Cookies", "Test", 4, 10, 12,
            List.of(new Ingredient("flour", 2, "cups"), new Ingredient("sugar", 1, "cup")),
            List.of("Bake"));

    @Test
    void generatesListFromManagerShortfalls() {
        ShoppingListGenerator generator = new ShoppingListGenerator();
        ShoppingList list = generator.generate(recipe, List.of(new Ingredient("FLOUR", 1, "cups")));
        assertEquals(2, list.getItems().size());
        assertEquals(1, list.getItems().getFirst().getQuantity());
        assertEquals("sugar", list.getItems().get(1).getName());
    }

    @Test
    void anEmptyShoppingListIsValidWhenEverythingIsAvailable() {
        ShoppingListGenerator generator = new ShoppingListGenerator();
        ShoppingList list = generator.generate(recipe, List.of(
                new Ingredient("flour", 2, "cups"), new Ingredient("sugar", 1, "cup")));
        assertNotNull(list);
        assertTrue(list.getItems().isEmpty());
    }

    @Test
    void noAvailableIngredientsProducesAllRecipeIngredients() {
        ShoppingListGenerator generator = new ShoppingListGenerator(new IngredientManager(List.of()));
        assertEquals(2, generator.generate(recipe).getItems().size());
    }
}
