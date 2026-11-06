package baking.view;

import baking.agent.AgentMemory;
import baking.agent.BakingAgent;
import baking.controller.AgentController;
import baking.llm.LLMClient;
import baking.llm.MockLLMProvider;
import baking.service.FavouritesManager;
import baking.service.IngredientManager;
import baking.service.RecipeRepository;
import baking.service.ShoppingListGenerator;
import baking.tools.ToolManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BakingCLITest {
    @TempDir Path temp;

    @Test
    void deterministicCommandsParseAndInvalidNumbersReportErrors() {
        MockLLMProvider provider = new MockLLMProvider("{\"intent\":\"unclear\"}");
        IngredientManager ingredients = new IngredientManager();
        AgentController controller = new AgentController(
                new BakingAgent(new LLMClient(provider), new ToolManager(List.of()), new AgentMemory()),
                ingredients, new RecipeRepository(), new FavouritesManager(temp.resolve("faves.json")),
                new ShoppingListGenerator(ingredients));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        BakingCLI cli = new BakingCLI(controller, new ByteArrayInputStream(new byte[0]),
                new PrintStream(output), new PrintStream(errors));

        cli.submitCommand("/select 1");
        cli.submitCommand("/ingredients Flour:2 cups");
        cli.submitCommand("/check");
        cli.submitCommand("/scale nope");
        cli.submitCommand("");

        assertTrue(output.toString().contains("Selected:"));
        assertTrue(output.toString().contains("Ingredients on hand:"));
        assertTrue(errors.toString().contains("positive whole number"));
        assertTrue(errors.toString().contains("Please enter"));
        assertTrue(provider.getPrompts().isEmpty());
    }

    @Test
    void clarificationAnswerIsForwardedAsPlainText() {
        MockLLMProvider provider = new MockLLMProvider("{\"intent\":\"unclear\"}");
        IngredientManager ingredients = new IngredientManager();
        AgentController controller = new AgentController(
                new BakingAgent(new LLMClient(provider), new ToolManager(List.of()), new AgentMemory()),
                ingredients, new RecipeRepository(), new FavouritesManager(temp.resolve("faves2.json")),
                new ShoppingListGenerator(ingredients));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        BakingCLI cli = new BakingCLI(controller, new ByteArrayInputStream(new byte[0]),
                new PrintStream(output), System.err);

        cli.submitCommand("help me");
        cli.submitCommand("/quit");

        assertEquals(2, provider.getPrompts().size());
        assertTrue(provider.getPrompts().get(1).contains("/quit"));
    }
}
