package baking.tools;

import baking.llm.LLMClient;
import baking.model.Recipe;
import baking.service.RecipeRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        Map<String, Object> analysis = llmClient.analyzeRequest(input);
        int minutes = availableTime(analysis);
        if (minutes <= 0) throw new IllegalArgumentException("The request must include a positive available time in minutes.");
        List<Recipe> sourceRecipes = recipesFromSearchOutput(input);
        List<Recipe> recommendations = recommendRecipes(sourceRecipes, minutes);
        return lastExplanation + "\n" + String.join("\n", recommendations.stream()
                .map(recipe -> "- " + recipe.getName() + " (" + recipe.getTotalTime() + " minutes)").toList());
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
                ? recipes.stream().sorted((a, b) -> Integer.compare(
                        Math.abs(a.getTotalTime() - availableTime), Math.abs(b.getTotalTime() - availableTime)))
                    .limit(Math.min(3, recipes.size())).toList()
                : fitting;
        if (candidates.isEmpty()) {
            lastExplanation = "No recipes are available in the catalog.";
            lastRecommendations = List.of();
            return List.of();
        }
        List<String> rankedNames = llmClient.rankOptions(candidates.stream().map(Recipe::getName).toList());
        List<Recipe> ranked = rankRecipes(candidates, rankedNames);
        lastExplanation = fallback
                ? "No recipe fits within " + availableTime + " minutes. These are the closest-time options, ranked for your request:"
                : "Recipes that fit within " + availableTime + " minutes, ranked for your request:";
        lastRecommendations = ranked;
        return ranked;
    }

    public List<Recipe> rankRecipes(List<Recipe> recipes) {
        return rankRecipes(recipes, llmClient.rankOptions(recipes.stream().map(Recipe::getName).toList()));
    }

    public String getLastExplanation() { return lastExplanation; }
    public List<Recipe> getLastRecommendations() { return List.copyOf(lastRecommendations); }

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

    private static int availableTime(Map<String, Object> analysis) {
        for (String key : List.of("availableTime", "availableTimeMinutes", "timeMinutes", "time")) {
            Object value = analysis.get(key);
            if (value instanceof Number number) return number.intValue();
            if (value instanceof String text) {
                String numeric = text.toLowerCase(Locale.ROOT).replace("minutes", "").replace("minute", "").trim();
                try { return Integer.parseInt(numeric); }
                catch (NumberFormatException ignored) { /* Try other time fields. */ }
            }
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
