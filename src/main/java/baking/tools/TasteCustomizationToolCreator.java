package baking.tools;

import baking.llm.LLMClient;

import java.util.Objects;

public final class TasteCustomizationToolCreator extends ToolCreator {
    private final LLMClient llmClient;

    public TasteCustomizationToolCreator(LLMClient llmClient) {
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
    }

    @Override
    public AgentTool createTool() { return new TasteCustomizationTool(llmClient); }
}
