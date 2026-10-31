package baking.llm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Objects;

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
        if (prompt == null || prompt.isBlank()) throw new IllegalArgumentException("prompt must not be blank");
        return provider.sendPrompt(prompt);
    }

    /** Asks for a JSON object describing intent and returns the parsed fields. */
    public Map<String, Object> analyzeRequest(String prompt) {
        String instruction = "Analyze the user's baking request and return only a JSON object with an "
                + "\"intent\" field and any relevant extracted fields. Do not include markdown.\nUser request: "
                + Objects.requireNonNull(prompt, "prompt");
        String response = generateResponse(instruction);
        try {
            Map<String, Object> parsed = objectMapper.readValue(response, new TypeReference<>() { });
            if (parsed == null || parsed.isEmpty()) throw new LLMException("The model returned an empty intent object.");
            return Map.copyOf(parsed);
        } catch (LLMException e) {
            throw e;
        } catch (Exception e) {
            throw new LLMException("The model response was not valid intent JSON.", e);
        }
    }

    /** Asks the model to order options and parses its JSON string array. */
    public List<String> rankOptions(List<String> options) {
        Objects.requireNonNull(options, "options");
        if (options.isEmpty()) return List.of();
        try {
            String optionsJson = objectMapper.writeValueAsString(options);
            String response = generateResponse("Rank these baking options from best to worst for the user's request. "
                    + "Return only a JSON array of option strings, without markdown. Options: " + optionsJson);
            List<String> ranked = objectMapper.readValue(response, new TypeReference<>() { });
            if (ranked == null || ranked.stream().anyMatch(Objects::isNull)) {
                throw new LLMException("The model returned an invalid ranked options list.");
            }
            return List.copyOf(ranked);
        } catch (LLMException e) {
            throw e;
        } catch (Exception e) {
            throw new LLMException("The model response was not a valid JSON options list.", e);
        }
    }
}
