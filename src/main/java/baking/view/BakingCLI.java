package baking.view;

import baking.controller.AgentController;
import baking.model.BakingRequest;
import baking.model.Ingredient;
import baking.model.Recipe;
import baking.model.ShoppingList;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;

/** Text interface that sends user requests through the controller facade. */
public final class BakingCLI {
    private final AgentController controller;
    private final Scanner scanner;
    private final PrintStream output;
    private final PrintStream errors;
    private boolean awaitingClarification;
    private boolean running = true;
    private Recipe selectedRecipe;
    private List<Ingredient> availableIngredients = List.of();

    public BakingCLI(AgentController controller) {
        this(controller, System.in, System.out, System.err);
    }

    public BakingCLI(AgentController controller, InputStream input, PrintStream output, PrintStream errors) {
        this.controller = Objects.requireNonNull(controller, "controller");
        this.scanner = new Scanner(Objects.requireNonNull(input, "input"));
        this.output = Objects.requireNonNull(output, "output");
        this.errors = Objects.requireNonNull(errors, "errors");
    }

    public void start() {
        output.println("Baking Agent CLI. Type /help for commands; /quit to exit.");
        while (running) {
            output.print(awaitingClarification ? "Answer> " : "You> ");
            output.flush();
            String input = getUserInput();
            if (input == null) { running = false; break; }
            submitCommand(input);
        }
    }

    public String getUserInput() { return scanner.hasNextLine() ? scanner.nextLine() : null; }

    public void submitCommand(String command) {
        if (command == null || command.isBlank()) {
            displayError("Please enter a baking request or command.");
            return;
        }
        String text = command.trim();
        if (text.startsWith("/") || text.equalsIgnoreCase("quit") || text.equalsIgnoreCase("exit")) {
            handleCommand(text.startsWith("/") ? text : "/quit");
            return;
        }
        if (awaitingClarification) {
            awaitingClarification = false;
            submitAgentRequest(text);
            return;
        }
        submitAgentRequest(text);
    }

    private void submitAgentRequest(String text) {
        try {
            String response = controller.processRequest(new BakingRequest(text, 0, 0,
                    List.of(), List.of(), ""));
            displayResult(response);
            awaitingClarification = response != null && response.toLowerCase().contains("would you like");
        } catch (RuntimeException e) {
            awaitingClarification = false;
            displayError(e.getMessage() == null ? "Unable to process that request." : e.getMessage());
        }
    }

    private void handleCommand(String command) {
        String[] parts = command.split("\\s+", 2);
        String name = parts[0].toLowerCase();
        String argument = parts.length == 2 ? parts[1].trim() : "";
        try {
            switch (name) {
                case "/help" -> displayResult("Commands: /ingredients [Name:quantity unit; ...], /select <recipe id>, "
                        + "/check, /scale <servings>, /shopping, /favourite [remove], /favourites, /help, /quit. "
                        + "/cancel. Plain text is sent to the baking agent.");
                case "/quit" -> { awaitingClarification = false; running = false; }
                case "/cancel" -> {
                    controller.cancelClarification();
                    awaitingClarification = false;
                    displayResult("Clarification cancelled.");
                }
                case "/ingredients" -> ingredientsCommand(argument);
                case "/select" -> selectCommand(argument);
                case "/check" -> checkCommand(argument);
                case "/scale" -> scaleCommand(argument);
                case "/shopping" -> shoppingCommand();
                case "/favourite" -> favouriteCommand(argument);
                case "/favourites" -> favouritesCommand();
                default -> displayError("Unknown command. Type /help to see available commands.");
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            displayError(e.getMessage() == null ? "Invalid command input." : e.getMessage());
        }
    }

    private void ingredientsCommand(String argument) {
        if (!argument.isEmpty()) availableIngredients = parseIngredients(argument);
        if (availableIngredients.isEmpty()) displayResult("Your ingredient list is empty.");
        else displayResult("Ingredients on hand: " + formatIngredients(availableIngredients));
    }

    private void selectCommand(String argument) {
        int id = positiveInteger(argument, "recipe id");
        Optional<Recipe> recipe = controller.findRecipe(id);
        if (recipe.isEmpty()) throw new IllegalArgumentException("No recipe exists with id " + id + ".");
        selectedRecipe = recipe.get();
        displayResult("Selected: " + selectedRecipe.getName() + " (" + selectedRecipe.getServings() + " servings)");
    }

    private void checkCommand(String argument) {
        if (!argument.isEmpty()) selectCommand(argument);
        Recipe recipe = requireSelectedRecipe();
        List<Ingredient> missing = controller.checkIngredients(recipe, availableIngredients);
        displayResult(missing.isEmpty() ? "You have all ingredients for " + recipe.getName() + "."
                : "Missing: " + formatIngredients(missing));
    }

    private void scaleCommand(String argument) {
        int servings = positiveInteger(argument, "serving count");
        selectedRecipe = controller.scaleRecipe(requireSelectedRecipe(), servings);
        displayResult("Scaled " + selectedRecipe.getName() + " to " + servings + " servings: "
                + formatIngredients(selectedRecipe.getIngredients()));
    }

    private void shoppingCommand() {
        ShoppingList list = controller.generateShoppingList(requireSelectedRecipe(), availableIngredients);
        displayResult(list.getItems().isEmpty() ? "Your shopping list is empty."
                : "Shopping list: " + formatIngredients(list.getItems()));
    }

    private void favouriteCommand(String argument) {
        Recipe recipe = requireSelectedRecipe();
        if (argument.equalsIgnoreCase("remove")) {
            controller.removeFavourite(recipe);
            displayResult("Removed " + recipe.getName() + " from favourites (if it was saved).");
        } else if (argument.isEmpty()) {
            controller.addFavourite(recipe);
            displayResult("Added " + recipe.getName() + " to favourites.");
        } else throw new IllegalArgumentException("Usage: /favourite [remove]");
    }

    private void favouritesCommand() {
        List<Recipe> favourites = controller.getFavourites();
        displayResult(favourites.isEmpty() ? "You have no favourite recipes."
                : "Favourites: " + favourites.stream().map(r -> r.getRecipeID() + ": " + r.getName())
                        .reduce((a, b) -> a + ", " + b).orElse(""));
    }

    private Recipe requireSelectedRecipe() {
        if (selectedRecipe == null) throw new IllegalArgumentException("Select a recipe first with /select <recipe id>.");
        return selectedRecipe;
    }

    private static int positiveInteger(String value, String label) {
        try {
            int number = Integer.parseInt(value);
            if (number > 0) return number;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException("Enter a positive whole number for the " + label + ".");
    }

    private static List<Ingredient> parseIngredients(String value) {
        return java.util.Arrays.stream(value.split(";"))
                .map(String::trim).filter(s -> !s.isEmpty()).map(BakingCLI::parseIngredient).toList();
    }

    private static Ingredient parseIngredient(String text) {
        int colon = text.indexOf(':');
        if (colon < 1) throw new IllegalArgumentException("Use ingredient format Name:quantity unit; for example Flour:2 cups.");
        String name = text.substring(0, colon).trim();
        String[] quantityAndUnit = text.substring(colon + 1).trim().split("\\s+", 2);
        if (quantityAndUnit.length != 2) throw new IllegalArgumentException("Each ingredient needs a quantity and unit.");
        try {
            double quantity = Double.parseDouble(quantityAndUnit[0]);
            if (!Double.isFinite(quantity) || quantity < 0) throw new NumberFormatException();
            return new Ingredient(name, quantity, quantityAndUnit[1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Ingredient quantities must be non-negative numbers.");
        }
    }

    private static String formatIngredients(List<Ingredient> ingredients) {
        return ingredients.stream().map(i -> i.getName() + " " + i.getQuantity() + " " + i.getUnit())
                .reduce((a, b) -> a + "; " + b).orElse("");
    }

    public void displayResult(String result) { output.println(Objects.requireNonNullElse(result, "")); }
    public void displayError(String error) { errors.println("Error: " + Objects.requireNonNullElse(error, "Unknown error.")); }
}
