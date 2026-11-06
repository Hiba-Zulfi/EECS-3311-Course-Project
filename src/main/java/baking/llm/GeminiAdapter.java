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
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/** Google Gemini REST adapter for the provider-neutral LLM interface. */
public class GeminiAdapter implements LLMProvider {
    private static final String DEFAULT_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/";

    private final LLMConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final URI endpoint;
    private final Duration requestTimeout;
    private final Sleeper sleeper;
    private volatile String lastError = "";

    public GeminiAdapter(LLMConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(config.requestTimeout()).build(),
                URI.create(DEFAULT_ENDPOINT + normalizeModel(config.model()) + ":generateContent"),
                config.requestTimeout(), new ObjectMapper(), duration -> Thread.sleep(duration.toMillis()));
    }

    GeminiAdapter(LLMConfig config, HttpClient httpClient, URI endpoint,
                  Duration requestTimeout, ObjectMapper mapper) {
        this(config, httpClient, endpoint, requestTimeout, mapper,
                duration -> Thread.sleep(duration.toMillis()));
    }

    GeminiAdapter(LLMConfig config, HttpClient httpClient, URI endpoint,
                  Duration requestTimeout, ObjectMapper mapper, Sleeper sleeper) {
        this.config = Objects.requireNonNull(config, "config");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
    }

    @Override
    public String sendPrompt(String prompt) {
        if (prompt == null || prompt.isBlank()) throw new IllegalArgumentException("prompt must not be blank");
        ObjectNode requestBody = mapper.createObjectNode();
        requestBody.putArray("contents").addObject().putArray("parts").addObject().put("text", prompt);
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(endpoint).timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", config.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestBody))).build();
        } catch (IOException | RuntimeException e) {
            if (e instanceof LLMException llmException) throw llmException;
            throw fail("Gemini request could not be prepared.", e);
        }
        for (int attempt = 0; attempt <= 3; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (retryableStatus(status) && attempt < 3) {
                    waitBeforeRetry(retryAfter(response, attempt));
                    continue;
                }
                if (status < 200 || status >= 300) {
                    String message = status == 429
                            ? "Gemini rate limit reached (HTTP 429). Please try again."
                            : "Gemini could not complete the request (HTTP " + status + ").";
                    throw fail(message, status);
                }
                String text = extractText(response.body());
                if (text.isBlank()) throw fail("Gemini returned an empty response.");
                lastError = "";
                return text;
            } catch (HttpTimeoutException e) {
                if (attempt < 3) {
                    waitBeforeRetry(backoff(attempt));
                    continue;
                }
                throw fail("Gemini request timed out after retries.", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw fail("Gemini request was interrupted.", e);
            } catch (IOException e) {
                throw fail("Gemini could not be reached. Check your connection and try again.", e);
            } catch (LLMException e) {
                throw e;
            } catch (RuntimeException e) {
                throw fail("Gemini returned a response that could not be read.", e);
            }
        }
        throw fail("Gemini could not complete the request after retries.");
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

    private LLMException fail(String message, int status) {
        lastError = message;
        return new LLMException(message, status);
    }

    private void waitBeforeRetry(Duration delay) {
        try {
            sleeper.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw fail("Gemini retry was interrupted.", e);
        }
    }

    private static boolean retryableStatus(int status) { return status == 429 || status == 503; }

    private static Duration backoff(int attempt) { return Duration.ofSeconds(1L << attempt); }

    private static Duration retryAfter(HttpResponse<?> response, int attempt) {
        String value = response.headers().firstValue("Retry-After").orElse("").trim();
        if (!value.isEmpty()) {
            try { return Duration.ofSeconds(Math.max(0, Long.parseLong(value))); }
            catch (NumberFormatException ignored) {
                try {
                    Instant when = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                    return Duration.between(Instant.now(), when).isNegative()
                            ? Duration.ZERO : Duration.between(Instant.now(), when);
                } catch (RuntimeException ignoredDate) { /* Use exponential backoff for invalid values. */ }
            }
        }
        return backoff(attempt);
    }

    @FunctionalInterface
    interface Sleeper { void sleep(Duration duration) throws InterruptedException; }

    private static String normalizeModel(String model) {
        String normalized = model.startsWith("models/") ? model.substring("models/".length()) : model;
        if (!normalized.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("LLM_MODEL must be a valid Gemini model name.");
        }
        return normalized;
    }
}
