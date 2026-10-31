package baking.agent;

import baking.model.BakingRequest;
import baking.model.Recipe;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Session memory for the active recipe, request, search results, and recent dialog. */
public class AgentMemory {
    private static final int DEFAULT_TURN_LIMIT = 20;

    private final int turnLimit;
    private Recipe currentRecipe;
    private List<Recipe> lastSearchResults = List.of();
    private BakingRequest lastBakingRequest;
    private final ArrayDeque<ConversationTurn> recentConversationTurns = new ArrayDeque<>();

    public AgentMemory() { this(DEFAULT_TURN_LIMIT); }

    public AgentMemory(int turnLimit) {
        if (turnLimit < 1) throw new IllegalArgumentException("turnLimit must be positive");
        this.turnLimit = turnLimit;
    }

    public Recipe getCurrentRecipe() { return currentRecipe; }
    public void setCurrentRecipe(Recipe currentRecipe) { this.currentRecipe = currentRecipe; }

    public List<Recipe> getLastSearchResults() { return List.copyOf(lastSearchResults); }
    public void setLastSearchResults(List<Recipe> lastSearchResults) {
        this.lastSearchResults = List.copyOf(Objects.requireNonNull(lastSearchResults, "lastSearchResults"));
    }

    public BakingRequest getLastBakingRequest() { return lastBakingRequest; }
    public void setLastBakingRequest(BakingRequest lastBakingRequest) { this.lastBakingRequest = lastBakingRequest; }

    public List<ConversationTurn> getRecentConversationTurns() { return List.copyOf(recentConversationTurns); }

    public void addConversationTurn(String role, String content) {
        recentConversationTurns.addLast(new ConversationTurn(role, content));
        while (recentConversationTurns.size() > turnLimit) recentConversationTurns.removeFirst();
    }

    /** Creates an independent session-state copy for transactional agent processing. */
    public AgentMemory copy() {
        AgentMemory copy = new AgentMemory(turnLimit);
        copy.currentRecipe = currentRecipe;
        copy.lastSearchResults = new ArrayList<>(lastSearchResults);
        copy.lastBakingRequest = lastBakingRequest;
        copy.recentConversationTurns.addAll(recentConversationTurns);
        return copy;
    }

    /** Replaces this memory's state with a previously prepared state. */
    public void replaceWith(AgentMemory source) {
        Objects.requireNonNull(source, "source");
        currentRecipe = source.currentRecipe;
        lastSearchResults = List.copyOf(source.lastSearchResults);
        lastBakingRequest = source.lastBakingRequest;
        recentConversationTurns.clear();
        recentConversationTurns.addAll(source.recentConversationTurns);
    }

    public record ConversationTurn(String role, String content) {
        public ConversationTurn {
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(content, "content");
        }
    }
}
