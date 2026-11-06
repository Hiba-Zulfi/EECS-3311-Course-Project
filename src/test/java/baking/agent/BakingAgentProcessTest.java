package baking.agent;

import baking.llm.LLMClient;
import baking.llm.LLMException;
import baking.llm.MockLLMProvider;
import baking.model.BakingRequest;
import baking.model.Ingredient;
import baking.model.Recipe;
import baking.model.UserPreferences;
import baking.service.IngredientManager;
import baking.service.RecipeRepository;
import baking.tools.ToolManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BakingAgentProcessTest {
    @Test
    void bakingRequestRunsRequestToolThenSearchAndStoresResultsAndPreferences() {
        MockLLMProvider provider = new MockLLMProvider("unused");
        provider.setResponses(
                "{\"intent\":\"baking request\"}",
                "{\"servings\":0,\"availableTime\":0,\"dietaryRestrictions\":[],\"tastePreference\":\"\"}");
        AgentMemory memory = new AgentMemory();
        UserPreferences preferences = new UserPreferences(6);
        preferences.addDietaryRestriction("vegetarian");
        preferences.addPreference("less sweet");
        BakingAgent agent = agent(provider, memory, preferences);

        String result = agent.processRequest(request("I want chocolate chip cookies"));

        assertTrue(result.contains("Matching recipes"));
        assertEquals(2, provider.getPrompts().size());
        assertTrue(provider.getPrompts().get(1).contains("Requested servings: 0")
                || provider.getPrompts().get(1).contains("I want chocolate chip cookies"));
        assertEquals(6, memory.getLastBakingRequest().getDesiredServings());
        assertEquals(List.of("vegetarian"), memory.getLastBakingRequest().getDietaryRestrictions());
        assertEquals("less sweet", memory.getLastBakingRequest().getTastePreference());
        assertFalse(memory.getLastSearchResults().isEmpty());
        assertTrue(memory.getLastSearchResults().stream().anyMatch(recipe ->
                recipe.getName().toLowerCase().contains("chocolate chip")));
        assertEquals(memory.getLastSearchResults().getFirst(), memory.getCurrentRecipe());
        assertEquals(2, memory.getRecentConversationTurns().size());
    }

    @Test
    void timeRequestPassesSearchOutputIntoRecommendationTool() {
        RecipeRepository repository = new RecipeRepository();
        String rankedCandidates = "[\"Classic Chocolate Chip Cookies\",\"Vegan Chocolate Chip Muffins\"]";
        MockLLMProvider provider = new MockLLMProvider("unused");
        provider.setResponses(
                "{\"intent\":\"time-based recommendation\"}",
                "{\"searchTerm\":\"Chocolate Chip\"}",
                "{\"availableTime\":10}",
                rankedCandidates);
        AgentMemory memory = new AgentMemory();
        BakingAgent agent = new BakingAgent(new LLMClient(provider),
                new ToolManager(new LLMClient(provider), repository, new IngredientManager()), memory);

        String result = agent.processRequest(request("I have ten minutes for chocolate chip"));

        assertTrue(result.contains("No recipe fits within 10 minutes"));
        assertEquals(4, provider.getPrompts().size());
        assertTrue(provider.getPrompts().get(2).contains("Previous recipe search output"));
        assertTrue(provider.getPrompts().get(2).contains("Matching recipes"));
        assertFalse(memory.getLastSearchResults().isEmpty());
        assertNotNull(memory.getCurrentRecipe());
        assertEquals(memory.getCurrentRecipe(), memory.getLastSearchResults().getFirst());
    }

    @Test
    void unclearRequestAsksForClarificationWithoutExecutingTools() {
        MockLLMProvider provider = new MockLLMProvider("{\"intent\":\"unclear\"}");
        AgentMemory memory = new AgentMemory();
        BakingAgent agent = agent(provider, memory, new UserPreferences());
        Recipe previous = recipe();
        memory.setCurrentRecipe(previous);

        String result = agent.processRequest(request("hello"));

        assertTrue(result.contains("Would you like a recipe"));
        assertEquals(1, provider.getPrompts().size());
        assertSame(previous, memory.getCurrentRecipe());
        assertTrue(memory.getRecentConversationTurns().stream().anyMatch(turn -> turn.role().equals("assistant")));
    }

    @Test
    void namedFoodRequestIsRecognizedEvenIfModelMarksItUnclear() {
        MockLLMProvider provider = new MockLLMProvider("unused");
        provider.setResponses(
                "{\"intent\":\"unclear\"}",
                "{\"servings\":12}");
        BakingAgent agent = agent(provider, new AgentMemory(), new UserPreferences());
        String result = agent.processRequest(request("I want chocolate chip cookies for 12 people"));
        assertTrue(result.contains("Matching recipes"));
        assertEquals(2, provider.getPrompts().size());
    }

    @Test
    void recipeClarificationAnswerIsCombinedWithOriginalRequest() {
        MockLLMProvider provider = new MockLLMProvider("unused");
        provider.setResponses(
                "{\"intent\":\"unclear\"}",
                "{\"intent\":\"RECIPE\"}",
                "{\"servings\":12}",
                "{\"searchTerm\":\"Chocolate Chip\"}");
        BakingAgent agent = agent(provider, new AgentMemory(), new UserPreferences());
        assertTrue(agent.processRequest(request("I need help with dessert"))
                .contains("Would you like a recipe"));
        String result = agent.processRequest(request("recipe"));
        assertTrue(result.contains("Matching recipes"));
        assertTrue(provider.getPrompts().get(1).contains("I need help with dessert"));
        assertTrue(provider.getPrompts().get(1).contains("Clarification answer: recipe"));
    }

    @Test
    void trulyUnclearInputStillAsksForClarification() {
        MockLLMProvider provider = new MockLLMProvider("{\"intent\":\"unclear\"}");
        BakingAgent agent = agent(provider, new AgentMemory(), new UserPreferences());
        assertTrue(agent.processRequest(request("hello" )).contains("Would you like a recipe"));
        assertEquals(1, provider.getPrompts().size());
    }

    @Test
    void llmFailureReturnsFriendlyErrorAndLeavesAllMemoryUnchanged() {
        MockLLMProvider provider = new MockLLMProvider("unused");
        provider.setResponses("{\"intent\":\"baking request\"}");
        provider.setFailureOnCall(2, new LLMException("mock provider unavailable"));
        AgentMemory memory = new AgentMemory();
        Recipe previous = recipe();
        BakingRequest previousRequest = new BakingRequest("old request", 4, 30, List.of(), List.of(), "vanilla");
        memory.setCurrentRecipe(previous);
        memory.setLastSearchResults(List.of(previous));
        memory.setLastBakingRequest(previousRequest);
        memory.addConversationTurn("user", "old turn");
        List<AgentMemory.ConversationTurn> previousTurns = memory.getRecentConversationTurns();
        BakingAgent agent = agent(provider, memory, new UserPreferences());

        String result = agent.processRequest(request("find me a cake"));

        assertTrue(result.contains("couldn't complete"));
        assertSame(previous, memory.getCurrentRecipe());
        assertEquals(List.of(previous), memory.getLastSearchResults());
        assertSame(previousRequest, memory.getLastBakingRequest());
        assertEquals(previousTurns, memory.getRecentConversationTurns());
    }

    private static BakingAgent agent(MockLLMProvider provider, AgentMemory memory, UserPreferences preferences) {
        LLMClient client = new LLMClient(provider);
        return new BakingAgent(client, new ToolManager(client, new RecipeRepository(), new IngredientManager()),
                memory, preferences);
    }

    private static BakingRequest request(String text) {
        return new BakingRequest(text, 0, 0, List.of(), List.of(), "");
    }

    private static Recipe recipe() {
        return new Recipe(99, "Previous cake", "Stored", 4, 15, 30,
                List.of(new Ingredient("flour", 2, "cups")), List.of("Bake"));
    }
}
