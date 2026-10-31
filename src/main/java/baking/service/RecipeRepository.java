package baking.service;

import baking.model.Ingredient;
import baking.model.Recipe;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Loads and queries the bundled baking recipe catalog. */
public class RecipeRepository {
    private static final String DEFAULT_RESOURCE = "/recipes.json";

    private final List<Recipe> recipes;

    public RecipeRepository() {
        InputStream resource = RecipeRepository.class.getResourceAsStream(DEFAULT_RESOURCE);
        if (resource == null) throw new IllegalStateException("Missing classpath resource " + DEFAULT_RESOURCE);
        try (resource) {
            recipes = load(resource);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load recipes from " + DEFAULT_RESOURCE, e);
        }
    }

    /** Creates a repository from a JSON stream, useful for alternate catalogs and tests. */
    public RecipeRepository(InputStream source) throws IOException {
        recipes = load(source);
    }

    public List<Recipe> getAllRecipes() {
        return List.copyOf(recipes);
    }

    public Optional<Recipe> findById(int recipeID) {
        return recipes.stream().filter(recipe -> recipe.getRecipeID() == recipeID).findFirst();
    }

    public List<Recipe> searchByName(String query) {
        if (query == null || query.isBlank()) return getAllRecipes();
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        return recipes.stream()
                .filter(recipe -> recipe.getName().toLowerCase(Locale.ROOT).contains(normalized))
                .toList();
    }

    public List<Recipe> findByDietaryTag(String tag) {
        if (tag == null || tag.isBlank()) return List.of();
        String normalized = tag.trim().toLowerCase(Locale.ROOT);
        return recipes.stream()
                .filter(recipe -> recipe.getDietaryTags().stream()
                        .anyMatch(value -> value.toLowerCase(Locale.ROOT).equals(normalized)))
                .toList();
    }

    private static List<Recipe> load(InputStream source) throws IOException {
        if (source == null) throw new IllegalArgumentException("source must not be null");
        JsonNode root = new ObjectMapper().readTree(source);
        if (root == null || !root.isArray()) throw new IOException("Recipe JSON must contain an array");
        List<Recipe> loaded = new ArrayList<>();
        for (JsonNode node : root) {
            int id = requiredInt(node, "recipeID");
            String name = requiredText(node, "name");
            String description = requiredText(node, "description");
            int servings = requiredInt(node, "servings");
            int prepTime = requiredInt(node, "prepTime");
            int bakeTime = requiredInt(node, "bakeTime");

            JsonNode ingredientNodes = node.path("ingredients");
            if (!ingredientNodes.isArray()) throw new IOException("ingredients must be an array for " + name);
            List<Ingredient> ingredients = new ArrayList<>();
            for (JsonNode ingredient : ingredientNodes) {
                ingredients.add(new Ingredient(requiredText(ingredient, "name"),
                        requiredDouble(ingredient, "quantity"), requiredText(ingredient, "unit")));
            }
            List<String> instructions = stringArray(node.path("instructions"), "instructions", name);
            List<String> tags = stringArray(node.path("dietaryTags"), "dietaryTags", name);
            loaded.add(new Recipe(id, name, description, servings, prepTime, bakeTime,
                    ingredients, instructions, tags));
        }
        return List.copyOf(loaded);
    }

    private static List<String> stringArray(JsonNode node, String field, String recipe) throws IOException {
        if (!node.isArray()) throw new IOException(field + " must be an array for " + recipe);
        List<String> values = new ArrayList<>();
        for (JsonNode value : node) {
            if (!value.isTextual()) throw new IOException(field + " values must be strings for " + recipe);
            values.add(value.asText());
        }
        return List.copyOf(values);
    }

    private static String requiredText(JsonNode node, String field) throws IOException {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) throw new IOException("Missing or invalid " + field);
        return value.asText();
    }

    private static int requiredInt(JsonNode node, String field) throws IOException {
        JsonNode value = node.path(field);
        if (!value.canConvertToInt()) throw new IOException("Missing or invalid " + field);
        return value.intValue();
    }

    private static double requiredDouble(JsonNode node, String field) throws IOException {
        JsonNode value = node.path(field);
        if (!value.isNumber() || !Double.isFinite(value.doubleValue())) throw new IOException("Missing or invalid " + field);
        return value.doubleValue();
    }
}
