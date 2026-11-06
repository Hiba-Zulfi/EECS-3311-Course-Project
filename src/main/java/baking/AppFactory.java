package baking;

import baking.agent.AgentMemory;
import baking.agent.BakingAgent;
import baking.controller.AgentController;
import baking.llm.GeminiAdapter;
import baking.llm.LLMClient;
import baking.llm.LLMConfig;
import baking.service.FavouritesManager;
import baking.service.IngredientManager;
import baking.service.RecipeRepository;
import baking.service.ShoppingListGenerator;
import baking.tools.ToolManager;

/** Builds the application graph from local configuration. */
public final class AppFactory {
    private AppFactory() { }

    public static AgentController createController() {
        LLMClient client = new LLMClient(new GeminiAdapter(LLMConfig.fromEnvironment()));
        RecipeRepository recipes = new RecipeRepository();
        IngredientManager ingredients = new IngredientManager();
        BakingAgent agent = new BakingAgent(client,
                new ToolManager(client, recipes, ingredients), new AgentMemory());
        return new AgentController(agent, ingredients, recipes, new FavouritesManager(),
                new ShoppingListGenerator(ingredients));
    }
}
