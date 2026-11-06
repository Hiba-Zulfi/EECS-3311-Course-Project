package baking;

import baking.controller.AgentController;
import baking.llm.GeminiAdapter;
import baking.llm.LLMClient;
import baking.llm.LLMConfig;
import baking.llm.LLMException;
import baking.view.BakingCLI;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        String option = args.length == 0 ? "" : args[0];
        if ("--llm-test".equals(option)) {
            try {
                LLMClient client = new LLMClient(new GeminiAdapter(LLMConfig.fromEnvironment()));
                System.out.println(client.generateResponse("Say hello"));
            } catch (IllegalStateException | LLMException e) {
                System.err.println("LLM test failed: " + e.getMessage());
            }
            return;
        }
        if ("--gui".equals(option)) {
            System.out.println("The GUI is reserved for a future release.");
            return;
        }
        if (!"--cli".equals(option)) {
            System.out.println("Usage: mvn exec:java -Dexec.args=--cli|--gui|--llm-test");
            return;
        }
        try {
            AgentController controller = AppFactory.createController();
            new BakingCLI(controller).start();
        } catch (IllegalStateException e) {
            System.err.println("Unable to start Baking Agent: " + e.getMessage());
        }
    }
}
