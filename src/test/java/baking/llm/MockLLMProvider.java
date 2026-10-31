package baking.llm;

import java.util.ArrayList;
import java.util.List;

/** In-memory provider used by unit tests; it never makes network calls. */
public class MockLLMProvider implements LLMProvider {
    private final List<String> prompts = new ArrayList<>();
    private String response;
    private String errorMessage = "";
    private RuntimeException failure;

    public MockLLMProvider(String response) {
        this.response = response;
    }

    @Override
    public String sendPrompt(String prompt) {
        prompts.add(prompt);
        if (failure != null) throw failure;
        return response;
    }

    @Override
    public String getLastError() {
        return errorMessage;
    }

    public void setResponse(String response) { this.response = response; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public void setFailure(RuntimeException failure) { this.failure = failure; }
    public List<String> getPrompts() { return List.copyOf(prompts); }
}
