package baking.controller;

import baking.agent.AgentMemory;
import baking.agent.BakingAgent;
import baking.llm.LLMClient;
import baking.llm.MockLLMProvider;
import baking.model.Ingredient;
import baking.model.Recipe;
import baking.service.FavouritesManager;
import baking.service.IngredientManager;
import baking.service.RecipeRepository;
import baking.service.ShoppingListGenerator;
import baking.tools.ToolManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentControllerTest {
    @TempDir Path temp;

    @Test
    void deterministicOperationsDoNotCallProvider() {
        MockLLMProvider provider = new MockLLMProvider("{\"intent\":\"unclear\"}");
        Ingredient flour = new Ingredient("Flour", 2, "cups");
        Recipe recipe = new Recipe(1, "Bread", "", 2, 10, 20,
                List.of(flour), List.of("Bake"));
        IngredientManager ingredients = new IngredientManager();
        AgentController controller = new AgentController(
                new BakingAgent(new LLMClient(provider), new ToolManager(List.of()), new AgentMemory()),
                ingredients, new RecipeRepository(), new FavouritesManager(temp.resolve("faves.json")),
                new ShoppingListGenerator(ingredients));

        assertMissingFlour(controller.checkIngredients(recipe, List.of()));
        assertEquals(4, controller.scaleRecipe(recipe, 4).getServings());
        assertMissingFlour(controller.generateShoppingList(recipe, List.of()).getItems());
        controller.addFavourite(recipe);
        assertEquals(List.of(recipe), controller.getFavourites());
        controller.removeFavourite(recipe);
        assertTrue(controller.getFavourites().isEmpty());
        assertTrue(provider.getPrompts().isEmpty());
    }

    private static void assertMissingFlour(List<Ingredient> items) {
        assertEquals(1, items.size());
        assertEquals("Flour", items.getFirst().getName());
        assertEquals(2, items.getFirst().getQuantity());
        assertEquals("cups", items.getFirst().getUnit());
    }

    @Test
    void processRequestDelegatesToAgent() {
        MockLLMProvider provider = new MockLLMProvider("{\"intent\":\"unclear\"}");
        IngredientManager ingredients = new IngredientManager();
        AgentController controller = new AgentController(
                new BakingAgent(new LLMClient(provider), new ToolManager(List.of()), new AgentMemory()),
                ingredients, new RecipeRepository(), new FavouritesManager(temp.resolve("faves.json")),
                new ShoppingListGenerator(ingredients));
        assertTrue(controller.processRequest(new baking.model.BakingRequest("help", 0, 0,
                List.of(), List.of(), "")).contains("Would you like"));
        assertEquals(1, provider.getPrompts().size());
    }
}
