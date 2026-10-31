package baking.tools;

import baking.llm.LLMClient;
import baking.llm.MockLLMProvider;
import baking.service.IngredientManager;
import baking.service.RecipeRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ToolManagerTest {
    @Test
    void defaultManagerBuildsAllSixToolsFromCreators() {
        LLMClient llmClient = new LLMClient(new MockLLMProvider("{\"servings\":4}"));
        ToolManager manager = new ToolManager(llmClient, new RecipeRepository(), new IngredientManager());
        assertEquals(6, manager.getAvailableTools().size());
        assertInstanceOf(BakingRequestTool.class, manager.getTool("BakingRequestTool"));
        assertInstanceOf(RecipeSearchTool.class, manager.getTool("recipesearchtool"));
        assertInstanceOf(TimeRecommendationTool.class, manager.getTool("TimeRecommendationTool"));
        assertInstanceOf(SubstitutionTool.class, manager.getTool("SubstitutionTool"));
        assertInstanceOf(TasteCustomizationTool.class, manager.getTool("TasteCustomizationTool"));
        assertInstanceOf(TroubleshootingTool.class, manager.getTool("TroubleshootingTool"));
    }

    @Test
    void registeredStrategyExecutesAndUnknownOrDuplicateToolsAreRejected() {
        AgentTool tool = new AgentTool() {
            @Override public String execute(String input) { return "handled: " + input; }
            @Override public String getName() { return "echo"; }
        };
        ToolManager manager = new ToolManager(List.of());
        manager.registerTool(tool);
        assertEquals("handled: cake", manager.executeTool("ECHO", "cake"));
        assertThrows(IllegalArgumentException.class, () -> manager.registerTool(tool));
        assertThrows(IllegalArgumentException.class, () -> manager.getTool("missing"));
        assertThrows(UnsupportedOperationException.class, () -> manager.getAvailableTools().clear());
    }

    @Test
    void creatorProducesANewToolInstance() {
        BakingRequestToolCreator creator = new BakingRequestToolCreator(
                new LLMClient(new MockLLMProvider("{\"servings\":4}")));
        assertNotSame(creator.createTool(), creator.createTool());
    }
}
