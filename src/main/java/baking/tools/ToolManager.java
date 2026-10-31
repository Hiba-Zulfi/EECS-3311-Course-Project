package baking.tools;

import baking.llm.LLMClient;
import baking.service.IngredientManager;
import baking.service.RecipeRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Strategy context that registers tools constructed by their Factory Method creators. */
public class ToolManager {
    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    public ToolManager(List<ToolCreator> creators) {
        Objects.requireNonNull(creators, "creators").forEach(creator ->
                registerTool(Objects.requireNonNull(creator, "creator").createTool()));
    }

    public ToolManager(LLMClient llmClient, RecipeRepository repository, IngredientManager ingredientManager) {
        this(defaultCreators(llmClient, repository, ingredientManager));
    }

    public void registerTool(AgentTool tool) {
        Objects.requireNonNull(tool, "tool");
        String name = Objects.requireNonNull(tool.getName(), "tool name").trim();
        if (name.isEmpty()) throw new IllegalArgumentException("tool name must not be blank");
        String key = normalize(name);
        if (tools.putIfAbsent(key, tool) != null) {
            throw new IllegalArgumentException("A tool named '" + name + "' is already registered.");
        }
    }

    public AgentTool getTool(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("tool name must not be blank");
        AgentTool tool = tools.get(normalize(name));
        if (tool == null) throw new IllegalArgumentException("No tool is registered with name '" + name + "'.");
        return tool;
    }

    public List<AgentTool> getAvailableTools() {
        return List.copyOf(new ArrayList<>(tools.values()));
    }

    public String executeTool(String name, String input) {
        return getTool(name).execute(input);
    }

    private static List<ToolCreator> defaultCreators(LLMClient llmClient, RecipeRepository repository,
                                                       IngredientManager ingredientManager) {
        Objects.requireNonNull(llmClient, "llmClient");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(ingredientManager, "ingredientManager");
        return List.of(
                new BakingRequestToolCreator(llmClient),
                new RecipeSearchToolCreator(repository, llmClient),
                new TimeRecommendationToolCreator(repository, llmClient),
                new SubstitutionToolCreator(llmClient, ingredientManager),
                new TasteCustomizationToolCreator(llmClient),
                new TroubleshootingToolCreator(llmClient));
    }

    private static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
