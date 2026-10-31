package baking.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** User's dietary restrictions and taste preferences. */
public class UserPreferences {
    private final List<String> dietaryRestrictions = new ArrayList<>();
    private final List<String> tastePreferences = new ArrayList<>();
    private int preferredServingSize;

    public UserPreferences() { this(0); }

    public UserPreferences(int preferredServingSize) {
        if (preferredServingSize < 0) throw new IllegalArgumentException("preferredServingSize must be non-negative");
        this.preferredServingSize = preferredServingSize;
    }

    /** Adds a taste preference, matching the class diagram's addPreference operation. */
    public void addPreference(String preference) { addUnique(tastePreferences, preference); }
    public void removePreference(String preference) { tastePreferences.remove(preference); }
    public List<String> getPreference() { return List.copyOf(tastePreferences); }
    public List<String> getTastePreferences() { return List.copyOf(tastePreferences); }
    public List<String> getDietaryRestrictions() { return List.copyOf(dietaryRestrictions); }
    public int getPreferredServingSize() { return preferredServingSize; }

    public void addDietaryRestriction(String restriction) { addUnique(dietaryRestrictions, restriction); }
    public void removeDietaryRestriction(String restriction) { dietaryRestrictions.remove(restriction); }

    public void setPreferredServingSize(int preferredServingSize) {
        if (preferredServingSize < 0) throw new IllegalArgumentException("preferredServingSize must be non-negative");
        this.preferredServingSize = preferredServingSize;
    }

    private static void addUnique(List<String> values, String value) {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("preference must not be blank");
        if (!values.contains(value)) values.add(value);
    }
}
