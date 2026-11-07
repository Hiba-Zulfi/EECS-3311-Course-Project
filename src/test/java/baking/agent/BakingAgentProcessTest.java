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
import baking.tools.TimeRecommendationTool;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BakingAgentProcessTest {
    @Test
    void bakingRequestRunsRequestToolThenSearchAndStoresResultsAndPreferences() {
        MockLLMProvider provider = new MockLLMProvider("unused");
        provider.setResponses(
                "{\"intent\":\"baking request\",\"servings\":0,\"availableTime\":0,"
                        + "\"dietaryRestrictions\":[],\"tastePreference\":\"\"}");
        AgentMemory memory = new AgentMemory();
        UserPreferences preferences = new UserPreferences(6);
        preferences.addDietaryRestriction("vegetarian");
        preferences.addPreference("less sweet");
        BakingAgent agent = agent(provider, memory, preferences);

        String result = agent.processRequest(request("I want chocolate chip cookies"));

        assertTrue(result.contains("Matching recipes"));
        assertEquals(1, provider.getPrompts().size());
        assertTrue(provider.getPrompts().getFirst().contains("I want chocolate chip cookies"));
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
    void bakingRequestWithoutTimeSaysNotSpecified() {
        MockLLMProvider provider = new MockLLMProvider("{\"intent\":\"baking request\",\"servings\":4}");
        BakingAgent agent = agent(provider, new AgentMemory(), new UserPreferences());

        String result = agent.processRequest(request("I want a vanilla cake"));

        assertTrue(result.contains("time available: not specified"));
        assertFalse(result.contains("0 minutes available"));
        assertEquals(1, provider.getPrompts().size());
    }

    @Test
    void timeRequestPassesSearchOutputIntoRecommendationTool() {
        RecipeRepository repository = new RecipeRepository();
        String rankedCandidates = "[\"Classic Chocolate Chip Cookies\",\"Vegan Chocolate Chip Muffins\"]";
        MockLLMProvider provider = new MockLLMProvider("unused");
        provider.setResponses(
                "{\"intent\":\"time-based recommendation\",\"availableTime\":10}",
                rankedCandidates);
        AgentMemory memory = new AgentMemory();
        BakingAgent agent = new BakingAgent(new LLMClient(provider),
                new ToolManager(new LLMClient(provider), repository, new IngredientManager()), memory);

        String result = agent.processRequest(request("I have ten minutes for chocolate chip"));

        assertTrue(result.contains("No recipe fits within 10 minutes"));
        assertEquals(2, provider.getPrompts().size());
        assertTrue(provider.getPrompts().get(1).contains("Rank these baking options"));
        assertFalse(memory.getLastSearchResults().isEmpty());
        assertNotNull(memory.getCurrentRecipe());
        assertEquals(memory.getCurrentRecipe(), memory.getLastSearchResults().getFirst());
    }

    @Test
    void timeOnlyRequestUsesCatalogAndParsesMinutesWithoutIntentCall() {
        MockLLMProvider provider = new MockLLMProvider("[\"Classic Chocolate Chip Cookies\",\"Oatmeal Raisin Cookies\"]");
        BakingAgent agent = agent(provider, new AgentMemory(), new UserPreferences());

        String result = agent.processRequest(request("I only have 45 minutes"));

        assertTrue(result.contains("Recipes that fit within 45 minutes"));
        assertTrue(result.contains("[1] Classic Chocolate Chip Cookies"));
        assertEquals(1, provider.getPrompts().size());
        assertTrue(provider.getPrompts().getFirst().contains("Rank these baking options"));
    }

    @Test
    void timeOnlyRequestReusesLastSearchResultsAndSupportsHourExpressions() {
        MockLLMProvider provider = new MockLLMProvider("[\"Fast Lemon Cookies\"]");
        AgentMemory memory = new AgentMemory();
        Recipe previousResult = new Recipe(90, "Fast Lemon Cookies", "Quick lemon cookies", 4, 5, 5,
                List.of(new Ingredient("flour", 1, "cup")), List.of("Bake"));
        memory.setLastSearchResults(List.of(previousResult));
        BakingAgent agent = agent(provider, memory, new UserPreferences());

        String result = agent.processRequest(request("an hour and a half"));

        assertTrue(result.contains("[90] Fast Lemon Cookies"));
        assertTrue(provider.getPrompts().getFirst().contains("Fast Lemon Cookies"));
        assertEquals(90, TimeRecommendationTool.parseAvailableTime("an hour and a half"));
        assertEquals(90, TimeRecommendationTool.parseAvailableTime("1 hour and a half"));
        assertEquals(60, TimeRecommendationTool.parseAvailableTime("1 hour"));
        assertEquals(45, TimeRecommendationTool.parseAvailableTime("45 minutes"));
        assertEquals(1, provider.getPrompts().size());
    }

    @Test
    void noRecipeFitsReturnsTheShortestOptions() {
        MockLLMProvider provider = new MockLLMProvider("[]");
        TimeRecommendationTool tool = new TimeRecommendationTool(new RecipeRepository(), new LLMClient(provider));
        List<Recipe> candidates = List.of(
                new Recipe(80, "Long Bake", "", 4, 50, 40, List.of(), List.of()),
                new Recipe(81, "Short Bake", "", 4, 5, 10, List.of(), List.of()),
                new Recipe(82, "Middle Bake", "", 4, 20, 20, List.of(), List.of()));

        List<Recipe> result = tool.recommendRecipes(candidates, 1);

        assertEquals(List.of(81, 82, 80), result.stream().map(Recipe::getRecipeID).toList());
        assertTrue(tool.getLastExplanation().contains("shortest recipes"));
        assertEquals(1, provider.getPrompts().size());
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
                "{\"intent\":\"unclear\",\"servings\":12}");
        BakingAgent agent = agent(provider, new AgentMemory(), new UserPreferences());
        String result = agent.processRequest(request("I want chocolate chip cookies for 12 people"));
        assertTrue(result.contains("Matching recipes"));
        assertEquals(1, provider.getPrompts().size());
    }

    @Test
    void recipeClarificationAnswerIsCombinedWithOriginalRequest() {
        MockLLMProvider provider = new MockLLMProvider("unused");
        provider.setResponses(
                "{\"intent\":\"unclear\"}",
                "{\"intent\":\"RECIPE\",\"servings\":12}");
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
    void specialistRequestsUseOneCombinedAnalysisAndOneToolCall() {
        assertSpecialistCalls("substitution", "What can replace butter in cookies?", "Use oil instead.");
        assertSpecialistCalls("taste customization", "Make these cookies less sweet", "Reduce sugar by 20%.");
        assertSpecialistCalls("troubleshooting", "My cake sank in the middle", "Check the leavening and oven temperature.");
    }

    @Test
    void rateLimitFailureGetsSpecificUserMessage() {
        MockLLMProvider provider = new MockLLMProvider("unused");
        provider.setFailureOnCall(1, new LLMException("Gemini rate limit reached (HTTP 429).", 429));
        BakingAgent agent = agent(provider, new AgentMemory(), new UserPreferences());
        assertTrue(agent.processRequest(request("suggest a substitution"))
                .contains("The AI service is busy, please try again"));
    }

    @Test
    void llmFailureReturnsFriendlyErrorAndLeavesAllMemoryUnchanged() {
        MockLLMProvider provider = new MockLLMProvider("unused");
        provider.setFailureOnCall(1, new LLMException("mock provider unavailable"));
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

        assertTrue(result.contains("AI service could not complete"));
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

    private static void assertSpecialistCalls(String intent, String request, String toolReply) {
        MockLLMProvider provider = new MockLLMProvider("unused");
        provider.setResponses("{\"intent\":\"" + intent + "\"}", toolReply);
        BakingAgent agent = agent(provider, new AgentMemory(), new UserPreferences());
        assertFalse(agent.processRequest(request(request)).isBlank());
        assertEquals(2, provider.getPrompts().size(), intent);
    }

    private static BakingRequest request(String text) {
        return new BakingRequest(text, 0, 0, List.of(), List.of(), "");
    }

    private static Recipe recipe() {
        return new Recipe(99, "Previous cake", "Stored", 4, 15, 30,
                List.of(new Ingredient("flour", 2, "cups")), List.of("Bake"));
    }
}
