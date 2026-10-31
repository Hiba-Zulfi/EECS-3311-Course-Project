package baking.tools;

/** Factory Method base class for constructing one concrete agent tool. */
public abstract class ToolCreator {
    public abstract AgentTool createTool();
}
