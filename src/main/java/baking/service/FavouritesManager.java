package baking.service;

import baking.model.Ingredient;
import baking.model.Recipe;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Maintains and persists a user's favourite recipes as JSON. */
public class FavouritesManager {
    private final Path storageFile;
    private final ObjectMapper mapper = new ObjectMapper();
    private List<Recipe> favourites;

    public FavouritesManager() {
        this(Path.of("data", "favourites.json"));
    }

    public FavouritesManager(Path storageFile) {
        this.storageFile = Objects.requireNonNull(storageFile, "storageFile");
        this.favourites = load();
    }

    /** Returns false when a recipe with the same ID is already saved. */
    public boolean addFavourite(Recipe recipe) {
        Objects.requireNonNull(recipe, "recipe");
        if (favourites.stream().anyMatch(saved -> saved.getRecipeID() == recipe.getRecipeID())) return false;
        List<Recipe> updated = new ArrayList<>(favourites);
        updated.add(recipe);
        persist(updated);
        favourites = updated;
        return true;
    }

    /** Returns false when the recipe was not in the favourites. */
    public boolean removeFavourite(Recipe recipe) {
        Objects.requireNonNull(recipe, "recipe");
        List<Recipe> updated = new ArrayList<>(favourites);
        boolean removed = updated.removeIf(saved -> saved.getRecipeID() == recipe.getRecipeID());
        if (removed) {
            persist(updated);
            favourites = updated;
        }
        return removed;
    }

    public List<Recipe> getFavourites() {
        return List.copyOf(favourites);
    }

    private List<Recipe> load() {
        if (!Files.exists(storageFile)) return new ArrayList<>();
        try {
            JsonNode root = mapper.readTree(storageFile.toFile());
            if (root == null || !root.isArray()) throw new IOException("favourites JSON must be an array");
            List<Recipe> loaded = new ArrayList<>();
            for (JsonNode node : root) loaded.add(readRecipe(node));
            return loaded;
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Unable to load favourites from " + storageFile, e);
        }
    }

    private void persist(List<Recipe> recipes) {
        try {
            Path parent = storageFile.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            ArrayNode root = mapper.createArrayNode();
            for (Recipe recipe : recipes) root.add(writeRecipe(recipe));
            mapper.writerWithDefaultPrettyPrinter().writeValue(storageFile.toFile(), root);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to save favourites to " + storageFile, e);
        }
    }

    private ObjectNode writeRecipe(Recipe recipe) {
        ObjectNode node = mapper.createObjectNode();
        node.put("recipeID", recipe.getRecipeID());
        node.put("name", recipe.getName());
        node.put("description", recipe.getDescription());
        node.put("servings", recipe.getServings());
        node.put("prepTime", recipe.getPrepTime());
        node.put("bakeTime", recipe.getBakeTime());
        ArrayNode ingredients = node.putArray("ingredients");
        for (Ingredient ingredient : recipe.getIngredients()) {
            ObjectNode item = ingredients.addObject();
            item.put("name", ingredient.getName());
            item.put("quantity", ingredient.getQuantity());
            item.put("unit", ingredient.getUnit());
        }
        writeStrings(node.putArray("instructions"), recipe.getInstructions());
        writeStrings(node.putArray("dietaryTags"), recipe.getDietaryTags());
        return node;
    }

    private Recipe readRecipe(JsonNode node) throws IOException {
        List<Ingredient> ingredients = new ArrayList<>();
        JsonNode ingredientNodes = node.path("ingredients");
        if (!ingredientNodes.isArray()) throw new IOException("favourite ingredients must be an array");
        for (JsonNode item : ingredientNodes) {
            ingredients.add(new Ingredient(requiredText(item, "name"), requiredDouble(item, "quantity"),
                    requiredText(item, "unit")));
        }
        return new Recipe(requiredInt(node, "recipeID"), requiredText(node, "name"),
                requiredText(node, "description"), requiredInt(node, "servings"),
                requiredInt(node, "prepTime"), requiredInt(node, "bakeTime"), ingredients,
                readStrings(node.path("instructions")), readStrings(node.path("dietaryTags")));
    }

    private static void writeStrings(ArrayNode target, List<String> values) {
        values.forEach(target::add);
    }

    private static List<String> readStrings(JsonNode node) throws IOException {
        if (!node.isArray()) throw new IOException("expected a JSON string array");
        List<String> values = new ArrayList<>();
        for (JsonNode value : node) {
            if (!value.isTextual()) throw new IOException("expected string array values");
            values.add(value.asText());
        }
        return values;
    }

    private static String requiredText(JsonNode node, String field) throws IOException {
        JsonNode value = node.path(field);
        if (!value.isTextual()) throw new IOException("missing or invalid " + field);
        return value.asText();
    }

    private static int requiredInt(JsonNode node, String field) throws IOException {
        JsonNode value = node.path(field);
        if (!value.canConvertToInt()) throw new IOException("missing or invalid " + field);
        return value.intValue();
    }

    private static double requiredDouble(JsonNode node, String field) throws IOException {
        JsonNode value = node.path(field);
        if (!value.isNumber()) throw new IOException("missing or invalid " + field);
        return value.doubleValue();
    }
}
