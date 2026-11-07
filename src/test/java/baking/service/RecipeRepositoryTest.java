package baking.service;

import baking.model.Recipe;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RecipeRepositoryTest {
    private final RecipeRepository repository = new RecipeRepository();

    @Test
    void loadsThirtySevenRecipesAcrossRequestedCategories() {
        List<Recipe> recipes = repository.getAllRecipes();
        assertEquals(37, recipes.size());
        assertTrue(recipes.stream().anyMatch(recipe -> recipe.getName().contains("Cookie")));
        assertTrue(recipes.stream().anyMatch(recipe -> recipe.getName().contains("Brownie")));
        assertTrue(recipes.stream().anyMatch(recipe -> recipe.getName().contains("Bread")));
        assertTrue(recipes.stream().anyMatch(recipe -> recipe.getName().contains("Muffin")));
        assertTrue(recipes.stream().anyMatch(recipe -> recipe.getName().contains("Cake")));
        assertTrue(recipes.stream().map(Recipe::getTotalTime).distinct().count() > 5);
    }

    @Test
    void supportsIdNameAndDietaryTagQueries() {
        assertEquals("Fudgy Brownies", repository.findById(7).orElseThrow().getName());
        assertTrue(repository.findById(999).isEmpty());
        assertEquals(2, repository.searchByName("chip").size());
        assertTrue(repository.findByDietaryTag("VEGAN").size() >= 5);
        assertTrue(repository.findByDietaryTag("unknown").isEmpty());
    }

    @Test
    void loadedRecipesIncludeIngredientsInstructionsAndTags() {
        Recipe bread = repository.findById(11).orElseThrow();
        assertFalse(bread.getIngredients().isEmpty());
        assertFalse(bread.getInstructions().isEmpty());
        assertTrue(bread.getDietaryTags().contains("vegan"));
    }

    @Test
    void everyRecipeHasCompleteValidIngredientsAndConsistentDietaryTags() {
        Set<String> simpleRecipeExceptions = Set.of("Flourless Chocolate Cake");
        Set<String> validUnits = Set.of("cup", "cups", "tbsp", "tsp", "g", "kg", "oz", "lb",
                "ml", "l", "pinch", "count");

        for (Recipe recipe : repository.getAllRecipes()) {
            int minimumIngredients = simpleRecipeExceptions.contains(recipe.getName()) ? 4 : 6;
            assertTrue(recipe.getIngredients().size() >= minimumIngredients,
                    () -> recipe.getName() + " has too few ingredients");
            recipe.getIngredients().forEach(ingredient -> {
                assertTrue(Double.isFinite(ingredient.getQuantity()) && ingredient.getQuantity() > 0,
                        () -> recipe.getName() + " has a non-positive quantity for " + ingredient.getName());
                assertTrue(validUnits.contains(ingredient.getUnit().toLowerCase(Locale.ROOT)),
                        () -> recipe.getName() + " has an unsupported unit: " + ingredient.getUnit());
            });

            List<String> names = recipe.getIngredients().stream()
                    .map(ingredient -> ingredient.getName().toLowerCase(Locale.ROOT)).toList();
            if (recipe.getDietaryTags().stream().anyMatch(tag -> tag.equalsIgnoreCase("vegan"))) {
                assertFalse(names.stream().anyMatch(name -> name.matches(".*\\b(egg|eggs|butter|milk|yogurt|sour cream|cream cheese)\\b.*")),
                        () -> recipe.getName() + " is tagged vegan but contains an animal ingredient");
            }
            if (recipe.getDietaryTags().stream().anyMatch(tag -> tag.equalsIgnoreCase("gluten-free"))) {
                assertFalse(names.stream().anyMatch(name -> name.contains("wheat flour")
                                || name.equals("all-purpose flour") || name.equals("bread flour")
                                || name.equals("cake flour")),
                        () -> recipe.getName() + " is tagged gluten-free but contains wheat flour");
            }
        }
    }
}
