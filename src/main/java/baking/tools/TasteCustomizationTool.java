package baking.tools;

import baking.llm.LLMClient;
import baking.llm.LLMException;
import baking.model.Ingredient;
import baking.model.Recipe;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Applies a requested taste change by asking the LLM for revised ingredient quantities. */
public class TasteCustomizationTool implements AgentTool {
    private final LLMClient llmClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private String lastExplanation = "";

    public TasteCustomizationTool(LLMClient llmClient) {
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
    }

    @Override
    public String execute(String input) {
        if (input == null || input.isBlank()) throw new IllegalArgumentException("taste request must not be blank");
        return "Taste customization: " + llmClient.generateResponse(
                "Suggest specific changes to a baking recipe to match this taste preference. "
                        + "Explain each change and its effect: " + input, "taste customization advice");
    }

    /** Returns a new recipe with model-proposed ingredient quantities; the supplied recipe is unchanged. */
    public Recipe customizeRecipe(Recipe recipe, String preference) {
        Objects.requireNonNull(recipe, "recipe");
        if (preference == null || preference.isBlank()) throw new IllegalArgumentException("preference must not be blank");
        String ingredientJson;
        try {
            ingredientJson = mapper.writeValueAsString(recipe.getIngredients().stream()
                    .map(item -> new IngredientData(item.getName(), item.getQuantity(), item.getUnit())).toList());
        } catch (Exception e) {
            throw new LLMException("Could not prepare recipe ingredients for taste customization.", e);
        }
        String response = llmClient.generateResponse("Customize the ingredient amounts for " + recipe.getName()
                + " to match this preference: " + preference + ". Current ingredients as JSON: " + ingredientJson
                + ". Return only JSON with an 'ingredients' array of {name, quantity, unit} and an 'explanation' string.",
                "taste customization");
        try {
            JsonNode root = mapper.readTree(response);
            JsonNode items = root.path("ingredients");
            if (!items.isArray()) throw new IllegalArgumentException("ingredients must be a JSON array");
            List<Ingredient> customized = new ArrayList<>();
            for (JsonNode item : items) {
                JsonNode name = item.path("name");
                JsonNode quantity = item.path("quantity");
                JsonNode unit = item.path("unit");
                if (!name.isTextual() || !quantity.isNumber() || !unit.isTextual()) {
                    throw new IllegalArgumentException("each ingredient needs name, numeric quantity, and unit");
                }
                customized.add(new Ingredient(name.asText(), quantity.doubleValue(), unit.asText()));
            }
            if (customized.isEmpty()) throw new IllegalArgumentException("ingredient list must not be empty");
            JsonNode explanation = root.path("explanation");
            lastExplanation = explanation.isTextual() ? explanation.asText() : "Recipe quantities were adjusted for " + preference + ".";
            return new Recipe(recipe.getRecipeID(), recipe.getName(), recipe.getDescription(), recipe.getServings(),
                    recipe.getPrepTime(), recipe.getBakeTime(), customized, recipe.getInstructions(), recipe.getDietaryTags());
        } catch (Exception e) {
            if (e instanceof LLMException llmException) throw llmException;
            throw new LLMException("The model did not return valid customized recipe JSON.", e);
        }
    }

    public String explainChanges(Recipe recipe) {
        Objects.requireNonNull(recipe, "recipe");
        return lastExplanation.isBlank() ? "No customization has been applied yet." : lastExplanation;
    }

    @Override
    public String getName() { return "TasteCustomizationTool"; }

    private record IngredientData(String name, double quantity, String unit) { }
}
