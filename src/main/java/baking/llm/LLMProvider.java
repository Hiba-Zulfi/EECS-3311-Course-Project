package baking.llm;

/** Provider-neutral interface for submitting a text prompt to a language model. */
public interface LLMProvider {
    String sendPrompt(String prompt);

    /** Returns the most recent provider error in plain English, or an empty string. */
    String getLastError();
}
