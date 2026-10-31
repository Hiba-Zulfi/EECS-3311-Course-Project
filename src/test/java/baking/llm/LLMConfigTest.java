package baking.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LLMConfigTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void loadsKeyAndModelFromDotEnvAndEnvironmentOverridesThem() throws Exception {
        Path envFile = temporaryDirectory.resolve(".env");
        Files.writeString(envFile, "LLM_API_KEY=dotenv-secret\nLLM_MODEL=gemini-example\n");
        LLMConfig fromFile = LLMConfig.load(envFile, Map.of());
        assertEquals("gemini-example", fromFile.model());

        LLMConfig overridden = LLMConfig.load(envFile,
                Map.of("LLM_API_KEY", "environment-secret", "LLM_MODEL", "gemini-override"));
        assertEquals("gemini-override", overridden.model());
        assertEquals("environment-secret", overridden.apiKey());
        assertEquals(30, overridden.requestTimeout().toSeconds());
    }

    @Test
    void rejectsMissingOrExampleCredentialsWithoutRevealingTheKey() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> LLMConfig.load(temporaryDirectory.resolve("absent"), Map.of()));
        assertTrue(error.getMessage().contains("LLM_API_KEY"));
        assertFalse(error.getMessage().contains("YOUR_LOCAL_API_KEY_HERE"));
    }
}
