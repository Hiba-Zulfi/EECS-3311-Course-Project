package baking.llm;

/** A plain-English failure from an LLM provider or response parser. */
public class LLMException extends RuntimeException {
    public LLMException(String message) {
        super(message);
    }

    public LLMException(String message, Throwable cause) {
        super(message, cause);
    }
}
