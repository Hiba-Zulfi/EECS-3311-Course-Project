package baking.model;

import java.util.List;
import java.util.Objects;

/** Structured user request sent through the baking agent. */
public class BakingRequest {
    private final String requestText;
    private final int desiredServings;
    private final int availableTime;
    private final List<Ingredient> availableIngredients;
    private final List<String> dietaryRestrictions;
    private final String tastePreference;

    public BakingRequest(String requestText, int desiredServings, int availableTime,
                         List<Ingredient> availableIngredients, List<String> dietaryRestrictions,
                         String tastePreference) {
        this.requestText = Objects.requireNonNull(requestText, "requestText");
        if (desiredServings < 0 || availableTime < 0) throw new IllegalArgumentException("servings and time must be non-negative");
        this.desiredServings = desiredServings;
        this.availableTime = availableTime;
        this.availableIngredients = List.copyOf(Objects.requireNonNull(availableIngredients, "availableIngredients"));
        this.dietaryRestrictions = List.copyOf(Objects.requireNonNull(dietaryRestrictions, "dietaryRestrictions"));
        this.tastePreference = Objects.requireNonNull(tastePreference, "tastePreference");
    }

    public String getRequestText() { return requestText; }
    public int getDesiredServings() { return desiredServings; }
    public int getAvailableTime() { return availableTime; }
    public List<Ingredient> getAvailableIngredients() { return availableIngredients; }
    public List<String> getDietaryRestrictions() { return dietaryRestrictions; }
    public String getTastePreference() { return tastePreference; }
}
