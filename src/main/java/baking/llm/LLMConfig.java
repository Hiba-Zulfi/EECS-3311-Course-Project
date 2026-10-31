package baking.llm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Loads Gemini credentials and model selection from environment variables or a dotenv file. */
public final class LLMConfig {
    private final String apiKey;
    private final String model;
    private final Duration requestTimeout;

    private LLMConfig(String apiKey, String model, Duration requestTimeout) {
        this.apiKey = apiKey;
        this.model = model;
        this.requestTimeout = requestTimeout;
    }

    public static LLMConfig fromEnvironment() {
        return load(Path.of(".env"), System.getenv());
    }

    public static LLMConfig load(Path envFile, Map<String, String> environment) {
        Objects.requireNonNull(envFile, "envFile");
        Objects.requireNonNull(environment, "environment");
        Map<String, String> values = new HashMap<>();
        if (Files.exists(envFile)) {
            try {
                for (String line : Files.readAllLines(envFile)) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                    int separator = trimmed.indexOf('=');
                    if (separator <= 0) continue;
                    String name = trimmed.substring(0, separator).trim();
                    String value = unquote(trimmed.substring(separator + 1).trim());
                    values.put(name, value);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Unable to read the .env configuration file.", e);
            }
        }
        values.putAll(environment);
        String apiKey = nonBlank(values.get("LLM_API_KEY"));
        String model = nonBlank(values.get("LLM_MODEL"));
        if (apiKey == null || apiKey.equals("YOUR_LOCAL_API_KEY_HERE")) {
            throw new IllegalStateException("Set LLM_API_KEY in the environment or .env file.");
        }
        if (model == null || model.equals("YOUR_MODEL_NAME_HERE")) {
            throw new IllegalStateException("Set LLM_MODEL in the environment or .env file.");
        }
        return new LLMConfig(apiKey, model, Duration.ofSeconds(30));
    }

    String apiKey() { return apiKey; }
    public String model() { return model; }
    public Duration requestTimeout() { return requestTimeout; }

    private static String nonBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
