package baking;

import baking.llm.GeminiAdapter;
import baking.llm.LLMClient;
import baking.llm.LLMConfig;
import baking.llm.LLMException;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        if (args.length > 0 && "--llm-test".equals(args[0])) {
            try {
                LLMClient client = new LLMClient(new GeminiAdapter(LLMConfig.fromEnvironment()));
                System.out.println(client.generateResponse("Say hello"));
            } catch (IllegalStateException | LLMException e) {
                System.err.println("LLM test failed: " + e.getMessage());
            }
            return;
        }
        System.out.println("Baking Agent starting");
    }
}
