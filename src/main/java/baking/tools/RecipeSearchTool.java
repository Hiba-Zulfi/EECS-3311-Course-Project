package baking.tools;

import baking.llm.LLMClient;
import baking.model.Recipe;
import baking.service.RecipeRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Interprets a baking request and finds matching recipes in the local catalog. */
public class RecipeSearchTool implements AgentTool {
    private final RecipeRepository repository;
    private List<Recipe> lastSearchResults = List.of();
    private boolean closeMatch;

    private static final Set<String> STOPWORDS = Set.of(
            "i", "me", "my", "we", "our", "you", "your", "want", "wanting", "would", "like", "need", "looking",
            "for", "people", "person", "serving", "servings", "serve", "a", "an", "the", "some", "any",
            "make", "bake", "baking", "cook", "cooking", "please", "find", "search", "recipe", "recipes",
            "give", "show", "get", "to", "of", "and", "or", "with", "in", "on", "at", "from", "that",
            "this", "it", "is", "are", "can", "could", "about", "really", "actually", "just", "very",
            "all", "have", "has", "had", "do", "does", "did", "will", "here", "there", "much");
    private static final Pattern WORD = Pattern.compile("[\\p{L}]+(?:'[\\p{L}]+)?");

    public RecipeSearchTool(RecipeRepository repository, LLMClient llmClient) {
        this(repository);
        Objects.requireNonNull(llmClient, "llmClient");
    }

    public RecipeSearchTool(RecipeRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public String execute(String input) {
        List<Recipe> matches = search(input, List.of(), 0);
        if (matches.isEmpty()) return "No recipes matched the requested keywords and hard filters.";
        String heading = closeMatch ? "No exact keyword match; here is a close category match:\n" : "Matching recipes:\n";
        return heading + String.join("\n", matches.stream()
                .map(recipe -> "- " + recipe.getName() + " (" + recipe.getTotalTime() + " minutes)").toList());
    }

    public List<Recipe> searchRecipes(String input) {
        if (input == null || input.isBlank()) throw new IllegalArgumentException("search request must not be blank");
        return search(input, List.of(), 0);
    }

    /** Searches the original user words and applies only structured, explicitly extracted hard filters. */
    public List<Recipe> searchRecipes(String input, List<String> dietaryRestrictions, int availableTime) {
        if (input == null || input.isBlank()) throw new IllegalArgumentException("search request must not be blank");
        if (availableTime < 0) throw new IllegalArgumentException("availableTime must not be negative");
        return search(input, dietaryRestrictions, availableTime);
    }

    public String execute(String input, List<String> dietaryRestrictions, int availableTime) {
        List<Recipe> matches = searchRecipes(input, dietaryRestrictions, availableTime);
        if (matches.isEmpty()) return "No recipes matched the requested keywords and hard filters.";
        String heading = closeMatch ? "No exact keyword match; here is a close category match:\n" : "Matching recipes:\n";
        return heading + String.join("\n", matches.stream()
                .map(recipe -> "- " + recipe.getName() + " (" + recipe.getTotalTime() + " minutes)").toList());
    }

    private List<Recipe> search(String input, List<String> dietaryRestrictions, int availableTime) {
        List<String> keywords = keywords(input);
        if (debugEnabled()) System.err.println("[BAKING_DEBUG] Search keywords: " + keywords);
        List<RecipeScore> scored = repository.getAllRecipes().stream()
                .map(recipe -> new RecipeScore(recipe, score(recipe, keywords)))
                .filter(candidate -> candidate.score() > 0)
                .sorted((a, b) -> Integer.compare(b.score(), a.score()))
                .toList();
        closeMatch = scored.isEmpty();
        List<Recipe> candidates;
        if (scored.isEmpty()) {
            candidates = closestCategoryMatch(input);
        } else {
            candidates = scored.stream().map(RecipeScore::recipe).toList();
        }
        debugCount("after keyword scoring", scored.size());
        List<Recipe> dietaryFiltered = candidates.stream()
                .filter(recipe -> dietaryRestrictions == null || dietaryRestrictions.isEmpty()
                        || dietaryRestrictions.stream().allMatch(tag -> recipe.getDietaryTags().stream()
                                .anyMatch(recipeTag -> recipeTag.equalsIgnoreCase(tag))))
                .toList();
        debugCount("after dietary filter", dietaryFiltered.size());
        lastSearchResults = dietaryFiltered.stream()
                .filter(recipe -> availableTime <= 0 || recipe.getTotalTime() <= availableTime)
                .toList();
        debugCount("after time filter", lastSearchResults.size());
        return lastSearchResults;
    }

    public List<Recipe> getLastSearchResults() { return List.copyOf(lastSearchResults); }

    public String interpretRequest(String input) {
        return "Search criteria: " + keywords(input);
    }

    public String formatResults(List<Recipe> recipes) {
        if (recipes.isEmpty()) return "No recipes matched the requested keywords and hard filters.";
        return (closeMatch ? "No exact keyword match; here is a close category match:\n" : "Matching recipes:\n")
                + String.join("\n", recipes.stream()
                .map(recipe -> "- " + recipe.getName() + " (" + recipe.getTotalTime() + " minutes)").toList());
    }

    @Override
    public String getName() { return "RecipeSearchTool"; }

    private static List<String> keywords(String input) {
        List<String> terms = new ArrayList<>();
        var matcher = WORD.matcher(input.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String word = normalizeWord(matcher.group());
            if (word.length() > 1 && !STOPWORDS.contains(word) && !word.chars().allMatch(Character::isDigit)) {
                terms.add(word);
            }
        }
        return List.copyOf(terms);
    }

    private static int score(Recipe recipe, List<String> keywords) {
        Set<String> words = new java.util.HashSet<>();
        addWords(words, recipe.getName());
        addWords(words, recipe.getDescription());
        recipe.getDietaryTags().forEach(tag -> addWords(words, tag));
        return (int) keywords.stream().distinct().filter(words::contains).count();
    }

    private static void addWords(Set<String> target, String value) {
        var matcher = WORD.matcher(value.toLowerCase(Locale.ROOT));
        while (matcher.find()) target.add(normalizeWord(matcher.group()));
    }

    private static String normalizeWord(String word) {
        if (word.endsWith("ies") && word.length() > 4) return word.substring(0, word.length() - 3) + "y";
        if (word.endsWith("s") && word.length() > 3) return word.substring(0, word.length() - 1);
        return word;
    }

    private List<Recipe> closestCategoryMatch(String input) {
        List<String> queryWords = keywords(input);
        List<RecipeScore> categoryScores = repository.getAllRecipes().stream()
                .map(recipe -> new RecipeScore(recipe, score(recipe, queryWords)))
                .sorted((a, b) -> Integer.compare(b.score(), a.score())).toList();
        if (categoryScores.isEmpty()) return List.of();
        int bestScore = categoryScores.getFirst().score();
        if (bestScore > 0) return categoryScores.stream().filter(s -> s.score() == bestScore)
                .map(RecipeScore::recipe).toList();
        // No literal overlap exists; show the first catalog recipe as an explicitly labeled fallback.
        return repository.getAllRecipes().stream().limit(1).toList();
    }

    private static void debugCount(String phase, int count) {
        if (debugEnabled()) System.err.println("[BAKING_DEBUG] Recipes " + phase + ": " + count);
    }

    private static boolean debugEnabled() {
        return Boolean.parseBoolean(System.getenv().getOrDefault("BAKING_DEBUG", "false"));
    }

    private record RecipeScore(Recipe recipe, int score) { }

}
