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
    private String lastSearchQuery = "";
    private boolean lastSearchHadKeywordMatch;

    private static final Set<String> STOPWORDS = Set.of(
            "i", "me", "my", "we", "our", "you", "your", "want", "wanting", "would", "like", "need", "looking",
            "for", "people", "person", "serving", "servings", "serve", "a", "an", "the", "some", "any",
            "make", "bake", "baking", "cook", "cooking", "please", "find", "search", "recipe", "recipes",
            "give", "show", "get", "to", "of", "and", "or", "with", "in", "on", "at", "from", "that",
            "this", "it", "is", "are", "can", "could", "about", "really", "actually", "just", "very",
            "all", "have", "has", "had", "do", "does", "did", "will", "here", "there", "much",
            "only", "minute", "minutes", "min", "mins", "hour", "hours", "hr", "hrs", "half");
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
        return formatResults(matches);
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
        return formatResults(matches);
    }

    private List<Recipe> search(String input, List<String> dietaryRestrictions, int availableTime) {
        lastSearchQuery = input.trim();
        List<String> keywords = keywords(input);
        if (debugEnabled()) System.err.println("[BAKING_DEBUG] Search keywords: " + keywords);
        List<RecipeScore> scored = repository.getAllRecipes().stream()
                .map(recipe -> new RecipeScore(recipe, score(recipe, keywords)))
                .filter(candidate -> candidate.score() > 0)
                .sorted((a, b) -> Integer.compare(b.score(), a.score()))
                .toList();
        lastSearchHadKeywordMatch = !scored.isEmpty();
        List<Recipe> candidates = scored.stream().map(RecipeScore::recipe).toList();
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
    public List<Recipe> getAllRecipes() { return repository.getAllRecipes(); }

    public static boolean hasSearchKeywords(String input) {
        return input != null && !keywords(input).isEmpty();
    }

    /** True for a bare category/recipe keyword that needs no language interpretation. */
    public static boolean isSimpleSearchQuery(String input) {
        if (input == null || keywords(input).size() != 1) return false;
        var matcher = WORD.matcher(input.trim());
        if (!matcher.find()) return false;
        return !matcher.find();
    }

    public boolean isRecipeNameKeyword(String input) {
        List<String> query = keywords(input);
        if (query.size() != 1) return false;
        String wanted = query.getFirst();
        return repository.getAllRecipes().stream().anyMatch(recipe -> {
            Set<String> nameWords = new java.util.HashSet<>();
            addWords(nameWords, recipe.getName());
            return nameWords.contains(wanted);
        });
    }

    public String interpretRequest(String input) {
        return "Search criteria: " + keywords(input);
    }

    public String formatResults(List<Recipe> recipes) {
        if (recipes.isEmpty()) {
            if (!lastSearchHadKeywordMatch) {
                String askedFor = lastSearchQuery.isBlank() ? "that" : lastSearchQuery;
                return "I don't have a recipe for '" + askedFor + "' in my collection yet. "
                        + "I can show you all recipes if you'd like.";
            }
            return "No recipes matched the requested keywords and hard filters.";
        }
        int shown = Math.min(5, recipes.size());
        StringBuilder result = new StringBuilder("Matching recipes:\n");
        for (int i = 0; i < shown; i++) {
            Recipe recipe = recipes.get(i);
            result.append('[').append(recipe.getRecipeID()).append("] ").append(recipe.getName())
                    .append(" (").append(recipe.getTotalTime()).append(" minutes)");
            if (i + 1 < shown) result.append('\n');
        }
        int remaining = recipes.size() - shown;
        if (remaining > 0) result.append("\n(").append(remaining).append(" more matches not shown)");
        return result.toString();
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

    private static void debugCount(String phase, int count) {
        if (debugEnabled()) System.err.println("[BAKING_DEBUG] Recipes " + phase + ": " + count);
    }

    private static boolean debugEnabled() {
        return Boolean.parseBoolean(System.getenv().getOrDefault("BAKING_DEBUG", "false"));
    }

    private record RecipeScore(Recipe recipe, int score) { }

}
