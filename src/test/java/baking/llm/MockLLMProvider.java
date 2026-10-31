package baking.llm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** In-memory provider used by unit tests; it never makes network calls. */
public class MockLLMProvider implements LLMProvider {
    private final List<String> prompts = new ArrayList<>();
    private String response;
    private String errorMessage = "";
    private RuntimeException failure;
    private List<String> queuedResponses = List.of();
    private int responseIndex;
    private int failureOnCall = -1;
    private RuntimeException queuedFailure;

    public MockLLMProvider(String response) {
        this.response = response;
    }

    @Override
    public String sendPrompt(String prompt) {
        prompts.add(prompt);
        if (prompts.size() == failureOnCall) throw queuedFailure;
        if (failure != null) throw failure;
        if (responseIndex < queuedResponses.size()) return queuedResponses.get(responseIndex++);
        if (!queuedResponses.isEmpty()) throw new IllegalStateException("No mock response configured for this LLM call.");
        return response;
    }

    @Override
    public String getLastError() {
        return errorMessage;
    }

    public void setResponse(String response) {
        this.response = response;
        this.queuedResponses = List.of();
        this.responseIndex = 0;
        this.failureOnCall = -1;
        this.queuedFailure = null;
    }
    public void setResponses(String... responses) {
        this.queuedResponses = List.copyOf(Arrays.asList(responses));
        this.responseIndex = 0;
        this.response = null;
        this.failureOnCall = -1;
        this.queuedFailure = null;
    }
    public void setFailureOnCall(int callNumber, RuntimeException failure) {
        if (callNumber < 1) throw new IllegalArgumentException("callNumber must be positive");
        this.failureOnCall = callNumber;
        this.queuedFailure = failure;
    }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public void setFailure(RuntimeException failure) { this.failure = failure; }
    public List<String> getPrompts() { return List.copyOf(prompts); }
}
