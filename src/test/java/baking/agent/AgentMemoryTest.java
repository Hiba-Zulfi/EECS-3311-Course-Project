package baking.agent;

import baking.model.BakingRequest;
import baking.model.Ingredient;
import baking.model.Recipe;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentMemoryTest {
    @Test
    void storesRecipeRequestSearchResultsAndConversationTurns() {
        AgentMemory memory = new AgentMemory();
        Recipe recipe = recipe();
        BakingRequest request = new BakingRequest("cake", 4, 30, List.of(), List.of("vegan"), "citrus");
        memory.setCurrentRecipe(recipe);
        memory.setLastSearchResults(List.of(recipe));
        memory.setLastBakingRequest(request);
        memory.addConversationTurn("user", "I want cake");
        memory.addConversationTurn("assistant", "Here is a recipe");

        assertSame(recipe, memory.getCurrentRecipe());
        assertEquals(List.of(recipe), memory.getLastSearchResults());
        assertSame(request, memory.getLastBakingRequest());
        assertEquals("user", memory.getRecentConversationTurns().getFirst().role());
        assertEquals("Here is a recipe", memory.getRecentConversationTurns().getLast().content());
        assertThrows(UnsupportedOperationException.class, () -> memory.getLastSearchResults().clear());
    }

    @Test
    void keepsOnlyTheMostRecentConfiguredNumberOfTurns() {
        AgentMemory memory = new AgentMemory(2);
        memory.addConversationTurn("user", "first");
        memory.addConversationTurn("assistant", "second");
        memory.addConversationTurn("user", "third");
        assertEquals(2, memory.getRecentConversationTurns().size());
        assertEquals("second", memory.getRecentConversationTurns().getFirst().content());
        assertThrows(IllegalArgumentException.class, () -> new AgentMemory(0));
    }

    @Test
    void copyAndReplaceSupportIndependentStagedUpdates() {
        AgentMemory original = new AgentMemory();
        original.setCurrentRecipe(recipe());
        original.addConversationTurn("user", "before");
        AgentMemory staged = original.copy();
        staged.addConversationTurn("assistant", "after");
        original.replaceWith(staged);
        assertEquals(2, original.getRecentConversationTurns().size());
        assertEquals("after", original.getRecentConversationTurns().getLast().content());
    }

    private static Recipe recipe() {
        return new Recipe(1, "Cake", "Test", 4, 10, 20,
                List.of(new Ingredient("flour", 2, "cups")), List.of("Bake"));
    }
}
