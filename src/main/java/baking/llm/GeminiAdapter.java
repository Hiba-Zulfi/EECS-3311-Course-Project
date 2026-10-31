package baking.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Objects;

/** Google Gemini REST adapter for the provider-neutral LLM interface. */
public class GeminiAdapter implements LLMProvider {
    private static final String DEFAULT_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/";

    private final LLMConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final URI endpoint;
    private final Duration requestTimeout;
    private volatile String lastError = "";

    public GeminiAdapter(LLMConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(config.requestTimeout()).build(),
                URI.create(DEFAULT_ENDPOINT + normalizeModel(config.model()) + ":generateContent"),
                config.requestTimeout(), new ObjectMapper());
    }

    GeminiAdapter(LLMConfig config, HttpClient httpClient, URI endpoint,
                  Duration requestTimeout, ObjectMapper mapper) {
        this.config = Objects.requireNonNull(config, "config");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public String sendPrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) throw new IllegalArgumentException("prompt must not be blank");
        try {
            ObjectNode requestBody = mapper.createObjectNode();
            requestBody.putArray("contents").addObject().putArray("parts").addObject().put("text", prompt);
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", config.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) {
                throw fail("Gemini rate limit reached. Please wait and try again.");
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw fail("Gemini could not complete the request (HTTP " + response.statusCode() + ").");
            }
            String text = extractText(response.body());
            if (text.isBlank()) throw fail("Gemini returned an empty response.");
            lastError = "";
            return text;
        } catch (LLMException e) {
            throw e;
        } catch (HttpTimeoutException e) {
            throw fail("Gemini request timed out. Please try again.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw fail("Gemini request was interrupted.", e);
        } catch (IOException e) {
            throw fail("Gemini could not be reached. Check your connection and try again.", e);
        } catch (RuntimeException e) {
            if (e instanceof LLMException llmException) throw llmException;
            throw fail("Gemini returned a response that could not be read.", e);
        }
    }

    @Override
    public String getLastError() {
        return lastError;
    }

    private String extractText(String responseBody) {
        try {
            JsonNode root = mapper.readTree(responseBody);
            JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
            if (!parts.isArray()) return "";
            StringBuilder text = new StringBuilder();
            for (JsonNode part : parts) {
                JsonNode textPart = part.get("text");
                if (textPart != null && textPart.isTextual()) text.append(textPart.asText());
            }
            return text.toString().trim();
        } catch (IOException e) {
            throw fail("Gemini returned invalid JSON.", e);
        }
    }

    private LLMException fail(String message) {
        lastError = message;
        return new LLMException(message);
    }

    private LLMException fail(String message, Throwable cause) {
        lastError = message;
        return new LLMException(message, cause);
    }

    private static String normalizeModel(String model) {
        String normalized = model.startsWith("models/") ? model.substring("models/".length()) : model;
        if (!normalized.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("LLM_MODEL must be a valid Gemini model name.");
        }
        return normalized;
    }
}
