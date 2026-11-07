package baking.view;

import baking.model.Ingredient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

/** Shared display-only formatting for ingredient quantities and units. */
public final class QuantityFormatter {
    private static final List<Fraction> FRACTIONS = List.of(
            new Fraction(0.25, "1/4"), new Fraction(1.0 / 3.0, "1/3"),
            new Fraction(0.5, "1/2"), new Fraction(2.0 / 3.0, "2/3"),
            new Fraction(0.75, "3/4"));
    private static final double FRACTION_TOLERANCE = 0.025;

    private QuantityFormatter() { }

    public static String formatQuantity(double quantity) {
        if (!Double.isFinite(quantity)) return Double.toString(quantity);
        double absolute = Math.abs(quantity);
        long whole = (long) Math.floor(absolute);
        double fractional = absolute - whole;
        Fraction nearest = FRACTIONS.stream().min((left, right) ->
                Double.compare(Math.abs(fractional - left.value()), Math.abs(fractional - right.value()))).orElseThrow();
        String sign = quantity < 0 ? "-" : "";
        if (Math.abs(fractional - nearest.value()) <= FRACTION_TOLERANCE) {
            if (whole == 0) return sign + nearest.label();
            return sign + whole + " " + nearest.label();
        }
        return BigDecimal.valueOf(quantity).setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    public static String formatIngredient(Ingredient ingredient) {
        String quantity = formatQuantity(ingredient.getQuantity());
        String unit = formatUnit(ingredient.getUnit(), ingredient.getQuantity());
        return ingredient.getName() + ": " + quantity + (unit.isEmpty() ? "" : " " + unit);
    }

    public static String formatIngredients(List<Ingredient> ingredients) {
        return ingredients.stream().map(QuantityFormatter::formatIngredient)
                .map(line -> "- " + line).reduce((left, right) -> left + "\n" + right).orElse("");
    }

    public static String formatUnit(String rawUnit, double quantity) {
        String unit = rawUnit.trim().toLowerCase(Locale.ROOT);
        if (unit.equals("count") || unit.equals("none") || unit.isEmpty()) return "";
        unit = switch (unit) {
            case "cups" -> "cup";
            case "tablespoons" -> "tablespoon";
            case "tbsp" -> "tbsp";
            case "teaspoons" -> "teaspoon";
            case "tsp" -> "tsp";
            case "grams" -> "g";
            case "kilograms" -> "kg";
            case "ounces" -> "oz";
            case "pounds" -> "lb";
            case "milliliters" -> "ml";
            case "liters" -> "l";
            case "pinches" -> "pinch";
            default -> unit;
        };
        if (List.of("large", "medium", "small", "whole", "each").contains(unit)) return unit;
        if (isAbbreviation(unit)) return unit;
        boolean singular = quantity > 0 && quantity <= 1.0;
        if (singular) return unit;
        return unit.endsWith("s") ? unit : unit + "s";
    }

    private static boolean isAbbreviation(String unit) {
        return List.of("tbsp", "tsp", "g", "kg", "oz", "lb", "ml", "l").contains(unit);
    }

    private record Fraction(double value, String label) { }
}
