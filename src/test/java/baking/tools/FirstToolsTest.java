package baking.tools;

import baking.llm.LLMClient;
import baking.llm.MockLLMProvider;
import baking.model.BakingRequest;
import baking.model.Recipe;
import baking.service.RecipeRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FirstToolsTest {
    @Test
    void bakingRequestToolBuildsPopulatedRequestAndSummary() {
        MockLLMProvider provider = new MockLLMProvider("{\"intent\":\"find_recipe\",\"servings\":8,"
                + "\"availableTime\":45,\"dietaryRestrictions\":[\"vegan\",\"dairy-free\"],"
                + "\"tastePreference\":\"less sweet\"}");
        BakingRequestTool tool = new BakingRequestTool(new LLMClient(provider));
        BakingRequest request = tool.parseRequest("I need a vegan bake for 8 in 45 minutes, less sweet");
        assertEquals(8, request.getDesiredServings());
        assertEquals(45, request.getAvailableTime());
        assertEquals(List.of("vegan", "dairy-free"), request.getDietaryRestrictions());
        assertEquals("less sweet", request.getTastePreference());
        assertTrue(tool.execute("I need a vegan bake").contains("Baking request parsed"));
        assertEquals("BakingRequestTool", tool.getName());
    }

    @Test
    void recipeSearchReturnsCriteriaMatchesAndAnObservableFallback() {
        RecipeSearchTool tool = new RecipeSearchTool(new RecipeRepository(),
                new LLMClient(new MockLLMProvider("{\"intent\":\"search\",\"searchTerm\":\"Chocolate Chip\"}")));
        List<Recipe> matches = tool.searchRecipes("find chocolate chip cookies");
        assertEquals(2, matches.size());
        assertTrue(tool.execute("find chocolate chip cookies").contains("Matching recipes"));

        RecipeSearchTool noMatches = new RecipeSearchTool(new RecipeRepository(),
                new LLMClient(new MockLLMProvider("{\"searchTerm\":\"unicorn\"}")));
        assertTrue(noMatches.execute("unicorn cake").contains("No recipes matched"));
    }

    @Test
    void timeRecommendationsFilterRankAndExplainFallback() {
        RecipeRepository repository = new RecipeRepository();
        List<Recipe> candidates = repository.getAllRecipes().stream()
                .filter(recipe -> recipe.getTotalTime() <= 30).toList();
        String rankedResponse = candidates.stream().map(Recipe::getName)
                .map(name -> "\"" + name.replace("\"", "\\\"") + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        TimeRecommendationTool fits = new TimeRecommendationTool(repository,
                new LLMClient(new MockLLMProvider(rankedResponse)));
        List<Recipe> fitRecommendations = fits.recommendRecipes(repository.getAllRecipes(), 30);
        assertFalse(fitRecommendations.isEmpty());
        assertTrue(fitRecommendations.stream().allMatch(recipe -> recipe.getTotalTime() <= 30));
        assertTrue(fits.getLastExplanation().contains("fit within 30 minutes"));

        TimeRecommendationTool fallback = new TimeRecommendationTool(repository,
                new LLMClient(new MockLLMProvider("[\"Banana Bread\",\"Lemon Drizzle Cake\"]")));
        List<Recipe> alternatives = fallback.recommendRecipes(repository.getAllRecipes(), 1);
        assertFalse(alternatives.isEmpty());
        assertTrue(fallback.getLastExplanation().contains("No recipe fits"));
    }
}
