package baking.tools;

import baking.llm.LLMClient;
import baking.service.RecipeRepository;

import java.util.Objects;

public final class RecipeSearchToolCreator extends ToolCreator {
    private final RecipeRepository repository;
    private final LLMClient llmClient;

    public RecipeSearchToolCreator(RecipeRepository repository, LLMClient llmClient) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
    }

    @Override
    public AgentTool createTool() { return new RecipeSearchTool(repository, llmClient); }
}
