package baking.llm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LLMClientTest {
    @Test
    void generateResponseDelegatesPromptToProvider() {
        MockLLMProvider provider = new MockLLMProvider("Hello from Gemini");
        LLMClient client = new LLMClient(provider);
        assertEquals("Hello from Gemini", client.generateResponse("Say hello"));
        assertEquals("Say hello", provider.getPrompts().getFirst());
    }

    @Test
    void analyzeRequestPromptsForAndParsesIntentJson() {
        MockLLMProvider provider = new MockLLMProvider("{\"intent\":\"find_recipe\",\"time\":30}");
        LLMClient client = new LLMClient(provider);
        Map<String, Object> result = client.analyzeRequest("I need a quick cake");
        assertEquals("find_recipe", result.get("intent"));
        assertEquals(30, result.get("time"));
        assertTrue(provider.getPrompts().getFirst().contains("JSON object"));
        assertTrue(provider.getPrompts().getFirst().contains("I need a quick cake"));
    }

    @Test
    void invalidIntentJsonBecomesPlainLanguageLlmException() {
        LLMClient client = new LLMClient(new MockLLMProvider("not JSON"));
        LLMException error = assertThrows(LLMException.class, () -> client.analyzeRequest("cookies"));
        assertTrue(error.getMessage().contains("valid intent JSON"));
    }

    @Test
    void rankOptionsParsesJsonArrayAndHandlesEmptyInput() {
        MockLLMProvider provider = new MockLLMProvider("[\"muffin\",\"cake\"]");
        LLMClient client = new LLMClient(provider);
        assertEquals(List.of("muffin", "cake"), client.rankOptions(List.of("cake", "muffin")));
        assertTrue(client.rankOptions(List.of()).isEmpty());
    }

    @Test
    void providerFailuresAreExposedAsLlmExceptionsAndPlainEnglishErrors() {
        MockLLMProvider provider = new MockLLMProvider("");
        provider.setErrorMessage("The model service is temporarily unavailable.");
        provider.setFailure(new LLMException(provider.getLastError()));
        LLMClient client = new LLMClient(provider);
        LLMException error = assertThrows(LLMException.class, () -> client.generateResponse("hello"));
        assertEquals("The model service is temporarily unavailable.", error.getMessage());
        assertEquals("The model service is temporarily unavailable.", provider.getLastError());
    }
}
