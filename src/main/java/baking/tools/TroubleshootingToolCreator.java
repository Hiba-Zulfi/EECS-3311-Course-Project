package baking.tools;

import baking.llm.LLMClient;

import java.util.Objects;

public final class TroubleshootingToolCreator extends ToolCreator {
    private final LLMClient llmClient;

    public TroubleshootingToolCreator(LLMClient llmClient) {
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
    }

    @Override
    public AgentTool createTool() { return new TroubleshootingTool(llmClient); }
}
