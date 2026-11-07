package baking.view;

import baking.model.Ingredient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BakingGUITest {
    @Test
    void parsesCommaSeparatedIngredientsAndUnitlessCounts() {
        List<Ingredient> ingredients = BakingGUI.parseIngredientInput("flour:3 cups, butter:1 cup, egg:2");

        assertEquals(List.of("cup", "cup", "count"), ingredients.stream().map(Ingredient::getUnit).toList());
        assertEquals(3, ingredients.getFirst().getQuantity());
        assertEquals("egg", ingredients.getLast().getName());
    }

    @Test
    void rejectsBadQuantitiesAndUnknownUnits() {
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> BakingGUI.parseIngredientInput("flour:many cups")).getMessage().contains("quantities"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> BakingGUI.parseIngredientInput("flour:2 scoops")).getMessage().contains("Valid units"));
    }
}
