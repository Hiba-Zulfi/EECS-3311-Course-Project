package baking.tools;

import baking.llm.LLMClient;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Explains likely causes of baking problems and proposes practical fixes. */
public class TroubleshootingTool implements AgentTool {
    private final LLMClient llmClient;

    public TroubleshootingTool(LLMClient llmClient) {
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
    }

    @Override
    public String execute(String input) {
        if (input == null || input.isBlank()) return requestAdditionalInformation();
        return "Troubleshooting advice: " + llmClient.generateResponse(
                "Diagnose this baking problem. First list likely causes, then give up to four practical fixes, "
                        + "one per line. Be concise and note uncertainty where appropriate. Problem: " + input,
                "troubleshooting diagnosis and fixes");
    }

    public String analyzeProblem(String problem) {
        if (problem == null || problem.isBlank()) return requestAdditionalInformation();
        return llmClient.generateResponse("Identify the most likely causes of this baking problem. "
                + "Keep the explanation concise and mention uncertainty where appropriate: " + problem,
                "troubleshooting diagnosis");
    }

    public List<String> suggestSolutions(String problem) {
        if (problem == null || problem.isBlank()) return List.of(requestAdditionalInformation());
        String response = llmClient.generateResponse("Give up to four practical, numbered or bulleted fixes for "
                + "this baking problem. Return one fix per line: " + problem, "troubleshooting fixes");
        List<String> fixes = Arrays.stream(response.split("\\R"))
                .map(line -> line.replaceFirst("^\\s*(?:[-*•]|\\d+[.)])\\s*", "").trim())
                .filter(line -> !line.isEmpty()).toList();
        return fixes.isEmpty() ? List.of("Check oven temperature with an oven thermometer and verify ingredient measurements.") : fixes;
    }

    public String requestAdditionalInformation() {
        return "Please describe what happened, the recipe, oven temperature, and baking time so I can troubleshoot it.";
    }

    @Override
    public String getName() { return "TroubleshootingTool"; }
}
