package baking.tools;

import baking.llm.LLMClient;
import baking.model.Ingredient;
import baking.model.Recipe;
import baking.service.IngredientManager;

import java.util.List;
import java.util.Objects;

/** Suggests ingredient alternatives with the recipe and pantry context. */
public class SubstitutionTool implements AgentTool {
    private final LLMClient llmClient;
    private final IngredientManager ingredientManager;

    public SubstitutionTool(LLMClient llmClient, IngredientManager ingredientManager) {
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
        this.ingredientManager = Objects.requireNonNull(ingredientManager, "ingredientManager");
    }

    @Override
    public String execute(String input) {
        if (input == null || input.isBlank()) throw new IllegalArgumentException("substitution request must not be blank");
        return "Substitution suggestion: " + llmClient.generateResponse(
                "Suggest a practical baking ingredient substitution for this request. Explain the amount and "
                        + "likely effect on texture or flavor. Request: " + input);
    }

    public String suggestSubstitute(Ingredient ingredient, Recipe recipe) {
        Objects.requireNonNull(ingredient, "ingredient");
        Objects.requireNonNull(recipe, "recipe");
        List<Ingredient> missing = ingredientManager.findMissingIngredients(recipe);
        String missingSummary = missing.isEmpty() ? "none" : String.join(", ", missing.stream()
                .map(item -> item.getName() + " (short by " + item.getQuantity() + " " + item.getUnit() + ")").toList());
        return llmClient.generateResponse("Suggest a substitute for " + ingredient.getQuantity() + " "
                + ingredient.getUnit() + " " + ingredient.getName() + " in recipe " + recipe.getName()
                + ". Missing pantry ingredients: " + missingSummary
                + ". Give a practical replacement amount and explain the effect on the bake.");
    }

    public String explainEffect(String substitution) {
        if (substitution == null || substitution.isBlank()) throw new IllegalArgumentException("substitution must not be blank");
        return llmClient.generateResponse("Briefly explain how this baking substitution may affect texture, flavor, "
                + "and baking time: " + substitution);
    }

    @Override
    public String getName() { return "SubstitutionTool"; }
}
