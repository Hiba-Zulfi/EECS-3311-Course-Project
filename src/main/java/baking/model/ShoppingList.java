package baking.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** A mutable collection of ingredients still needed for a recipe. */
public class ShoppingList {
    private final List<Ingredient> items = new ArrayList<>();

    public void addItem(Ingredient item) { items.add(Objects.requireNonNull(item, "item")); }
    public void removeItem(Ingredient item) { items.remove(item); }
    public List<Ingredient> getItems() { return List.copyOf(items); }

    public static ShoppingList generate(Recipe recipe, List<Ingredient> available) {
        ShoppingList list = new ShoppingList();
        findMissingItems(recipe, available).forEach(list::addItem);
        return list;
    }

    public static List<Ingredient> findMissingItems(Recipe recipe, List<Ingredient> available) {
        Objects.requireNonNull(recipe, "recipe");
        Objects.requireNonNull(available, "available");
        List<Ingredient> missing = new ArrayList<>();
        for (Ingredient required : recipe.getIngredients()) {
            double quantityAvailable = available.stream()
                    .filter(item -> sameIngredient(required, item))
                    .mapToDouble(Ingredient::getQuantity).sum();
            double deficit = required.getQuantity() - quantityAvailable;
            if (deficit > 1e-9) missing.add(new Ingredient(required.getName(), deficit, required.getUnit()));
        }
        return List.copyOf(missing);
    }

    private static boolean sameIngredient(Ingredient first, Ingredient second) {
        return first.getName().trim().equalsIgnoreCase(second.getName().trim())
                && first.getUnit().trim().equalsIgnoreCase(second.getUnit().trim());
    }
}
