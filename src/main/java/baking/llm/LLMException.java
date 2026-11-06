package baking.llm;

/** A plain-English failure from an LLM provider or response parser. */
public class LLMException extends RuntimeException {
    private final Integer httpStatus;

    public LLMException(String message) {
        this(message, null, null);
    }

    public LLMException(String message, Throwable cause) {
        this(message, cause, null);
    }

    public LLMException(String message, Integer httpStatus) { this(message, null, httpStatus); }

    public LLMException(String message, Throwable cause, Integer httpStatus) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public Integer getHttpStatus() { return httpStatus; }
}
