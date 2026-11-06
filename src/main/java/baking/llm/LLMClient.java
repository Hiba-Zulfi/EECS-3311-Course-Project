package baking.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Locale;

/** Higher-level prompts and JSON response parsing over an LLM provider. */
public class LLMClient {
    private final LLMProvider provider;
    private final ObjectMapper objectMapper;

    public LLMClient(LLMProvider provider) {
        this(provider, new ObjectMapper());
    }

    LLMClient(LLMProvider provider, ObjectMapper objectMapper) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public String generateResponse(String prompt) {
        return generateResponse(prompt, "text generation");
    }

    public String generateResponse(String prompt, String step) {
        if (prompt == null || prompt.isBlank()) throw new IllegalArgumentException("prompt must not be blank");
        try {
            return provider.sendPrompt(prompt);
        } catch (RuntimeException e) {
            debugFailure(e, step);
            throw e;
        }
    }

    /** Asks for a JSON object describing intent and returns the parsed fields. */
    public Map<String, Object> analyzeRequest(String prompt) {
        return analyzeRequest(prompt, "request analysis");
    }

    public Map<String, Object> analyzeRequest(String prompt, String step) {
        String instruction = "Classify this baking conversation. Return exactly one JSON object and no other text. "
                + "The intent must be one of: \"baking request\", \"recipe search\", "
                + "\"time-based recommendation\", \"substitution\", \"taste customization\", "
                + "\"troubleshooting\", or \"unclear\". A request to make or bake a named food is "
                + "\"baking request\". A request to find or list recipes is \"recipe search\". "
                + "Include any relevant extracted fields in the same object.\nUser request: "
                + Objects.requireNonNull(prompt, "prompt");
        String response = generateResponse(instruction, step);
        if (Boolean.parseBoolean(System.getenv().getOrDefault("BAKING_DEBUG", "false"))) {
            System.err.println("[BAKING_DEBUG] Raw intent reply: " + response);
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(extractJsonObject(response), new TypeReference<>() { });
            if (parsed == null || parsed.isEmpty()) return Map.of("intent", "unclear");
            Object intentValue = parsed.get("intent");
            if (intentValue instanceof String label) {
                String normalized = label.trim().toLowerCase(Locale.ROOT).replace('_', ' ')
                        .replace('-', ' ').replaceAll("\\s+", " ");
                parsed.put("intent", normalizeIntent(normalized));
            }
            return Map.copyOf(parsed);
        } catch (LLMException e) {
            debugFailure(e, step + " response parsing");
            return Map.of("intent", "unclear");
        } catch (Exception e) {
            debugFailure(e, step + " response parsing");
            return Map.of("intent", "unclear");
        }
    }

    private String extractJsonObject(String response) {
        if (response == null) throw new LLMException("The model returned no intent response.");
        String cleaned = response.replaceAll("(?is)```(?:json)?\\s*", "").replace("```", "").trim();
        int start = cleaned.indexOf('{');
        if (start < 0) throw new LLMException("The model response contained no JSON object.");
        boolean quoted = false;
        boolean escaped = false;
        int depth = 0;
        for (int i = start; i < cleaned.length(); i++) {
            char ch = cleaned.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (ch == '\\') escaped = true;
                else if (ch == '"') quoted = false;
            } else if (ch == '"') quoted = true;
            else if (ch == '{') depth++;
            else if (ch == '}' && --depth == 0) return cleaned.substring(start, i + 1);
        }
        throw new LLMException("The model response contained an incomplete JSON object.");
    }

    private static String normalizeIntent(String intent) {
        return switch (intent) {
            case "baking", "recipe", "make recipe", "baking request", "submit baking request" -> "baking request";
            case "search", "find recipe", "search recipe", "search recipes", "recipe search" -> "recipe search";
            case "time based", "time recommendation", "time based recommendation", "time-based recommendation" -> "time-based recommendation";
            case "ingredient substitution", "substitute", "substitution" -> "substitution";
            case "customize taste", "taste", "taste customization" -> "taste customization";
            case "troubleshoot", "troubleshooting" -> "troubleshooting";
            case "unclear" -> "unclear";
            default -> intent;
        };
    }

    /** Asks the model to order options and parses its JSON string array. */
    public List<String> rankOptions(List<String> options) {
        return rankOptions(options, "option ranking");
    }

    public List<String> rankOptions(List<String> options, String step) {
        Objects.requireNonNull(options, "options");
        if (options.isEmpty()) return List.of();
        try {
            String optionsJson = objectMapper.writeValueAsString(options);
            String response = generateResponse("Rank these baking options from best to worst for the user's request. "
                    + "Return only a JSON array of option strings, without markdown. Options: " + optionsJson, step);
            List<String> ranked = objectMapper.readValue(response, new TypeReference<>() { });
            if (ranked == null || ranked.stream().anyMatch(Objects::isNull)) {
                LLMException failure = new LLMException("The model returned an invalid ranked options list.");
                debugFailure(failure, step + " response parsing");
                throw failure;
            }
            return List.copyOf(ranked);
        } catch (LLMException e) {
            throw e;
        } catch (Exception e) {
            LLMException failure = new LLMException("The model response was not a valid JSON options list.", e);
            debugFailure(failure, step + " response parsing");
            throw failure;
        }
    }

    private static void debugFailure(Throwable failure, String step) {
        if (!Boolean.parseBoolean(System.getenv().getOrDefault("BAKING_DEBUG", "false"))) return;
        Integer status = failure instanceof LLMException llmFailure ? llmFailure.getHttpStatus() : null;
        String message = failure.getMessage() == null ? "(no message)" : failure.getMessage();
        Throwable cause = failure.getCause();
        System.err.println("[BAKING_DEBUG] LLM failure: type=" + failure.getClass().getName()
                + (cause == null ? "" : ", causeType=" + cause.getClass().getName())
                + ", message=" + message + ", httpStatus=" + (status == null ? "none" : status)
                + ", step=" + step);
    }
}
