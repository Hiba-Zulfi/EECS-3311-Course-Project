package baking.model;

import java.util.Objects;

/** An ingredient name, quantity, and unit. */
public class Ingredient {
    private final String name;
    private double quantity;
    private final String unit;

    public Ingredient(String name, double quantity, String unit) {
        this.name = requireText(name, "name");
        if (!Double.isFinite(quantity) || quantity < 0) {
            throw new IllegalArgumentException("quantity must be finite and non-negative");
        }
        this.quantity = quantity;
        this.unit = requireText(unit, "unit");
    }

    public String getName() { return name; }
    public double getQuantity() { return quantity; }
    public String getUnit() { return unit; }

    public void updateQuantity(double multiplier) {
        if (!Double.isFinite(multiplier) || multiplier < 0) {
            throw new IllegalArgumentException("multiplier must be finite and non-negative");
        }
        quantity *= multiplier;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }
}
