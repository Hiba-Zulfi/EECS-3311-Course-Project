package baking.service;

import baking.model.Recipe;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecipeRepositoryTest {
    private final RecipeRepository repository = new RecipeRepository();

    @Test
    void loadsTwentyFiveRecipesAcrossRequestedCategories() {
        List<Recipe> recipes = repository.getAllRecipes();
        assertEquals(25, recipes.size());
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
}
