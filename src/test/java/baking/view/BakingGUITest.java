package baking.view;

import baking.model.Ingredient;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BakingGUITest {
    @Test
    void vectorIconsPaintWithoutImageAssets() {
        var heart = GuiIcons.heart(20);
        var cup = GuiIcons.measuringCup(28);
        assertEquals(20, heart.getIconWidth());
        assertEquals(28, cup.getIconHeight());

        BufferedImage canvas = new BufferedImage(28, 28, BufferedImage.TYPE_INT_ARGB);
        heart.paintIcon(null, canvas.getGraphics(), 0, 0);
        assertTrue(canvas.getRGB(10, 8) != 0);
        canvas = new BufferedImage(28, 28, BufferedImage.TYPE_INT_ARGB);
        cup.paintIcon(null, canvas.getGraphics(), 0, 0);
        assertTrue(canvas.getRGB(8, 19) != 0);
    }

    @Test
    void sharedQuantityFormatterRoundsAndUsesCloseCookingFractions() {
        assertEquals("0.58", QuantityFormatter.formatQuantity(0.5833333));
        assertEquals("1.17", QuantityFormatter.formatQuantity(1.1666667));
        assertEquals("1 1/3", QuantityFormatter.formatQuantity(1.3125));
        assertEquals("2", QuantityFormatter.formatQuantity(2.0));
        assertEquals("1", QuantityFormatter.formatQuantity(1.0));
        assertEquals("1/2 cup", QuantityFormatter.formatIngredient(new Ingredient("sugar", 0.5, "cups"))
                .replace("sugar ", ""));
        assertEquals("2 cups", QuantityFormatter.formatIngredient(new Ingredient("flour", 2, "cup"))
                .replace("flour ", ""));
        assertEquals("1 1/3 cups", QuantityFormatter.formatIngredient(new Ingredient("flour", 1.3125, "cup"))
                .replace("flour ", ""));
    }

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
