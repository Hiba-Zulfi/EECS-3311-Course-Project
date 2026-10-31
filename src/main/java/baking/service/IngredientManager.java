package baking.service;

import baking.model.Ingredient;
import baking.model.Recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Deterministic comparison of recipe requirements with ingredients on hand. */
public class IngredientManager {
    private List<Ingredient> availableIngredients;

    public IngredientManager() {
        this(List.of());
    }

    public IngredientManager(List<Ingredient> availableIngredients) {
        setAvailableIngredients(availableIngredients);
    }

    public void setAvailableIngredients(List<Ingredient> availableIngredients) {
        this.availableIngredients = List.copyOf(Objects.requireNonNull(availableIngredients, "availableIngredients"));
    }

    /** Returns the missing quantities for a recipe using the currently configured pantry. */
    public List<Ingredient> checkAvailability(Recipe recipe) {
        return findMissingIngredients(recipe);
    }

    public List<Ingredient> checkAvailability(Recipe recipe, List<Ingredient> available) {
        Objects.requireNonNull(recipe, "recipe");
        return compareIngredients(recipe.getIngredients(), available);
    }

    public List<Ingredient> findMissingIngredients(Recipe recipe) {
        Objects.requireNonNull(recipe, "recipe");
        return compareIngredients(recipe.getIngredients(), availableIngredients);
    }

    /** Returns each required ingredient's shortfall after combining matching pantry quantities. */
    public List<Ingredient> compareIngredients(List<Ingredient> required, List<Ingredient> available) {
        Objects.requireNonNull(required, "required");
        Objects.requireNonNull(available, "available");
        List<Ingredient> missing = new ArrayList<>();
        for (Ingredient requirement : required) {
            Objects.requireNonNull(requirement, "required ingredient");
            double quantityOnHand = available.stream()
                    .filter(Objects::nonNull)
                    .filter(item -> sameIngredient(requirement, item))
                    .mapToDouble(Ingredient::getQuantity)
                    .sum();
            double shortfall = requirement.getQuantity() - quantityOnHand;
            if (shortfall > 0) {
                missing.add(new Ingredient(requirement.getName(), shortfall, requirement.getUnit()));
            }
        }
        return List.copyOf(missing);
    }

    private static boolean sameIngredient(Ingredient required, Ingredient available) {
        return required.getName().trim().equalsIgnoreCase(available.getName().trim())
                && required.getUnit().trim().equalsIgnoreCase(available.getUnit().trim());
    }
}
