package baking.llm;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class GeminiAdapterTest {
    @Test
    void sendsGeminiRequestAndReadsTextResponse() throws Exception {
        withServer(200, "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hello" +
                " Gemini\"}]}}]}", 0, (adapter, server) -> {
            assertEquals("Hello Gemini", adapter.sendPrompt("Say hello"));
            assertEquals("", adapter.getLastError());
        });
    }

    @Test
    void rateLimitHasClearError() throws Exception {
        withServer(429, "{}", 0, (adapter, server) -> {
            LLMException error = assertThrows(LLMException.class, () -> adapter.sendPrompt("hello"));
            assertTrue(error.getMessage().contains("rate limit"));
            assertEquals(429, error.getHttpStatus());
            assertEquals(error.getMessage(), adapter.getLastError());
        });
    }

    @Test
    void retries503WithExponentialBackoff() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/generate", exchange -> {
            int attempt = requests.incrementAndGet();
            int status = attempt <= 3 ? 503 : 200;
            byte[] body = (status == 200 ? "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"ok\"}]}}]}" : "busy")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            List<Duration> delays = new ArrayList<>();
            GeminiAdapter adapter = adapter(server, Duration.ofSeconds(2), delays::add);
            assertEquals("ok", adapter.sendPrompt("hello"));
            assertEquals(4, requests.get());
            assertEquals(List.of(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4)), delays);
        } finally { server.stop(0); }
    }

    @Test
    void retryAfterHeaderOverridesBackoff() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/generate", exchange -> {
            int attempt = requests.incrementAndGet();
            if (attempt == 1) exchange.getResponseHeaders().add("Retry-After", "3");
            int status = attempt == 1 ? 429 : 200;
            byte[] body = (status == 200 ? "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"ok\"}]}}]}" : "busy")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            List<Duration> delays = new ArrayList<>();
            GeminiAdapter adapter = adapter(server, Duration.ofSeconds(2), delays::add);
            assertEquals("ok", adapter.sendPrompt("hello"));
            assertEquals(List.of(Duration.ofSeconds(3)), delays);
        } finally { server.stop(0); }
    }

    @Test
    void otherHttpFailuresHaveClearErrorWithoutEchoingResponseBody() throws Exception {
        withServer(403, "contains-private-server-details", 0, (adapter, server) -> {
            LLMException error = assertThrows(LLMException.class, () -> adapter.sendPrompt("hello"));
            assertTrue(error.getMessage().contains("HTTP 403"));
            assertFalse(error.getMessage().contains("private"));
        });
    }

    @Test
    void emptyAndMalformedResponsesHaveClearErrors() throws Exception {
        withServer(200, "{\"candidates\":[]}", 0, (adapter, server) -> {
            assertTrue(assertThrows(LLMException.class, () -> adapter.sendPrompt("hello"))
                    .getMessage().contains("empty response"));
        });
        withServer(200, "not json", 0, (adapter, server) -> {
            assertTrue(assertThrows(LLMException.class, () -> adapter.sendPrompt("hello"))
                    .getMessage().contains("invalid JSON"));
        });
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void requestTimeoutIsReportedClearly() throws Exception {
        withServer(200, "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"late\"}]}}]}",
                500, (adapter, server) -> {
                    LLMException error = assertThrows(LLMException.class, () -> adapter.sendPrompt("hello"));
                    assertTrue(error.getMessage().contains("timed out"));
                }, Duration.ofMillis(75));
    }

    private static void withServer(int status, String body, long delayMillis, AdapterAction action) throws Exception {
        withServer(status, body, delayMillis, action, Duration.ofSeconds(2));
    }

    private static void withServer(int status, String body, long delayMillis, AdapterAction action,
                                   Duration timeout) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/generate", exchange -> {
            try {
                if (delayMillis > 0) Thread.sleep(delayMillis);
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // The timeout test intentionally closes a client connection before the delayed response.
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            LLMConfig config = LLMConfig.load(PathForTests.dummy(),
                    Map.of("LLM_API_KEY", "test-key", "LLM_MODEL", "gemini-test"));
            GeminiAdapter adapter = new GeminiAdapter(config, HttpClient.newHttpClient(),
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/generate"),
                    timeout, new com.fasterxml.jackson.databind.ObjectMapper(), delay -> { });
            action.run(adapter, server);
        } finally {
            server.stop(0);
        }
    }

    private static GeminiAdapter adapter(HttpServer server, Duration timeout, GeminiAdapter.Sleeper sleeper) {
        LLMConfig config = LLMConfig.load(PathForTests.dummy(),
                Map.of("LLM_API_KEY", "test-key", "LLM_MODEL", "gemini-test"));
        return new GeminiAdapter(config, HttpClient.newHttpClient(),
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/generate"), timeout,
                new com.fasterxml.jackson.databind.ObjectMapper(), sleeper);
    }

    @FunctionalInterface
    private interface AdapterAction {
        void run(GeminiAdapter adapter, HttpServer server) throws Exception;
    }

    private static final class PathForTests {
        private static java.nio.file.Path dummy() {
            return java.nio.file.Path.of("nonexistent-test-dotenv");
        }
    }
}
