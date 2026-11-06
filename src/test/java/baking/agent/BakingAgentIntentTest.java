package baking.agent;

import baking.llm.LLMClient;
import baking.llm.MockLLMProvider;
import baking.model.UserPreferences;
import baking.service.IngredientManager;
import baking.service.RecipeRepository;
import baking.tools.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BakingAgentIntentTest {
    private static final Map<String, Class<? extends AgentTool>> EXPECTED_TOOLS = Map.of(
            "baking request", BakingRequestTool.class,
            "recipe search", RecipeSearchTool.class,
            "time-based recommendation", TimeRecommendationTool.class,
            "substitution", SubstitutionTool.class,
            "taste customization", TasteCustomizationTool.class,
            "troubleshooting", TroubleshootingTool.class);

    @Test
    void classifiesEachSupportedIntentAndSelectsItsStrategy() {
        MockLLMProvider provider = new MockLLMProvider("{}");
        LLMClient llmClient = new LLMClient(provider);
        BakingAgent agent = agent(llmClient);
        EXPECTED_TOOLS.forEach((intent, expectedType) -> {
            provider.setResponse("{\"intent\":\"" + intent + "\"}");
            assertEquals(intent, agent.analyzeIntent("help me bake"));
            assertInstanceOf(expectedType, agent.selectTool(intent));
        });
    }

    @Test
    void normalizesModelIntentAliasesAndAppliesStoredPreferencesWithoutAnotherAnalysisCall() {
        MockLLMProvider provider = new MockLLMProvider("{\"intent\":\"recipe_search\"}");
        LLMClient llmClient = new LLMClient(provider);
        UserPreferences preferences = new UserPreferences(6);
        preferences.addDietaryRestriction("vegan");
        preferences.addPreference("less sweet");
        BakingAgent agent = new BakingAgent(llmClient,
                new baking.tools.ToolManager(llmClient, new RecipeRepository(), new IngredientManager()),
                new AgentMemory(), preferences);
        assertEquals("recipe search", agent.analyzeIntent("find a dessert"));
        assertInstanceOf(RecipeSearchTool.class, agent.selectTool("recipe_search"));
        agent.processRequest(new baking.model.BakingRequest("find a cake", 0, 0, List.of(), List.of(), ""));
        assertEquals(2, provider.getPrompts().size());
        assertEquals(List.of("vegan"), agent.getMemory().getLastBakingRequest().getDietaryRestrictions());
        assertEquals("less sweet", agent.getMemory().getLastBakingRequest().getTastePreference());
        assertEquals(6, agent.getMemory().getLastBakingRequest().getDesiredServings());
    }

    @Test
    void missingOrUnrecognizedIntentIsClassifiedAsUnclear() {
        MockLLMProvider provider = new MockLLMProvider("{\"intent\":\"something else\"}");
        BakingAgent agent = agent(new LLMClient(provider));
        assertEquals("unclear", agent.analyzeIntent("help"));
        provider.setResponse("{\"summary\":\"no intent field\"}");
        assertEquals("unclear", agent.analyzeIntent("help"));
    }

    private static BakingAgent agent(LLMClient llmClient) {
        return new BakingAgent(llmClient,
                new baking.tools.ToolManager(llmClient, new RecipeRepository(), new IngredientManager()),
                new AgentMemory());
    }
}
