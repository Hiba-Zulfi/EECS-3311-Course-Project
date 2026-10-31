package baking.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A bakeable recipe and its timing, ingredient, and dietary information. */
public class Recipe {
    private final int recipeID;
    private final String name;
    private final String description;
    private int servings;
    private final int prepTime;
    private final int bakeTime;
    private List<Ingredient> ingredients;
    private final List<String> instructions;
    private final List<String> dietaryTags;

    public Recipe(int recipeID, String name, String description, int servings, int prepTime,
                  int bakeTime, List<Ingredient> ingredients, List<String> instructions) {
        this(recipeID, name, description, servings, prepTime, bakeTime, ingredients, instructions, List.of());
    }

    public Recipe(int recipeID, String name, String description, int servings, int prepTime,
                  int bakeTime, List<Ingredient> ingredients, List<String> instructions,
                  List<String> dietaryTags) {
        if (recipeID < 1) throw new IllegalArgumentException("recipeID must be positive");
        if (servings < 1) throw new IllegalArgumentException("servings must be positive");
        if (prepTime < 0 || bakeTime < 0) throw new IllegalArgumentException("times must be non-negative");
        this.recipeID = recipeID;
        this.name = requireText(name, "name");
        this.description = Objects.requireNonNull(description, "description");
        this.servings = servings;
        this.prepTime = prepTime;
        this.bakeTime = bakeTime;
        updateIngredients(ingredients);
        this.instructions = List.copyOf(Objects.requireNonNull(instructions, "instructions"));
        this.dietaryTags = List.copyOf(Objects.requireNonNull(dietaryTags, "dietaryTags"));
    }

    public int getRecipeID() { return recipeID; }
    public int getRecipeId() { return recipeID; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getServings() { return servings; }
    public int getPrepTime() { return prepTime; }
    public int getBakeTime() { return bakeTime; }
    public int getTotalTime() { return prepTime + bakeTime; }
    public List<Ingredient> getIngredients() { return List.copyOf(ingredients); }
    public List<String> getInstructions() { return instructions; }
    public List<String> getDietaryTags() { return dietaryTags; }

    public void updateIngredients(List<Ingredient> ingredients) {
        this.ingredients = new ArrayList<>(Objects.requireNonNull(ingredients, "ingredients"));
        if (this.ingredients.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("ingredients must not contain null");
        }
    }

    public void addIngredient(Ingredient ingredient) {
        ingredients.add(Objects.requireNonNull(ingredient, "ingredient"));
    }

    /** Returns a scaled copy whose ingredient quantities match the requested servings. */
    public Recipe scaleRecipe(Recipe recipe, int quantity) {
        return scaleRecipe(recipe, (double) quantity);
    }

    /** Accepts a numeric serving count and rejects non-finite or fractional values. */
    public Recipe scaleRecipe(Recipe recipe, double quantity) {
        Objects.requireNonNull(recipe, "recipe");
        if (!Double.isFinite(quantity) || quantity <= 0 || quantity != Math.rint(quantity)
                || quantity > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("servings must be a positive whole number");
        }
        int newServings = (int) quantity;
        double multiplier = quantity / recipe.servings;
        List<Ingredient> scaledIngredients = recipe.ingredients.stream()
                .map(i -> new Ingredient(i.getName(), i.getQuantity() * multiplier, i.getUnit()))
                .toList();
        return new Recipe(recipe.recipeID, recipe.name, recipe.description, newServings,
                recipe.prepTime, recipe.bakeTime, scaledIngredients, recipe.instructions, recipe.dietaryTags);
    }

    public Recipe scaleRecipe(Recipe recipe, String quantity) {
        final double parsed;
        try {
            parsed = Double.parseDouble(Objects.requireNonNull(quantity, "quantity"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("servings must be numeric", e);
        }
        return scaleRecipe(recipe, parsed);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
