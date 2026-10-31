package baking.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import baking.service.IngredientManager;

/** A mutable collection of ingredients still needed for a recipe. */
public class ShoppingList {
    private final List<Ingredient> items = new ArrayList<>();

    public void addItem(Ingredient item) { items.add(Objects.requireNonNull(item, "item")); }
    public void removeItem(Ingredient item) { items.remove(item); }
    public List<Ingredient> getItems() { return List.copyOf(items); }

    public static ShoppingList generate(Recipe recipe, List<Ingredient> available) {
        Objects.requireNonNull(recipe, "recipe");
        return fromMissingIngredients(new IngredientManager().compareIngredients(recipe.getIngredients(), available));
    }

    public static List<Ingredient> findMissingItems(Recipe recipe, List<Ingredient> available) {
        Objects.requireNonNull(recipe, "recipe");
        return new IngredientManager().compareIngredients(recipe.getIngredients(), available);
    }

    public static ShoppingList fromMissingIngredients(List<Ingredient> missingIngredients) {
        ShoppingList list = new ShoppingList();
        Objects.requireNonNull(missingIngredients, "missingIngredients").forEach(list::addItem);
        return list;
    }
}
