package baking.controller;

import baking.agent.BakingAgent;
import baking.model.BakingRequest;
import baking.model.Ingredient;
import baking.model.Recipe;
import baking.model.ShoppingList;
import baking.service.FavouritesManager;
import baking.service.IngredientManager;
import baking.service.RecipeRepository;
import baking.service.ShoppingListGenerator;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Facade used by all application views. */
public final class AgentController {
    private final BakingAgent agent;
    private final IngredientManager ingredientManager;
    private final RecipeRepository recipeRepository;
    private final FavouritesManager favouritesManager;
    private final ShoppingListGenerator shoppingListGenerator;

    public AgentController(BakingAgent agent, IngredientManager ingredientManager,
                           RecipeRepository recipeRepository, FavouritesManager favouritesManager,
                           ShoppingListGenerator shoppingListGenerator) {
        this.agent = Objects.requireNonNull(agent, "agent");
        this.ingredientManager = Objects.requireNonNull(ingredientManager, "ingredientManager");
        this.recipeRepository = Objects.requireNonNull(recipeRepository, "recipeRepository");
        this.favouritesManager = Objects.requireNonNull(favouritesManager, "favouritesManager");
        this.shoppingListGenerator = Objects.requireNonNull(shoppingListGenerator, "shoppingListGenerator");
    }

    public String processRequest(BakingRequest request) { return agent.processRequest(request); }
    public void cancelClarification() { agent.cancelPendingClarification(); }
    public List<Ingredient> checkIngredients(Recipe recipe, List<Ingredient> available) {
        return ingredientManager.checkAvailability(recipe, available);
    }
    public Recipe scaleRecipe(Recipe recipe, int servings) {
        return recipe.scaleRecipe(recipe, servings);
    }
    public boolean addFavourite(Recipe recipe) { return favouritesManager.addFavourite(recipe); }
    public void removeFavourite(Recipe recipe) { favouritesManager.removeFavourite(recipe); }
    public List<Recipe> getFavourites() { return favouritesManager.getFavourites(); }
    public ShoppingList generateShoppingList(Recipe recipe, List<Ingredient> available) {
        return shoppingListGenerator.generate(recipe, available);
    }

    public Optional<Recipe> findRecipe(int recipeId) { return recipeRepository.findById(recipeId); }
    public List<Recipe> getRecipes() { return recipeRepository.getAllRecipes(); }
    public List<Recipe> getLastSearchResults() { return agent.getLastSearchResults(); }
}
