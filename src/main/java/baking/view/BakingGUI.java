package baking.view;

import baking.controller.AgentController;
import baking.model.BakingRequest;
import baking.model.Ingredient;
import baking.model.Recipe;
import baking.model.ShoppingList;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Swing interface that accesses baking services only through AgentController. */
public final class BakingGUI {
    private final AgentController controller;
    private final JFrame frame = new JFrame("Baking Agent");
    private final JTextField requestField = new JTextField();
    private final JButton submitButton = new JButton("Submit");
    private final JTextArea resultsArea = new JTextArea();
    private final JLabel statusLine = new JLabel("Ready");
    private final DefaultListModel<Recipe> recipeModel = new DefaultListModel<>();
    private final JList<Recipe> recipeList = new JList<>(recipeModel);
    private final JTextField ingredientsField = new JTextField();
    private final JTextField servingsField = new JTextField();
    private final JTextField specialistField = new JTextField();
    private final List<Ingredient> availableIngredients = new ArrayList<>();
    private Recipe selectedRecipe;

    public BakingGUI(AgentController controller) {
        this.controller = Objects.requireNonNull(controller, "controller");
        configureFrame();
    }

    private void configureFrame() {
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setMinimumSize(new Dimension(850, 600));
        frame.setSize(1050, 720);
        frame.setLocationByPlatform(true);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel requestPanel = new JPanel(new BorderLayout(6, 6));
        requestPanel.add(requestField, BorderLayout.CENTER);
        requestPanel.add(submitButton, BorderLayout.EAST);
        root.add(requestPanel, BorderLayout.NORTH);

        resultsArea.setEditable(false);
        resultsArea.setLineWrap(true);
        resultsArea.setWrapStyleWord(true);
        JScrollPane resultsScroll = new JScrollPane(resultsArea);
        resultsScroll.setBorder(BorderFactory.createTitledBorder("Results"));
        root.add(resultsScroll, BorderLayout.CENTER);

        recipeList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        recipeList.setCellRenderer(new DefaultListCellRenderer() {
            @Override public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                    int index, boolean selected, boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof Recipe recipe) label.setText("[" + recipe.getRecipeID() + "] " + recipe.getName());
                return label;
            }
        });
        JPanel recipePanel = new JPanel(new BorderLayout(5, 5));
        recipePanel.setBorder(BorderFactory.createTitledBorder("Recipes"));
        recipePanel.add(new JScrollPane(recipeList), BorderLayout.CENTER);
        JButton selectButton = new JButton("Select");
        selectButton.addActionListener(event -> selectRecipe());
        recipePanel.add(selectButton, BorderLayout.SOUTH);
        recipePanel.setPreferredSize(new Dimension(300, 300));
        root.add(recipePanel, BorderLayout.EAST);

        JPanel lower = new JPanel(new BorderLayout(6, 6));
        lower.add(buildControls(), BorderLayout.CENTER);
        lower.add(statusLine, BorderLayout.SOUTH);
        root.add(lower, BorderLayout.SOUTH);
        frame.setContentPane(root);
        submitButton.addActionListener(event -> submitRequest());
        requestField.addActionListener(event -> submitRequest());
        refreshRecipeList(controller.getRecipes());
    }

    private JPanel buildControls() {
        JPanel controls = new JPanel(new GridLayout(0, 1, 4, 4));
        JPanel ingredients = new JPanel(new BorderLayout(5, 5));
        ingredients.add(new JLabel("Available Ingredients (Name:quantity unit, ...):"), BorderLayout.WEST);
        ingredients.add(ingredientsField, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        JButton check = new JButton("Check Availability");
        check.addActionListener(event -> runAction(this::checkAvailability));
        servingsField.setColumns(6);
        servingsField.setToolTipText("Servings");
        JButton scale = new JButton("Scale");
        scale.addActionListener(event -> runAction(this::scaleRecipe));
        JButton shopping = new JButton("Generate Shopping List");
        shopping.addActionListener(event -> runAction(this::generateShoppingList));
        JButton favourite = new JButton("Favourite");
        favourite.addActionListener(event -> runAction(this::favouriteRecipe));
        JButton favourites = new JButton("View Favourites");
        favourites.addActionListener(event -> runAction(this::viewFavourites));
        JButton addIngredients = new JButton("Add / Update Ingredients");
        addIngredients.addActionListener(event -> runAction(this::updateIngredients));
        specialistField.setColumns(14);
        specialistField.setToolTipText("Ingredient, taste preference, or baking problem");
        JButton substitute = new JButton("Find Substitute");
        substitute.addActionListener(event -> runAction(this::findSubstitute));
        JButton taste = new JButton("Customize Taste");
        taste.addActionListener(event -> runAction(this::customizeTaste));
        JButton troubleshoot = new JButton("Troubleshoot");
        troubleshoot.addActionListener(event -> runAction(this::troubleshoot));
        actions.add(addIngredients);
        actions.add(check);
        actions.add(new JLabel("Servings:"));
        actions.add(servingsField);
        actions.add(scale);
        actions.add(shopping);
        actions.add(favourite);
        actions.add(favourites);
        controls.add(ingredients);
        controls.add(actions);
        JPanel specialistActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        specialistActions.add(new JLabel("Specialist request:"));
        specialistActions.add(specialistField);
        specialistActions.add(substitute);
        specialistActions.add(taste);
        specialistActions.add(troubleshoot);
        controls.add(specialistActions);
        return controls;
    }

    private void updateIngredients() {
        List<Ingredient> additions = parseIngredientInput(ingredientsField.getText());
        if (additions.isEmpty()) throw new IllegalArgumentException("Enter ingredients in Name:quantity unit format.");
        List<Ingredient> merged = controller.mergeIngredients(availableIngredients, additions);
        availableIngredients.clear();
        availableIngredients.addAll(merged);
        resultsArea.setText("Available ingredients: " + formatIngredients(availableIngredients));
    }

    private void checkAvailability() {
        Recipe recipe = requireSelectedRecipe();
        List<Ingredient> missing = controller.checkIngredients(recipe, availableIngredients);
        resultsArea.setText(missing.isEmpty() ? "You have all ingredients for " + recipe.getName() + "."
                : "Missing: " + formatIngredients(missing));
    }

    private void scaleRecipe() {
        int servings = positiveInteger(servingsField.getText(), "serving count");
        selectedRecipe = controller.scaleRecipe(requireSelectedRecipe(), servings);
        resultsArea.setText("Scaled " + selectedRecipe.getName() + " to " + servings + " servings: "
                + formatIngredients(selectedRecipe.getIngredients()));
    }

    private void generateShoppingList() {
        ShoppingList list = controller.generateShoppingList(requireSelectedRecipe(), availableIngredients);
        resultsArea.setText(list.getItems().isEmpty() ? "Your shopping list is empty."
                : "Shopping list: " + formatIngredients(list.getItems()));
    }

    private void favouriteRecipe() {
        Recipe recipe = requireSelectedRecipe();
        resultsArea.setText(controller.addFavourite(recipe) ? "Added " + recipe.getName() + " to favourites."
                : recipe.getName() + " is already in your favourites.");
    }

    private void viewFavourites() {
        List<Recipe> favourites = controller.getFavourites();
        resultsArea.setText(favourites.isEmpty() ? "You have no favourite recipes."
                : favourites.stream().map(recipe -> "[" + recipe.getRecipeID() + "] " + recipe.getName())
                        .reduce((a, b) -> a + "\n" + b).orElse(""));
    }

    private void findSubstitute() {
        String ingredient = specialistField.getText().trim();
        if (ingredient.isEmpty()) throw new IllegalArgumentException("Enter an ingredient to replace.");
        Recipe recipe = selectedRecipe;
        String context = recipe == null ? "" : " in " + recipe.getName();
        processAgentRequest("What can I use instead of " + ingredient + context + "?");
    }

    private void customizeTaste() {
        String preference = specialistField.getText().trim();
        if (preference.isEmpty()) throw new IllegalArgumentException("Enter a taste preference, such as less sweet.");
        Recipe recipe = requireSelectedRecipe();
        processAgentRequest("Customize the taste of " + recipe.getName() + " to be " + preference + ".");
    }

    private void troubleshoot() {
        String problem = specialistField.getText().trim();
        if (problem.isEmpty()) throw new IllegalArgumentException("Describe the baking problem to troubleshoot.");
        Recipe recipe = selectedRecipe;
        processAgentRequest(recipe == null ? "Troubleshoot this baking problem: " + problem
                : "Troubleshoot this problem with " + recipe.getName() + ": " + problem);
    }

    private void runAction(Runnable action) {
        try { action.run(); }
        catch (RuntimeException error) { showError(Objects.toString(error.getMessage(), "Invalid input.")); }
    }

    private Recipe requireSelectedRecipe() {
        if (selectedRecipe == null) throw new IllegalArgumentException("Select a recipe from the list first.");
        return selectedRecipe;
    }

    private static int positiveInteger(String text, String label) {
        try {
            int value = Integer.parseInt(text.trim());
            if (value > 0) return value;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException("Enter a positive whole number for the " + label + ".");
    }

    static List<Ingredient> parseIngredientInput(String text) {
        if (text == null || text.isBlank()) return List.of();
        List<Ingredient> ingredients = new ArrayList<>();
        for (String item : text.split(",")) {
            String[] named = item.trim().split(":", 2);
            if (named.length != 2 || named[0].isBlank()) {
                throw new IllegalArgumentException("Use Name:quantity unit format, for example Flour:2 cups.");
            }
            String[] amount = named[1].trim().split("\\s+", 2);
            try {
                double quantity = Double.parseDouble(amount[0]);
                if (!Double.isFinite(quantity) || quantity < 0) throw new NumberFormatException();
                String unit = amount.length == 1 ? "count" : normalizeUnit(amount[1]);
                ingredients.add(new Ingredient(named[0].trim(), quantity, unit));
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("Ingredient quantities must be non-negative numbers.");
            }
        }
        return List.copyOf(ingredients);
    }

    private static String normalizeUnit(String raw) {
        String normalized = switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "cups" -> "cup";
            case "tablespoon", "tablespoons" -> "tbsp";
            case "teaspoon", "teaspoons" -> "tsp";
            case "grams" -> "g";
            case "kilogram", "kilograms" -> "kg";
            case "ounce", "ounces" -> "oz";
            case "pound", "pounds" -> "lb";
            case "milliliter", "milliliters" -> "ml";
            case "liter", "liters" -> "l";
            case "pinches" -> "pinch";
            default -> raw.trim().toLowerCase(java.util.Locale.ROOT);
        };
        if (!List.of("cup", "tbsp", "tsp", "g", "kg", "oz", "lb", "ml", "l", "pinch").contains(normalized)) {
            throw new IllegalArgumentException("Unknown unit '" + raw + "'. Valid units: cup, tbsp, tsp, g, kg, oz, lb, ml, l, pinch.");
        }
        return normalized;
    }

    private static String formatIngredients(List<Ingredient> ingredients) {
        return ingredients.stream().map(ingredient -> ingredient.getName() + " "
                + BigDecimal.valueOf(ingredient.getQuantity()).stripTrailingZeros().toPlainString()
                + (ingredient.getUnit().equalsIgnoreCase("count") ? "" : " " + ingredient.getUnit()))
                .reduce((left, right) -> left + "; " + right).orElse("");
    }

    public void start() {
        frame.setVisible(true);
        requestField.requestFocusInWindow();
    }

    private void submitRequest() {
        processAgentRequest(requestField.getText().trim());
    }

    private void processAgentRequest(String requestText) {
        if (requestText.isEmpty()) { showError("Enter a baking request first."); return; }
        submitButton.setEnabled(false);
        statusLine.setText("Thinking...");
        List<Ingredient> ingredientsSnapshot = List.copyOf(availableIngredients);
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() {
                return controller.processRequest(new BakingRequest(requestText, 0, 0,
                        ingredientsSnapshot, List.of(), ""));
            }
            @Override protected void done() {
                submitButton.setEnabled(true);
                try {
                    resultsArea.setText(get());
                    refreshRecipeList(controller.getLastSearchResults());
                    statusLine.setText("Ready");
                } catch (Exception error) {
                    showError(messageFor(error));
                    statusLine.setText("Ready");
                }
            }
        }.execute();
    }

    private void refreshRecipeList(List<Recipe> recipes) {
        recipeModel.clear();
        recipes.forEach(recipeModel::addElement);
    }

    private void selectRecipe() {
        selectedRecipe = recipeList.getSelectedValue();
        if (selectedRecipe == null) { showError("Select a recipe from the list first."); return; }
        resultsArea.setText("Selected [" + selectedRecipe.getRecipeID() + "] " + selectedRecipe.getName()
                + " - " + selectedRecipe.getDescription() + " (" + selectedRecipe.getTotalTime()
                + " minutes; serves " + selectedRecipe.getServings() + ")");
    }

    private void showError(String message) { resultsArea.setText("Error: " + message); }
    private static String messageFor(Exception error) {
        Throwable cause = error.getCause();
        return Objects.toString(cause == null ? error.getMessage() : cause.getMessage(), "Unable to complete the request.");
    }
}
