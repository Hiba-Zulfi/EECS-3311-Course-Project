package baking.agent;

import baking.llm.LLMClient;
import baking.model.BakingRequest;
import baking.model.UserPreferences;
import baking.tools.AgentTool;
import baking.tools.ToolManager;

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
        String intent = analyzeIntent(request.getRequestText());
        if (UNCLEAR.equals(intent)) return requestClarification();
        AgentTool tool = selectTool(intent);
        return executeTool(tool, withStoredPreferences(request.getRequestText()));
    }

    public String analyzeIntent(String input) {
        if (input == null || input.isBlank()) return UNCLEAR;
        Object rawIntent = llmClient.analyzeRequest(input).get("intent");
        if (!(rawIntent instanceof String label)) return UNCLEAR;
        return INTENT_ALIASES.getOrDefault(normalize(label), UNCLEAR);
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
        return "I’m not sure what kind of baking help you need. Would you like a recipe, a time-based recommendation, "
                + "an ingredient substitution, taste customization, or troubleshooting?";
    }

    public AgentMemory getMemory() { return memory; }

    private String withStoredPreferences(String requestText) {
        StringBuilder context = new StringBuilder(requestText);
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

    private static String normalize(String intent) {
        return intent == null ? "" : intent.trim().toLowerCase(Locale.ROOT)
                .replace('_', ' ').replace('-', ' ').replaceAll("\\s+", " ");
    }
}
