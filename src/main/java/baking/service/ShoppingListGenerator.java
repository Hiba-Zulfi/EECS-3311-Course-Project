package baking.service;

import baking.model.Ingredient;
import baking.model.Recipe;
import baking.model.ShoppingList;

import java.util.List;
import java.util.Objects;

/** Builds shopping lists from deterministic ingredient availability results. */
public class ShoppingListGenerator {
    private final IngredientManager ingredientManager;

    public ShoppingListGenerator() {
        this(new IngredientManager());
    }

    public ShoppingListGenerator(IngredientManager ingredientManager) {
        this.ingredientManager = Objects.requireNonNull(ingredientManager, "ingredientManager");
    }

    /** Generates a list using the pantry configured in the IngredientManager. */
    public ShoppingList generate(Recipe recipe) {
        Objects.requireNonNull(recipe, "recipe");
        return ShoppingList.fromMissingIngredients(ingredientManager.findMissingIngredients(recipe));
    }

    /** Generates a list using the supplied available ingredients. */
    public ShoppingList generate(Recipe recipe, List<Ingredient> availableIngredients) {
        Objects.requireNonNull(recipe, "recipe");
        return ShoppingList.fromMissingIngredients(
                ingredientManager.compareIngredients(recipe.getIngredients(), availableIngredients));
    }
}
