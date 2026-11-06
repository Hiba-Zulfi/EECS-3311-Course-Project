package baking.tools;

import baking.llm.LLMClient;
import baking.model.BakingRequest;
import baking.model.Ingredient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Extracts structured baking requirements from free-form user text. */
public class BakingRequestTool implements AgentTool {
    private final LLMClient llmClient;
    private BakingRequest lastParsedRequest;

    public BakingRequestTool(LLMClient llmClient) {
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
    }

    @Override
    public String execute(String input) {
        BakingRequest request = parseRequest(input);
        return "Baking request parsed: " + request.getDesiredServings() + " servings, "
                + request.getAvailableTime() + " minutes available, dietary restrictions: "
                + (request.getDietaryRestrictions().isEmpty() ? "none" : String.join(", ", request.getDietaryRestrictions()))
                + ", taste preference: " + (request.getTastePreference().isBlank() ? "none" : request.getTastePreference());
    }

    public BakingRequest parseRequest(String input) {
        if (input == null || input.isBlank()) throw new IllegalArgumentException("request text must not be blank");
        Map<String, Object> fields = llmClient.analyzeRequest(input);
        int servings = integer(fields, 0, "desiredServings", "servings", "servingCount");
        int availableTime = integer(fields, 0, "availableTime", "availableTimeMinutes", "timeMinutes", "time");
        List<String> restrictions = strings(fields.getOrDefault("dietaryRestrictions",
                fields.getOrDefault("dietaryRestriction", fields.get("dietary"))));
        String taste = text(fields, "tastePreference", "taste", "flavorPreference");
        lastParsedRequest = new BakingRequest(input, servings, availableTime, List.<Ingredient>of(), restrictions, taste);
        if (debugEnabled()) {
            System.err.println("[BAKING_DEBUG] Extracted BakingRequest: requestText=" + input + ", servings=" + servings
                    + ", availableTime=" + availableTime + ", dietaryRestrictions=" + restrictions
                    + ", tastePreference=" + taste);
        }
        return lastParsedRequest;
    }

    public BakingRequest getLastParsedRequest() { return lastParsedRequest; }

    @Override
    public String getName() { return "BakingRequestTool"; }

    private static boolean debugEnabled() {
        return Boolean.parseBoolean(System.getenv().getOrDefault("BAKING_DEBUG", "false"));
    }

    private static int integer(Map<String, Object> values, int fallback, String... keys) {
        for (String key : keys) {
            Object value = values.get(key);
            if (value instanceof Number number) return Math.max(0, number.intValue());
            if (value instanceof String text) {
                try { return Math.max(0, Integer.parseInt(text.trim())); }
                catch (NumberFormatException ignored) { /* Try another known field. */ }
            }
        }
        return fallback;
    }

    private static List<String> strings(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().filter(String.class::isInstance).map(String.class::cast)
                    .map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
        if (value instanceof String text && !text.isBlank()) {
            return List.of(text.split("[,;]")).stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
        return List.of();
    }

    private static String text(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            Object value = values.get(key);
            if (value instanceof String string) return string.trim();
        }
        return "";
    }
}
