package baking.tools;

/** Strategy contract shared by all baking agent tools. */
public interface AgentTool {
    String execute(String input);
    String getName();
}
