package baking.tools;

import baking.llm.LLMClient;
import baking.model.Recipe;
import baking.service.RecipeRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Filters recipes by available time and asks the LLM to rank the viable options. */
public class TimeRecommendationTool implements AgentTool {
    private final RecipeRepository repository;
    private final LLMClient llmClient;
    private String lastExplanation = "";
    private List<Recipe> lastRecommendations = List.of();

    public TimeRecommendationTool(RecipeRepository repository, LLMClient llmClient) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
    }

    @Override
    public String execute(String input) {
        if (input == null || input.isBlank()) throw new IllegalArgumentException("request must not be blank");
        int minutes = parseAvailableTime(input);
        if (minutes <= 0) throw new IllegalArgumentException("The request must include a positive available time in minutes.");
        List<Recipe> sourceRecipes = recipesFromSearchOutput(input);
        List<Recipe> recommendations = recommendRecipes(sourceRecipes, minutes);
        return formatRecommendations(recommendations);
    }

    public List<Recipe> filterByTime(List<Recipe> recipes, int availableTime) {
        if (availableTime < 0) throw new IllegalArgumentException("availableTime must not be negative");
        return recipes.stream().filter(recipe -> recipe.getTotalTime() <= availableTime).toList();
    }

    /** Returns ranked fitting recipes or, if none fit, the nearest-time alternatives. */
    public List<Recipe> recommendRecipes(List<Recipe> recipes, int availableTime) {
        Objects.requireNonNull(recipes, "recipes");
        if (availableTime <= 0) throw new IllegalArgumentException("availableTime must be positive");
        List<Recipe> fitting = filterByTime(recipes, availableTime);
        boolean fallback = fitting.isEmpty();
        List<Recipe> candidates = fallback
                ? recipes.stream().sorted((a, b) -> Integer.compare(a.getTotalTime(), b.getTotalTime()))
                    .limit(Math.min(3, recipes.size())).toList()
                : fitting;
        if (candidates.isEmpty()) {
            lastExplanation = "No recipes are available in the catalog.";
            lastRecommendations = List.of();
            return List.of();
        }
        List<String> rankedNames = llmClient.rankOptions(candidates.stream().map(Recipe::getName).toList(),
                "time recommendation ranking");
        List<Recipe> ranked = rankRecipes(candidates, rankedNames);
        lastExplanation = fallback
                ? "No recipe fits within " + availableTime + " minutes. Here are the shortest recipes, ranked for your request:"
                : "Recipes that fit within " + availableTime + " minutes, ranked for your request:";
        lastRecommendations = ranked;
        return ranked;
    }

    public List<Recipe> rankRecipes(List<Recipe> recipes) {
        return rankRecipes(recipes, llmClient.rankOptions(recipes.stream().map(Recipe::getName).toList(),
                "recipe ranking"));
    }

    public String getLastExplanation() { return lastExplanation; }
    public List<Recipe> getLastRecommendations() { return List.copyOf(lastRecommendations); }

    public String formatRecommendations(List<Recipe> recipes) {
        int shown = Math.min(5, recipes.size());
        StringBuilder result = new StringBuilder(lastExplanation);
        for (int i = 0; i < shown; i++) {
            Recipe recipe = recipes.get(i);
            result.append(i == 0 ? "\n" : "\n").append('[').append(recipe.getRecipeID()).append("] ")
                    .append(recipe.getName()).append(" (").append(recipe.getTotalTime()).append(" minutes)");
        }
        int remaining = recipes.size() - shown;
        if (remaining > 0) result.append("\n(").append(remaining).append(" more matches not shown)");
        return result.toString();
    }

    @Override
    public String getName() { return "TimeRecommendationTool"; }

    private static List<Recipe> rankRecipes(List<Recipe> recipes, List<String> rankedNames) {
        List<Recipe> ranked = new ArrayList<>();
        for (String name : rankedNames) {
            recipes.stream().filter(recipe -> recipe.getName().equalsIgnoreCase(name)).findFirst()
                    .filter(recipe -> !ranked.contains(recipe)).ifPresent(ranked::add);
        }
        recipes.stream().filter(recipe -> !ranked.contains(recipe)).forEach(ranked::add);
        return List.copyOf(ranked);
    }

    public static int parseAvailableTime(String input) {
        if (input == null || input.isBlank()) return 0;
        String normalized = input.toLowerCase(Locale.ROOT).replace('-', ' ')
                .replaceAll("\\s+", " ").trim();
        if (normalized.matches(".*\\b(?:an|a|one|1) hour and (?:a )?half\\b.*")) return 90;
        java.util.regex.Matcher hourMatcher = java.util.regex.Pattern
                .compile("(?i)(\\d+(?:\\.\\d+)?)\\s*(?:hours?|hrs?)\\b").matcher(normalized);
        if (hourMatcher.find()) {
            try { return (int) Math.round(Double.parseDouble(hourMatcher.group(1)) * 60); }
            catch (NumberFormatException ignored) { return 0; }
        }
        if (normalized.matches(".*\\b(?:an|a|one) hour\\b.*")) return 60;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?i)(\\d+)\\s*(?:minutes?|mins?)\\b").matcher(normalized);
        if (matcher.find()) {
            try { return Integer.parseInt(matcher.group(1)); }
            catch (NumberFormatException ignored) { return 0; }
        }
        return 0;
    }

    private List<Recipe> recipesFromSearchOutput(String input) {
        if (input.contains("No recipes matched")) return List.of();
        List<Recipe> results = repository.getAllRecipes().stream()
                .filter(recipe -> input.toLowerCase(Locale.ROOT)
                        .contains(("- " + recipe.getName()).toLowerCase(Locale.ROOT)))
                .toList();
        return results.isEmpty() ? repository.getAllRecipes() : results;
    }
}
