package baking.tools;

import baking.llm.LLMClient;
import baking.service.IngredientManager;

import java.util.Objects;

public final class SubstitutionToolCreator extends ToolCreator {
    private final LLMClient llmClient;
    private final IngredientManager ingredientManager;

    public SubstitutionToolCreator(LLMClient llmClient, IngredientManager ingredientManager) {
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
        this.ingredientManager = Objects.requireNonNull(ingredientManager, "ingredientManager");
    }

    @Override
    public AgentTool createTool() { return new SubstitutionTool(llmClient, ingredientManager); }
}
