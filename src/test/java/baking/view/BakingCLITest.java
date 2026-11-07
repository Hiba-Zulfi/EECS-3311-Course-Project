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
    void recipeSearchShowsIdsAndAtMostFiveMatches() {
        MockLLMProvider provider = new MockLLMProvider("{\"intent\":\"recipe search\"}");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        BakingCLI cli = createCli(provider, output, "results.json");

        cli.submitCommand("cookies");

        String result = output.toString();
        assertTrue(result.contains("[1]"));
        assertTrue(result.contains("Classic Chocolate Chip Cookies"));
        assertTrue(result.contains("(1 more matches not shown)"));
        assertEquals(5, result.split("\\[\\d+\\]").length - 1);
    }

    @Test
    void idOrdinalAndPartialNameSelectionsUseLastResultsWithoutCallingLlm() {
        MockLLMProvider provider = new MockLLMProvider("{\"intent\":\"recipe search\"}");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        BakingCLI cli = createCli(provider, output, "selection.json");
        cli.submitCommand("cookies");
        int callsAfterSearch = provider.getPrompts().size();

        cli.submitCommand("select the second option");
        cli.submitCommand("/select Peanut Butter");
        cli.submitCommand("4");

        String result = output.toString();
        assertTrue(result.contains("Selected: [2]"));
        assertTrue(result.contains("Selected: [3] Peanut Butter Cookies"));
        assertTrue(result.contains("Selected: [4] Lemon Shortbread Cookies"));
        assertTrue(result.contains("minutes; serves"));
        assertEquals(callsAfterSearch, provider.getPrompts().size());
    }

    @Test
    void slashQuitDuringClarificationExitsWithoutSubmittingItAsAnAnswer() {
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

        assertEquals(1, provider.getPrompts().size());
    }

    @Test
    void helpDuringClarificationIsCommandAndKeepsTheQuestionPending() {
        MockLLMProvider provider = new MockLLMProvider("{\"intent\":\"unclear\"}");
        IngredientManager ingredients = new IngredientManager();
        AgentController controller = new AgentController(
                new BakingAgent(new LLMClient(provider), new ToolManager(List.of()), new AgentMemory()),
                ingredients, new RecipeRepository(), new FavouritesManager(temp.resolve("faves-help.json")),
                new ShoppingListGenerator(ingredients));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        BakingCLI cli = new BakingCLI(controller, new ByteArrayInputStream(new byte[0]),
                new PrintStream(output), System.err);

        cli.submitCommand("hello");
        cli.submitCommand("/help");
        cli.submitCommand("recipe");

        assertTrue(output.toString().contains("Commands:"));
        assertEquals(2, provider.getPrompts().size());
        assertTrue(provider.getPrompts().get(1).contains("hello"));
        assertTrue(provider.getPrompts().get(1).contains("Clarification answer: recipe"));
    }

    @Test
    void cancelDuringClarificationClearsPendingRequestAndRestoresNormalInput() {
        MockLLMProvider provider = new MockLLMProvider("{\"intent\":\"unclear\"}");
        IngredientManager ingredients = new IngredientManager();
        AgentController controller = new AgentController(
                new BakingAgent(new LLMClient(provider), new ToolManager(List.of()), new AgentMemory()),
                ingredients, new RecipeRepository(), new FavouritesManager(temp.resolve("faves3.json")),
                new ShoppingListGenerator(ingredients));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        BakingCLI cli = new BakingCLI(controller, new ByteArrayInputStream(new byte[0]),
                new PrintStream(output), System.err);

        cli.submitCommand("help me");
        cli.submitCommand("/cancel");
        cli.submitCommand("cookies");

        assertEquals(2, provider.getPrompts().size());
        assertTrue(provider.getPrompts().get(1).contains("User request: cookies"));
        assertFalse(provider.getPrompts().get(1).contains("help me"));
        assertTrue(output.toString().contains("Clarification cancelled."));
    }

    @Test
    void quitAndExitWordsAreCommandsDuringClarification() {
        MockLLMProvider provider = new MockLLMProvider("{\"intent\":\"unclear\"}");
        IngredientManager ingredients = new IngredientManager();
        AgentController controller = new AgentController(
                new BakingAgent(new LLMClient(provider), new ToolManager(List.of()), new AgentMemory()),
                ingredients, new RecipeRepository(), new FavouritesManager(temp.resolve("faves4.json")),
                new ShoppingListGenerator(ingredients));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        BakingCLI cli = new BakingCLI(controller, new ByteArrayInputStream(new byte[0]),
                new PrintStream(output), System.err);

        cli.submitCommand("help me");
        cli.submitCommand("quit");
        cli.submitCommand("exit");
        assertEquals(1, provider.getPrompts().size());
    }

    private BakingCLI createCli(MockLLMProvider provider, ByteArrayOutputStream output, String favouritesFile) {
        IngredientManager ingredients = new IngredientManager();
        AgentController controller = new AgentController(
                new BakingAgent(new LLMClient(provider), new ToolManager(new LLMClient(provider),
                        new RecipeRepository(), ingredients), new AgentMemory()),
                ingredients, new RecipeRepository(), new FavouritesManager(temp.resolve(favouritesFile)),
                new ShoppingListGenerator(ingredients));
        return new BakingCLI(controller, new ByteArrayInputStream(new byte[0]),
                new PrintStream(output), System.err);
    }
}
