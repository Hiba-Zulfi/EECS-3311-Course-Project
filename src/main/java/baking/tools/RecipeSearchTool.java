package baking.tools;

import baking.llm.LLMClient;
import baking.model.Recipe;
import baking.service.RecipeRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Interprets a baking request and finds matching recipes in the local catalog. */
public class RecipeSearchTool implements AgentTool {
    private final RecipeRepository repository;
    private final LLMClient llmClient;
    private List<Recipe> lastSearchResults = List.of();

    public RecipeSearchTool(RecipeRepository repository, LLMClient llmClient) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
    }

    @Override
    public String execute(String input) {
        List<Recipe> matches = searchRecipes(input);
        if (matches.isEmpty()) return "No recipes matched that request. Try another recipe name or dietary preference.";
        return "Matching recipes:\n" + String.join("\n", matches.stream()
                .map(recipe -> "- " + recipe.getName() + " (" + recipe.getTotalTime() + " minutes)").toList());
    }

    public List<Recipe> searchRecipes(String input) {
        if (input == null || input.isBlank()) throw new IllegalArgumentException("search request must not be blank");
        Map<String, Object> criteria = llmClient.analyzeRequest(input);
        List<String> terms = searchTerms(criteria, input);
        List<String> dietary = stringValues(criteria.getOrDefault("dietaryRestrictions", criteria.get("dietaryTags")));
        lastSearchResults = repository.getAllRecipes().stream()
                .filter(recipe -> terms.isEmpty() || terms.stream().anyMatch(term -> contains(recipe.getName(), term)))
                .filter(recipe -> dietary.isEmpty() || dietary.stream().allMatch(tag -> recipe.getDietaryTags().stream()
                        .anyMatch(recipeTag -> recipeTag.equalsIgnoreCase(tag))))
                .toList();
        return lastSearchResults;
    }

    public List<Recipe> getLastSearchResults() { return List.copyOf(lastSearchResults); }

    public String interpretRequest(String input) {
        Map<String, Object> criteria = llmClient.analyzeRequest(input);
        return "Search criteria: " + searchTerms(criteria, input);
    }

    @Override
    public String getName() { return "RecipeSearchTool"; }

    private static List<String> searchTerms(Map<String, Object> criteria, String input) {
        for (String field : List.of("searchTerms", "keywords")) {
            List<String> values = stringValues(criteria.get(field));
            if (!values.isEmpty()) return values;
        }
        for (String field : List.of("searchTerm", "recipeName", "category", "query")) {
            Object value = criteria.get(field);
            if (value instanceof String text && !text.isBlank()) return List.of(text.trim());
        }
        return List.of(input.trim());
    }

    private static List<String> stringValues(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().filter(String.class::isInstance).map(String.class::cast)
                    .map(String::trim).filter(text -> !text.isEmpty()).toList();
        }
        if (value instanceof String text && !text.isBlank()) {
            return List.of(text.split("[,;]")).stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
        return List.of();
    }

    private static boolean contains(String text, String term) {
        return text.toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT));
    }
}
