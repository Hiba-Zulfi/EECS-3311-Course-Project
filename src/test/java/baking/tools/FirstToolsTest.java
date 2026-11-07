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
    void recipeSearchReturnsCriteriaMatchesWithoutCallingTheLlm() {
        MockLLMProvider provider = new MockLLMProvider("{\"intent\":\"search\",\"searchTerm\":\"Chocolate Chip\"}");
        RecipeSearchTool tool = new RecipeSearchTool(new RecipeRepository(), new LLMClient(provider));
        List<Recipe> matches = tool.searchRecipes("find chocolate chip cookies");
        assertTrue(matches.size() >= 2);
        assertTrue(matches.getFirst().getName().equals("Classic Chocolate Chip Cookies"));
        assertTrue(tool.execute("find chocolate chip cookies").contains("Matching recipes"));
        assertTrue(provider.getPrompts().isEmpty());

        RecipeSearchTool noMatches = new RecipeSearchTool(new RecipeRepository(),
                new LLMClient(new MockLLMProvider("{\"searchTerm\":\"unicorn\"}")));
        assertTrue(noMatches.execute("unicorn").contains("I don't have a recipe for 'unicorn'"));
        assertTrue(noMatches.getLastSearchResults().isEmpty());
    }

    @Test
    void recipeSearchFindsFrenchMacaronsFromTheCatalog() {
        RecipeSearchTool tool = new RecipeSearchTool(new RecipeRepository(),
                new LLMClient(new MockLLMProvider("{}")));

        List<Recipe> matches = tool.searchRecipes("macarons");

        assertFalse(matches.isEmpty());
        assertEquals("French Macarons", matches.getFirst().getName());
    }

    @Test
    void unknownRequestDoesNotPresentAnUnrelatedRecipeAsAMatch() {
        RecipeSearchTool tool = new RecipeSearchTool(new RecipeRepository(),
                new LLMClient(new MockLLMProvider("{}")));

        String response = tool.execute("sushi");

        assertTrue(response.contains("I don't have a recipe for 'sushi' in my collection yet."));
        assertTrue(response.contains("show you all recipes"));
        assertFalse(response.contains("Classic Chocolate Chip Cookies"));
        assertTrue(tool.getLastSearchResults().isEmpty());
    }

    @Test
    void recipeSearchMatchesTheExactRequestDespiteServingsAndFillerWords() {
        RecipeRepository repository = new RecipeRepository();
        RecipeSearchTool exact = new RecipeSearchTool(repository,
                new LLMClient(new MockLLMProvider("{}")));
        List<Recipe> exactMatches = exact.searchRecipes(
                "I want chocolate chip cookies for 12 people", List.of(), 0);
        assertFalse(exactMatches.isEmpty());
        assertTrue(exactMatches.getFirst().getName().toLowerCase().contains("chocolate chip"));

        List<Recipe> fillerMatches = exact.searchRecipes(
                "I would really like to make some chocolate chip cookies for all of my people", List.of(), 0);
        assertEquals(exactMatches.stream().map(Recipe::getRecipeID).toList(),
                fillerMatches.stream().map(Recipe::getRecipeID).toList());
    }

    @Test
    void servingsDoNotExcludeRecipesAndExplicitHardFiltersStillApply() {
        RecipeSearchTool tool = new RecipeSearchTool(new RecipeRepository(),
                new LLMClient(new MockLLMProvider("{}")));
        List<Recipe> matches = tool.searchRecipes("I want chocolate chip cookies for 999 people", List.of(), 0);
        assertFalse(matches.isEmpty());
        assertTrue(matches.stream().anyMatch(recipe -> recipe.getServings() != 999));
        assertTrue(tool.searchRecipes("chocolate chip cookies", List.of(), 1).isEmpty());
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
