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
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Swing interface that accesses baking services only through AgentController. */
public final class BakingGUI {
    private static final int GAP = 10;
    private final AgentController controller;
    private final JFrame frame = new JFrame("Baking Agent");
    private final JLabel currentRecipeBanner = new JLabel(
            "No recipe selected. Find one in the Find Recipes tab.");
    private final JTabbedPane tabs = new JTabbedPane();
    private final JLabel statusLine = new JLabel("Ready");

    private final JTextField requestField = new JTextField();
    private final JButton askButton = new JButton("Ask");
    private final JTextArea findResults = resultArea();
    private final DefaultListModel<Recipe> recipeModel = new DefaultListModel<>();
    private final JList<Recipe> recipeList = recipeList(recipeModel);

    private final JPanel recipeToolsPanel = new JPanel();
    private final JLabel servingsError = new JLabel(" ");
    private final JLabel ingredientsError = new JLabel(" ");
    private final JTextField servingsField = new JTextField();
    private final JTextField ingredientsField = new JTextField();
    private final JTextArea recipeToolsResults = resultArea();
    private final List<Ingredient> availableIngredients = new ArrayList<>();
    private final JButton scaleButton = new JButton("Scale");
    private final JButton checkButton = new JButton("Check what's missing");
    private final JButton shoppingButton = new JButton("Make shopping list");
    private final JButton saveFavouriteButton = new JButton("Save to favourites");

    private Recipe selectedRecipe;

    public BakingGUI(AgentController controller) {
        this.controller = Objects.requireNonNull(controller, "controller");
        buildWindow();
    }

    private void buildWindow() {
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setMinimumSize(new Dimension(820, 620));
        frame.setSize(980, 720);
        frame.setLocationByPlatform(true);

        JPanel root = new JPanel(new BorderLayout(GAP, GAP));
        root.setBorder(BorderFactory.createEmptyBorder(GAP, GAP, GAP, GAP));
        currentRecipeBanner.setFont(currentRecipeBanner.getFont().deriveFont(Font.BOLD, 17f));
        currentRecipeBanner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEtchedBorder(), BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        root.add(currentRecipeBanner, BorderLayout.NORTH);

        tabs.addTab("Find Recipes", buildFindRecipesTab());
        tabs.addTab("Recipe Tools", buildRecipeToolsTab());
        root.add(tabs, BorderLayout.CENTER);
        statusLine.setBorder(BorderFactory.createEmptyBorder(4, 2, 0, 2));
        root.add(statusLine, BorderLayout.SOUTH);
        frame.setContentPane(root);

        askButton.addActionListener(event -> askAgent());
        requestField.addActionListener(event -> askAgent());
        recipeList.addListSelectionListener(event -> { /* Highlighting never changes the active recipe. */ });
        recipeList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2) useHighlightedRecipe();
            }
        });
        refreshRecipeList(controller.getRecipes());
        setRecipeToolsEnabled(false);
        applyReadableFonts(frame.getContentPane());
    }

    private JPanel buildFindRecipesTab() {
        JPanel panel = tabPanel();
        panel.add(instruction("Describe what you want to bake"), BorderLayout.NORTH);

        JPanel requestRow = new JPanel(new BorderLayout(8, 0));
        requestField.setText("chocolate chip cookies for 12 people");
        requestRow.add(requestField, BorderLayout.CENTER);
        requestRow.add(askButton, BorderLayout.EAST);

        findResults.setRows(5);
        JScrollPane resultScroll = new JScrollPane(findResults);
        resultScroll.setBorder(BorderFactory.createTitledBorder("Agent response"));

        JPanel listArea = new JPanel(new BorderLayout(6, 6));
        listArea.add(new JLabel("Recipes (select a row, then use the button):"), BorderLayout.NORTH);
        listArea.add(new JScrollPane(recipeList), BorderLayout.CENTER);
        JButton useRecipe = new JButton("Use this recipe");
        useRecipe.addActionListener(event -> useHighlightedRecipe());
        listArea.add(useRecipe, BorderLayout.SOUTH);

        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.add(requestRow, BorderLayout.NORTH);
        center.add(resultScroll, BorderLayout.CENTER);
        center.add(listArea, BorderLayout.SOUTH);
        panel.add(center, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildRecipeToolsTab() {
        recipeToolsPanel.setLayout(new BorderLayout(8, 8));
        recipeToolsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        recipeToolsPanel.add(instruction("Choose a recipe in Find Recipes, then use these tools."), BorderLayout.NORTH);

        JPanel groups = new JPanel(new GridLayout(2, 2, 8, 8));
        groups.add(buildScaleGroup());
        groups.add(buildIngredientsGroup());
        groups.add(buildShoppingGroup());
        groups.add(buildSaveFavouriteGroup());
        recipeToolsResults.setRows(7);
        JScrollPane output = new JScrollPane(recipeToolsResults);
        output.setBorder(BorderFactory.createTitledBorder("Results"));

        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.add(groups, BorderLayout.NORTH);
        center.add(output, BorderLayout.CENTER);
        recipeToolsPanel.add(center, BorderLayout.CENTER);
        return recipeToolsPanel;
    }

    private JPanel buildScaleGroup() {
        JPanel group = titledGroup("Scale recipe");
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        row.add(new JLabel("Servings"));
        servingsField.setColumns(7);
        row.add(servingsField);
        scaleButton.addActionListener(event -> scaleRecipe());
        row.add(scaleButton);
        group.add(row, BorderLayout.CENTER);
        servingsError.setForeground(new java.awt.Color(170, 35, 35));
        group.add(servingsError, BorderLayout.SOUTH);
        return group;
    }

    private JPanel buildIngredientsGroup() {
        JPanel group = titledGroup("Ingredients");
        group.setLayout(new BorderLayout(5, 5));
        JPanel input = new JPanel(new BorderLayout(5, 5));
        input.add(new JLabel("What you have (e.g. flour:2 cups, egg:3)"), BorderLayout.NORTH);
        input.add(ingredientsField, BorderLayout.CENTER);
        checkButton.addActionListener(event -> checkWhat'sMissing());
        input.add(checkButton, BorderLayout.EAST);
        group.add(input, BorderLayout.CENTER);
        ingredientsError.setForeground(new java.awt.Color(170, 35, 35));
        group.add(ingredientsError, BorderLayout.SOUTH);
        return group;
    }

    private JPanel buildShoppingGroup() {
        JPanel group = titledGroup("Shopping list");
        shoppingButton.addActionListener(event -> makeShoppingList());
        group.add(shoppingButton, BorderLayout.CENTER);
        return group;
    }

    private JPanel buildSaveFavouriteGroup() {
        JPanel group = titledGroup("Favourites");
        saveFavouriteButton.addActionListener(event -> saveFavourite());
        group.add(saveFavouriteButton, BorderLayout.CENTER);
        return group;
    }

    private void askAgent() {
        String request = requestField.getText().trim();
        if (request.isEmpty()) {
            findResults.setText("Please describe what you want to bake.");
            requestField.requestFocusInWindow();
            return;
        }
        askButton.setEnabled(false);
        statusLine.setText("Thinking...");
        List<Ingredient> pantry = List.copyOf(availableIngredients);
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() {
                return controller.processRequest(new BakingRequest(request, 0, 0, pantry, List.of(), ""));
            }
            @Override protected void done() {
                askButton.setEnabled(true);
                try {
                    findResults.setText(get());
                    List<Recipe> results = controller.getLastSearchResults();
                    refreshRecipeList(results.isEmpty() ? controller.getRecipes() : results);
                    statusLine.setText("Ready");
                } catch (Exception error) {
                    findResults.setText("Unable to complete the request: " + messageFor(error));
                    statusLine.setText("Ready");
                }
            }
        }.execute();
    }

    private void useHighlightedRecipe() {
        Recipe recipe = recipeList.getSelectedValue();
        if (recipe == null) {
            findResults.setText("Select a recipe from the list first.");
            return;
        }
        selectedRecipe = recipe;
        currentRecipeBanner.setText("Current recipe: " + selectedRecipe.getName());
        statusLine.setText("Now working on: " + selectedRecipe.getName());
        recipeToolsResults.setText("For " + selectedRecipe.getName() + ":");
        setRecipeToolsEnabled(true);
    }

    private void scaleRecipe() {
        servingsError.setText(" ");
        int servings;
        try { servings = positiveInteger(servingsField.getText()); }
        catch (IllegalArgumentException error) { servingsError.setText(error.getMessage()); return; }
        selectedRecipe = controller.scaleRecipe(requireSelectedRecipe(), servings);
        showRecipeResult("Scaled to " + servings + " servings: " + formatIngredients(selectedRecipe.getIngredients()));
    }

    private void checkWhat'sMissing() {
        ingredientsError.setText(" ");
        if (!ingredientsField.getText().isBlank()) {
            try {
                List<Ingredient> additions = parseIngredientInput(ingredientsField.getText());
                List<Ingredient> merged = controller.mergeIngredients(availableIngredients, additions);
                availableIngredients.clear();
                availableIngredients.addAll(merged);
            } catch (IllegalArgumentException error) {
                ingredientsError.setText(error.getMessage());
                return;
            }
        }
        List<Ingredient> missing = controller.checkIngredients(requireSelectedRecipe(), availableIngredients);
        showRecipeResult(missing.isEmpty() ? "You have all required ingredients."
                : "Missing: " + formatIngredients(missing));
    }

    private void makeShoppingList() {
        ShoppingList list = controller.generateShoppingList(requireSelectedRecipe(), availableIngredients);
        showRecipeResult(list.getItems().isEmpty() ? "Your shopping list is empty."
                : "Shopping list: " + formatIngredients(list.getItems()));
    }

    private void showRecipeResult(String result) {
        recipeToolsResults.setText("For " + requireSelectedRecipe().getName() + ":\n" + result);
    }

    private void setRecipeToolsEnabled(boolean enabled) {
        setEnabledRecursively(recipeToolsPanel, enabled);
        // Keep the explanatory text and result area available for an empty-state message.
        recipeToolsResults.setEnabled(true);
    }

    private static void setEnabledRecursively(Component component, boolean enabled) {
        component.setEnabled(enabled);
        if (component instanceof java.awt.Container container) {
            for (Component child : container.getComponents()) setEnabledRecursively(child, enabled);
        }
    }

    private Recipe requireSelectedRecipe() {
        if (selectedRecipe == null) throw new IllegalStateException("Choose a recipe in Find Recipes first.");
        return selectedRecipe;
    }

    private void refreshRecipeList(List<Recipe> recipes) {
        recipeModel.clear();
        recipes.forEach(recipeModel::addElement);
    }

    private static JPanel tabPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        return panel;
    }

    private static JLabel instruction(String text) {
        JLabel label = new JLabel(text);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        return label;
    }

    private static JPanel titledGroup(String title) {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder(title),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)));
        return panel;
    }

    private static JTextArea resultArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return area;
    }

    private static JList<Recipe> recipeList(DefaultListModel<Recipe> model) {
        JList<Recipe> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> owner, Object value, int index,
                    boolean selected, boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(owner, value, index, selected, focus);
                if (value instanceof Recipe recipe) label.setText("[" + recipe.getRecipeID() + "] " + recipe.getName());
                return label;
            }
        });
        return list;
    }

    private static void applyReadableFonts(Component component) {
        Font current = component.getFont();
        if (current != null) component.setFont(current.deriveFont(15f));
        if (component instanceof java.awt.Container container) {
            for (Component child : container.getComponents()) applyReadableFonts(child);
        }
    }

    private static int positiveInteger(String text) {
        try {
            int value = Integer.parseInt(text.trim());
            if (value > 0) return value;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException("Enter a positive whole number.");
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

    private void showError(String message) { findResults.setText("Error: " + message); }
    private static String messageFor(Exception error) {
        Throwable cause = error.getCause();
        return Objects.toString(cause == null ? error.getMessage() : cause.getMessage(), "Unable to complete the request.");
    }

    public void start() { frame.setVisible(true); requestField.requestFocusInWindow(); }
}
