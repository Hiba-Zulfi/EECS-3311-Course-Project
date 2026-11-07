package baking.agent;

import baking.llm.LLMClient;
import baking.llm.LLMException;
import baking.model.BakingRequest;
import baking.model.Ingredient;
import baking.model.Recipe;
import baking.model.UserPreferences;
import baking.tools.AgentTool;
import baking.tools.BakingRequestTool;
import baking.tools.RecipeSearchTool;
import baking.tools.TimeRecommendationTool;
import baking.tools.ToolManager;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Classifies baking requests and routes them to the matching tool strategy. */
public class BakingAgent {
    private static final String UNCLEAR = "unclear";
    private static final Map<String, String> INTENT_ALIASES = Map.ofEntries(
            Map.entry("baking request", "baking request"),
            Map.entry("submit baking request", "baking request"),
            Map.entry("baking", "baking request"),
            Map.entry("recipe search", "recipe search"),
            Map.entry("search recipes", "recipe search"),
            Map.entry("search recipe", "recipe search"),
            Map.entry("find recipe", "recipe search"),
            Map.entry("time based recommendation", "time-based recommendation"),
            Map.entry("time recommendation", "time-based recommendation"),
            Map.entry("time based", "time-based recommendation"),
            Map.entry("substitution", "substitution"),
            Map.entry("ingredient substitution", "substitution"),
            Map.entry("taste customization", "taste customization"),
            Map.entry("customize taste", "taste customization"),
            Map.entry("troubleshooting", "troubleshooting"),
            Map.entry("troubleshoot", "troubleshooting"),
            Map.entry("unclear", UNCLEAR));
    private static final Map<String, String> INTENT_TO_TOOL = Map.of(
            "baking request", "BakingRequestTool",
            "recipe search", "RecipeSearchTool",
            "time-based recommendation", "TimeRecommendationTool",
            "substitution", "SubstitutionTool",
            "taste customization", "TasteCustomizationTool",
            "troubleshooting", "TroubleshootingTool");

    private final LLMClient llmClient;
    private final ToolManager toolManager;
    private final AgentMemory memory;
    private final UserPreferences userPreferences;

    public BakingAgent(LLMClient llmClient, ToolManager toolManager, AgentMemory memory) {
        this(llmClient, toolManager, memory, new UserPreferences());
    }

    public BakingAgent(LLMClient llmClient, ToolManager toolManager, AgentMemory memory,
                       UserPreferences userPreferences) {
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
        this.toolManager = Objects.requireNonNull(toolManager, "toolManager");
        this.memory = Objects.requireNonNull(memory, "memory");
        this.userPreferences = Objects.requireNonNull(userPreferences, "userPreferences");
    }

    public String processRequest(BakingRequest request) {
        Objects.requireNonNull(request, "request");
        AgentMemory staged = memory.copy();
        String currentStep = "intent and requirement analysis";
        try {
            String input = request.getRequestText();
            if (staged.getPendingClarificationRequest() != null) {
                input = staged.getPendingClarificationRequest() + "\nClarification answer: " + input;
                request = new BakingRequest(input, request.getDesiredServings(), request.getAvailableTime(),
                        request.getAvailableIngredients(), request.getDietaryRestrictions(), request.getTastePreference());
            }
            int timeOnlyMinutes = TimeRecommendationTool.parseAvailableTime(input);
            boolean timeOnlyRequest = timeOnlyMinutes > 0 && !RecipeSearchTool.hasSearchKeywords(input);
            Map<String, Object> analysis = timeOnlyRequest
                    ? Map.of("intent", "time-based recommendation", "availableTime", timeOnlyMinutes)
                    : llmClient.analyzeRequest(input, currentStep);
            String intent = classifyIntent(input, analysis);
            if (UNCLEAR.equals(intent)) {
                if (staged.getClarificationCount() >= 2) intent = "baking request";
                else {
                String clarification = requestClarification();
                staged.setPendingClarification(input, staged.getClarificationCount() + 1);
                staged.addConversationTurn("user", input);
                staged.addConversationTurn("assistant", clarification);
                memory.replaceWith(staged);
                return clarification;
                }
            }

            staged.clearPendingClarification();

            BakingRequest parsed = ((BakingRequestTool) selectTool("baking request")).parseRequest(input, analysis);
            request = mergeRequest(parsed, request);
            request = applyPreferences(request, request);

            String enrichedRequest = withStoredPreferences(request);
            staged.setLastBakingRequest(request);
            currentStep = switch (intent) {
                case "baking request" -> "baking request recipe search";
                case "time-based recommendation" -> "time-based recipe search and ranking";
                case "recipe search" -> "recipe search";
                default -> intent + " tool";
            };
            String result = switch (intent) {
                case "baking request" -> runBakingRequestChain(request, staged);
                case "time-based recommendation" -> runTimeRecommendationChain(request, staged);
                case "recipe search" -> runRecipeSearch(request, staged);
                default -> executeTool(selectTool(intent), withCurrentRecipe(enrichedRequest));
            };
            staged.addConversationTurn("user", request.getRequestText());
            staged.addConversationTurn("assistant", result);
            memory.replaceWith(staged);
            return result;
        } catch (LLMException e) {
            debugFailure(e, currentStep);
            return failureMessage(e);
        } catch (IllegalArgumentException e) {
            return "I couldn't understand that request. " + e.getMessage();
        } catch (RuntimeException e) {
            debugFailure(e, currentStep);
            return "The AI service could not complete that request. Please try again.";
        }
    }

    public String analyzeIntent(String input) {
        if (input == null || input.isBlank()) return UNCLEAR;
        return classifyIntent(input, llmClient.analyzeRequest(input, "intent analysis"));
    }

    private String classifyIntent(String input, Map<String, Object> analysis) {
        Object rawIntent = analysis.get("intent");
        if (!(rawIntent instanceof String label)) return looksLikeBakeRequest(input) ? "baking request" : UNCLEAR;
        String normalized = normalize(label);
        String intent = INTENT_ALIASES.getOrDefault(normalized, UNCLEAR);
        if (UNCLEAR.equals(intent) && looksLikeBakeRequest(input)) return "baking request";
        return intent;
    }

    public AgentTool selectTool(String intent) {
        String canonicalIntent = INTENT_ALIASES.getOrDefault(normalize(intent), UNCLEAR);
        String toolName = INTENT_TO_TOOL.get(canonicalIntent);
        if (toolName == null) throw new IllegalArgumentException("Cannot select a tool for unclear intent.");
        return toolManager.getTool(toolName);
    }

    public String executeTool(AgentTool tool, String input) {
        return Objects.requireNonNull(tool, "tool").execute(input);
    }

    public String generateResponse(String prompt) {
        return llmClient.generateResponse(prompt);
    }

    public String requestClarification() {
        return "I couldn't understand that. Would you like a recipe, a time-based recommendation, "
                + "an ingredient substitution, taste customization, or troubleshooting?";
    }

    public AgentMemory getMemory() { return memory; }
    public List<Recipe> getLastSearchResults() { return memory.getLastSearchResults(); }

    public void cancelPendingClarification() { memory.clearPendingClarification(); }

    private String runBakingRequestChain(BakingRequest original, AgentMemory staged) {
        BakingRequest effectiveRequest = applyPreferences(original, original);
        staged.setLastBakingRequest(effectiveRequest);
        String requestOutput = "Baking request parsed: " + effectiveRequest.getDesiredServings() + " servings, "
                + effectiveRequest.getAvailableTime() + " minutes available, dietary restrictions: "
                + (effectiveRequest.getDietaryRestrictions().isEmpty() ? "none"
                : String.join(", ", effectiveRequest.getDietaryRestrictions())) + ", taste preference: "
                + (effectiveRequest.getTastePreference().isBlank() ? "none" : effectiveRequest.getTastePreference());
        RecipeSearchTool searchTool = (RecipeSearchTool) selectTool("recipe search");
        String searchResult = searchTool.execute(original.getRequestText(),
                effectiveRequest.getDietaryRestrictions(), effectiveRequest.getAvailableTime());
        List<Recipe> matches = searchTool.getLastSearchResults();
        staged.setLastSearchResults(matches);
        if (!matches.isEmpty()) staged.setCurrentRecipe(matches.getFirst());
        return requestOutput + "\n" + searchResult;
    }

    private String runTimeRecommendationChain(BakingRequest request, AgentMemory staged) {
        if (request.getAvailableTime() <= 0) {
            return "Please tell me how many minutes you have so I can recommend recipes that fit.";
        }
        RecipeSearchTool searchTool = (RecipeSearchTool) selectTool("recipe search");
        List<Recipe> matches;
        if (RecipeSearchTool.hasSearchKeywords(request.getRequestText())) {
            matches = searchTool.searchRecipes(request.getRequestText(), request.getDietaryRestrictions(), 0);
        } else if (!staged.getLastSearchResults().isEmpty()) {
            matches = staged.getLastSearchResults();
        } else {
            matches = searchTool.getAllRecipes();
        }
        staged.setLastSearchResults(matches);
        TimeRecommendationTool recommendationTool = (TimeRecommendationTool) selectTool("time-based recommendation");
        List<Recipe> ranked = recommendationTool.recommendRecipes(matches, request.getAvailableTime());
        String result = recommendationTool.formatRecommendations(ranked);
        if (!ranked.isEmpty()) staged.setCurrentRecipe(ranked.getFirst());
        return result;
    }

    private String runRecipeSearch(BakingRequest request, AgentMemory staged) {
        RecipeSearchTool tool = (RecipeSearchTool) selectTool("recipe search");
        List<Recipe> matches = tool.searchRecipes(request.getRequestText(), request.getDietaryRestrictions(),
                request.getAvailableTime());
        staged.setLastSearchResults(matches);
        if (!matches.isEmpty()) staged.setCurrentRecipe(matches.getFirst());
        return tool.formatResults(matches);
    }

    private static BakingRequest mergeRequest(BakingRequest extracted, BakingRequest supplied) {
        LinkedHashSet<String> restrictions = new LinkedHashSet<>(extracted.getDietaryRestrictions());
        restrictions.addAll(supplied.getDietaryRestrictions());
        List<Ingredient> available = extracted.getAvailableIngredients().isEmpty()
                ? supplied.getAvailableIngredients() : extracted.getAvailableIngredients();
        return new BakingRequest(extracted.getRequestText(), firstPositive(extracted.getDesiredServings(),
                supplied.getDesiredServings()), firstPositive(extracted.getAvailableTime(), supplied.getAvailableTime()),
                available, List.copyOf(restrictions), firstNonBlank(extracted.getTastePreference(), supplied.getTastePreference()));
    }

    private BakingRequest applyPreferences(BakingRequest parsed, BakingRequest original) {
        LinkedHashSet<String> restrictions = new LinkedHashSet<>(parsed.getDietaryRestrictions());
        restrictions.addAll(original.getDietaryRestrictions());
        restrictions.addAll(userPreferences.getDietaryRestrictions());
        String taste = firstNonBlank(parsed.getTastePreference(), original.getTastePreference(),
                String.join(", ", userPreferences.getTastePreferences()));
        int servings = firstPositive(parsed.getDesiredServings(), original.getDesiredServings(),
                userPreferences.getPreferredServingSize());
        int time = firstPositive(parsed.getAvailableTime(), original.getAvailableTime(), 0);
        List<Ingredient> available = parsed.getAvailableIngredients().isEmpty()
                ? original.getAvailableIngredients() : parsed.getAvailableIngredients();
        return new BakingRequest(original.getRequestText(), servings, time, available,
                List.copyOf(restrictions), taste);
    }

    private String withStoredPreferences(BakingRequest request) {
        StringBuilder context = new StringBuilder(request.getRequestText());
        if (request.getDesiredServings() > 0) context.append("\nRequested servings: ").append(request.getDesiredServings());
        if (request.getAvailableTime() > 0) context.append("\nAvailable time: ").append(request.getAvailableTime()).append(" minutes");
        if (!request.getDietaryRestrictions().isEmpty()) {
            context.append("\nRequest dietary restrictions: ").append(String.join(", ", request.getDietaryRestrictions()));
        }
        if (!request.getTastePreference().isBlank()) context.append("\nRequest taste preference: ").append(request.getTastePreference());
        if (!userPreferences.getDietaryRestrictions().isEmpty()) {
            context.append("\nDietary restrictions: ").append(String.join(", ", userPreferences.getDietaryRestrictions()));
        }
        if (!userPreferences.getTastePreferences().isEmpty()) {
            context.append("\nTaste preferences: ").append(String.join(", ", userPreferences.getTastePreferences()));
        }
        if (userPreferences.getPreferredServingSize() > 0) {
            context.append("\nPreferred serving size: ").append(userPreferences.getPreferredServingSize());
        }
        return context.toString();
    }

    private String withCurrentRecipe(String input) {
        Recipe current = memory.getCurrentRecipe();
        return current == null ? input : input + "\nCurrent recipe: " + current.getName();
    }

    private static int firstPositive(int... values) {
        for (int value : values) if (value > 0) return value;
        return 0;
    }

    private static String failureMessage(LLMException error) {
        Integer status = error.getHttpStatus();
        if (status != null && (status == 429 || status == 503)) {
            return "The AI service is busy, please try again in a moment.";
        }
        if (error.getMessage() != null && error.getMessage().toLowerCase(Locale.ROOT).contains("timed out")) {
            return "The AI service took too long to respond. Please try again.";
        }
        return "The AI service could not complete that request. Please try again.";
    }

    private static void debugFailure(Throwable error, String step) {
        if (!Boolean.parseBoolean(System.getenv().getOrDefault("BAKING_DEBUG", "false"))) return;
        Integer status = error instanceof LLMException llmError ? llmError.getHttpStatus() : null;
        Throwable cause = error.getCause();
        System.err.println("[BAKING_DEBUG] Request failure: type=" + error.getClass().getName()
                + (cause == null ? "" : ", causeType=" + cause.getClass().getName())
                + ", message=" + Objects.toString(error.getMessage(), "(no message)")
                + ", httpStatus=" + (status == null ? "none" : status) + ", step=" + step);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return "";
    }

    private static String normalize(String intent) {
        return intent == null ? "" : intent.trim().toLowerCase(Locale.ROOT)
                .replace('_', ' ').replace('-', ' ').replaceAll("\\s+", " ");
    }

    private static boolean looksLikeBakeRequest(String input) {
        String text = input.toLowerCase(Locale.ROOT);
        boolean asksToMake = text.matches("(?s).*(\\b(i want|i'd like|i would like|make|bake|prepare|cook)\\b).*" );
        boolean namesFood = text.matches("(?s).*(cookie|cookies|cake|bread|muffin|brownie|pie|pastr|biscuit|scone|cupcake|loaf).*" );
        return asksToMake && namesFood;
    }
}
