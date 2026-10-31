package baking.tools;

import baking.llm.LLMClient;

import java.util.Objects;

public final class BakingRequestToolCreator extends ToolCreator {
    private final LLMClient llmClient;

    public BakingRequestToolCreator(LLMClient llmClient) {
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
    }

    @Override
    public AgentTool createTool() { return new BakingRequestTool(llmClient); }
}
