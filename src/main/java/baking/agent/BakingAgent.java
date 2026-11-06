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
        try {
            String input = request.getRequestText();
            if (staged.getPendingClarificationRequest() != null) {
                input = staged.getPendingClarificationRequest() + "\nClarification answer: " + input;
                request = new BakingRequest(input, request.getDesiredServings(), request.getAvailableTime(),
                        request.getAvailableIngredients(), request.getDietaryRestrictions(), request.getTastePreference());
            }
            String intent = analyzeIntent(input);
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

            String enrichedRequest = withStoredPreferences(request);
            staged.setLastBakingRequest(request);
            String result = switch (intent) {
                case "baking request" -> runBakingRequestChain(enrichedRequest, request, staged);
                case "time-based recommendation" -> runTimeRecommendationChain(enrichedRequest, request, staged);
                case "recipe search" -> runRecipeSearch(enrichedRequest, staged);
                default -> executeTool(selectTool(intent), withCurrentRecipe(enrichedRequest));
            };
            staged.addConversationTurn("user", request.getRequestText());
            staged.addConversationTurn("assistant", result);
            memory.replaceWith(staged);
            return result;
        } catch (LLMException e) {
            return "I'm sorry, I couldn't complete that baking request right now. "
                    + "Please try again in a moment.";
        }
    }

    public String analyzeIntent(String input) {
        if (input == null || input.isBlank()) return UNCLEAR;
        Object rawIntent = llmClient.analyzeRequest(input).get("intent");
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
        return "I'm not sure what kind of baking help you need. Would you like a recipe, a time-based recommendation, "
                + "an ingredient substitution, taste customization, or troubleshooting?";
    }

    public AgentMemory getMemory() { return memory; }

    private String runBakingRequestChain(String input, BakingRequest original, AgentMemory staged) {
        AgentTool requestTool = selectTool("baking request");
        String requestOutput = executeTool(requestTool, input);
        BakingRequest parsed = ((BakingRequestTool) requestTool).getLastParsedRequest();
        if (parsed == null) throw new IllegalStateException("Baking request tool did not produce a request.");
        BakingRequest effectiveRequest = applyPreferences(parsed, original);
        staged.setLastBakingRequest(effectiveRequest);
        String searchInput = input + "\nParsed request result:\n" + requestOutput
                + "\nUse these requirements: servings=" + effectiveRequest.getDesiredServings()
                + ", availableTime=" + effectiveRequest.getAvailableTime()
                + ", dietaryRestrictions=" + effectiveRequest.getDietaryRestrictions()
                + ", tastePreference=" + effectiveRequest.getTastePreference();
        return runRecipeSearch(searchInput, staged);
    }

    private String runTimeRecommendationChain(String input, BakingRequest request, AgentMemory staged) {
        AgentTool searchTool = selectTool("recipe search");
        String searchOutput = executeTool(searchTool, input);
        List<Recipe> matches = ((RecipeSearchTool) searchTool).getLastSearchResults();
        staged.setLastSearchResults(matches);
        String recommendationInput = input + "\nPrevious recipe search output:\n" + searchOutput
                + "\nAvailable time: " + request.getAvailableTime() + " minutes.";
        AgentTool recommendationTool = selectTool("time-based recommendation");
        String result = executeTool(recommendationTool, recommendationInput);
        List<Recipe> ranked = ((TimeRecommendationTool) recommendationTool).getLastRecommendations();
        if (!ranked.isEmpty()) staged.setCurrentRecipe(ranked.getFirst());
        return result;
    }

    private String runRecipeSearch(String input, AgentMemory staged) {
        AgentTool tool = selectTool("recipe search");
        String result = executeTool(tool, input);
        List<Recipe> matches = ((RecipeSearchTool) tool).getLastSearchResults();
        staged.setLastSearchResults(matches);
        if (!matches.isEmpty()) staged.setCurrentRecipe(matches.getFirst());
        return result;
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
