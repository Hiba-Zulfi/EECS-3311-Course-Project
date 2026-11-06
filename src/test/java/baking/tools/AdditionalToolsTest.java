package baking.tools;

import baking.llm.LLMClient;
import baking.llm.MockLLMProvider;
import baking.model.Ingredient;
import baking.model.Recipe;
import baking.service.IngredientManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdditionalToolsTest {
    private final Recipe recipe = new Recipe(31, "Vanilla cake", "A test cake", 8, 20, 35,
            List.of(new Ingredient("butter", 1, "cup"), new Ingredient("sugar", 1, "cup")),
            List.of("Mix", "Bake"), List.of("vegetarian"));

    @Test
    void substitutionUsesPantryContextAndReturnsObservableSuggestion() {
        MockLLMProvider provider = new MockLLMProvider("Use applesauce in equal amount for a softer crumb.");
        SubstitutionTool tool = new SubstitutionTool(new LLMClient(provider),
                new IngredientManager(List.of(new Ingredient("sugar", 1, "cup"))));
        String result = tool.suggestSubstitute(recipe.getIngredients().getFirst(), recipe);
        assertTrue(result.contains("applesauce"));
        assertTrue(provider.getPrompts().getFirst().contains("Missing pantry ingredients"));
        assertEquals("SubstitutionTool", tool.getName());
    }

    @Test
    void tasteCustomizationReturnsNewRecipeWithModelQuantities() {
        MockLLMProvider provider = new MockLLMProvider("{\"ingredients\":["
                + "{\"name\":\"butter\",\"quantity\":0.75,\"unit\":\"cup\"},"
                + "{\"name\":\"sugar\",\"quantity\":0.5,\"unit\":\"cup\"}],"
                + "\"explanation\":\"Reduced sugar for a less sweet cake.\"}");
        TasteCustomizationTool tool = new TasteCustomizationTool(new LLMClient(provider));
        Recipe customized = tool.customizeRecipe(recipe, "less sweet");
        assertNotSame(recipe, customized);
        assertEquals(0.5, customized.getIngredients().get(1).getQuantity());
        assertEquals(1, recipe.getIngredients().get(1).getQuantity());
        assertTrue(tool.explainChanges(customized).contains("less sweet"));
        assertTrue(tool.execute("Make it more citrusy").contains("Taste customization"));
    }

    @Test
    void invalidCustomizationJsonRaisesClearException() {
        TasteCustomizationTool tool = new TasteCustomizationTool(new LLMClient(new MockLLMProvider("not json")));
        assertTrue(assertThrows(baking.llm.LLMException.class,
                () -> tool.customizeRecipe(recipe, "less sweet")).getMessage().contains("customized recipe JSON"));
    }

    @Test
    void troubleshootingReturnsCausesFixesAndAsksForMoreInfoWhenBlank() {
        MockLLMProvider provider = new MockLLMProvider("1. Too much leavening\n- Oven temperature was low");
        TroubleshootingTool tool = new TroubleshootingTool(new LLMClient(provider));
        String result = tool.execute("My cake sank in the middle");
        assertTrue(result.contains("Troubleshooting advice"));
        assertTrue(result.contains("Too much leavening"));
        assertEquals(1, provider.getPrompts().size());
        assertEquals(2, tool.suggestSolutions("cake sank").size());
        assertTrue(tool.execute(" ").contains("Please describe what happened"));
    }
}
