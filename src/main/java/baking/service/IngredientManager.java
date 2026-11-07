package baking.service;

import baking.model.Ingredient;
import baking.model.Recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Locale;

/** Deterministic comparison of recipe requirements with ingredients on hand. */
public class IngredientManager {
    private static final java.util.Set<String> QUALIFIERS = java.util.Set.of(
            "all", "purpose", "sifted", "chopped", "melted", "softened", "unsalted", "salted",
            "large", "medium", "small", "fresh", "dried", "ground", "plain", "raw", "whole");
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
            double quantityOnHand = available.stream().filter(Objects::nonNull)
                    .filter(item -> sameName(requirement.getName(), item.getName()))
                    .mapToDouble(item -> convert(item.getQuantity(), item.getUnit(), requirement.getUnit()))
                    .sum();
            double shortfall = requirement.getQuantity() - quantityOnHand;
            if (shortfall > 0) {
                missing.add(new Ingredient(requirement.getName(), shortfall, requirement.getUnit()));
            }
        }
        return List.copyOf(missing);
    }

    /** Replaces an existing pantry entry with the same normalized name, or appends a new entry. */
    public List<Ingredient> mergeIngredients(List<Ingredient> current, List<Ingredient> additions) {
        List<Ingredient> merged = new ArrayList<>(Objects.requireNonNull(current, "current"));
        for (Ingredient addition : Objects.requireNonNull(additions, "additions")) {
            Objects.requireNonNull(addition, "ingredient");
            int index = -1;
            for (int i = 0; i < merged.size(); i++) {
                if (sameName(merged.get(i).getName(), addition.getName())) { index = i; break; }
            }
            if (index >= 0) merged.set(index, addition);
            else merged.add(addition);
        }
        return List.copyOf(merged);
    }

    private static boolean sameName(String left, String right) {
        return normalizeName(left).equals(normalizeName(right));
    }

    private static String normalizeName(String name) {
        List<String> words = new ArrayList<>();
        for (String raw : name.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}]+", " ").trim().split("\\s+")) {
            String word = raw;
            if (word.endsWith("ies") && word.length() > 4) word = word.substring(0, word.length() - 3) + "y";
            else if (word.endsWith("s") && word.length() > 3) word = word.substring(0, word.length() - 1);
            if (!word.isBlank() && !QUALIFIERS.contains(word)) words.add(word);
        }
        return String.join(" ", words);
    }

    private static double convert(double quantity, String from, String to) {
        Unit fromUnit = unit(from), toUnit = unit(to);
        if (!fromUnit.dimension().equals(toUnit.dimension())) return 0;
        return quantity * fromUnit.factor() / toUnit.factor();
    }

    private static Unit unit(String raw) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        value = switch (value) {
            case "cups" -> "cup";
            case "tablespoon", "tablespoons" -> "tbsp";
            case "teaspoon", "teaspoons" -> "tsp";
            case "grams" -> "g";
            case "kilogram", "kilograms" -> "kg";
            case "ounce", "ounces" -> "oz";
            case "pound", "pounds" -> "lb";
            case "milliliter", "milliliters" -> "ml";
            case "liter", "liters" -> "l";
            case "pinches" -> "pinch";
            case "large", "medium", "small", "whole", "each", "piece", "pieces", "count" -> "count";
            default -> value;
        };
        return switch (value) {
            case "cup" -> new Unit("volume", 240);
            case "tbsp" -> new Unit("volume", 15);
            case "tsp" -> new Unit("volume", 5);
            case "ml" -> new Unit("volume", 1);
            case "l" -> new Unit("volume", 1000);
            case "g" -> new Unit("mass", 1);
            case "kg" -> new Unit("mass", 1000);
            case "oz" -> new Unit("mass", 28.349523125);
            case "lb" -> new Unit("mass", 453.59237);
            case "count" -> new Unit("count", 1);
            case "pinch" -> new Unit("pinch", 1);
            default -> new Unit("other:" + value, 1);
        };
    }

    private record Unit(String dimension, double factor) { }
}
