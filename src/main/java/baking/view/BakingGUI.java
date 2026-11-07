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
import javax.swing.JComboBox;
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
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
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
    private final JTextField ingredientNameField = new JTextField();
    private final JTextField ingredientAmountField = new JTextField();
    private final JComboBox<String> ingredientUnit = new JComboBox<>(new String[]{
            "cup", "tbsp", "tsp", "g", "kg", "oz", "lb", "ml", "l", "pinch", "none (count)"});
    private final DefaultListModel<Ingredient> pantryModel = new DefaultListModel<>();
    private final JList<Ingredient> pantryList = ingredientList(pantryModel);
    private final JTextArea recipeToolsResults = resultArea();
    private final List<Ingredient> availableIngredients = new ArrayList<>();
    private final JButton scaleButton = new JButton("Scale");
    private final JButton checkButton = new JButton("Check what's missing");
    private final JButton shoppingButton = new JButton("Make shopping list");
    private final JButton saveFavouriteButton = new JButton("Save to favourites");

    private final DefaultListModel<Recipe> favouritesModel = new DefaultListModel<>();
    private final JList<Recipe> favouritesList = recipeList(favouritesModel);
    private final JLabel emptyFavouritesMessage = new JLabel("No saved favourites yet.");
    private final JPanel aiHelpPanel = new JPanel(new BorderLayout(8, 8));
    private final JComboBox<Ingredient> substituteIngredient = new JComboBox<>();
    private final JTextField tasteField = new JTextField();
    private final JTextArea problemField = new JTextArea(4, 30);
    private final JTextField clarificationField = new JTextField();
    private final JTextArea aiResults = resultArea();
    private final JLabel aiInputError = new JLabel(" ");

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
        currentRecipeBanner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEtchedBorder(), BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        root.add(currentRecipeBanner, BorderLayout.NORTH);

        tabs.addTab("Find Recipes", buildFindRecipesTab());
        tabs.addTab("Recipe Tools", buildRecipeToolsTab());
        tabs.addTab("Inventory", buildInventoryTab());
        tabs.addTab("Favourites", buildFavouritesTab());
        tabs.addTab("AI Help", buildAIHelpTab());
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
        setAIHelpEnabled(false);
        BakingTheme.apply(frame, frame.getContentPane(), currentRecipeBanner, tabs);
        tabs.addChangeListener(event -> {
            if (tabs.getSelectedIndex() == 3) refreshFavourites();
        });
    }

    private JPanel buildFindRecipesTab() {
        JPanel panel = tabPanel();
        panel.add(instruction("Describe what you want to bake"), BorderLayout.NORTH);

        JPanel requestRow = new JPanel(new BorderLayout(8, 0));
        installPlaceholder(requestField, "e.g. chocolate chip cookies for 12 people");
        requestRow.add(requestField, BorderLayout.CENTER);
        requestRow.add(askButton, BorderLayout.EAST);

        findResults.setRows(5);
        JScrollPane resultScroll = new JScrollPane(findResults);
        resultScroll.setBorder(BorderFactory.createTitledBorder("Agent response"));

        JPanel listArea = new JPanel(new BorderLayout(6, 6));
        listArea.setBorder(BorderFactory.createTitledBorder("Suggested recipes"));
        listArea.add(new JLabel("Choose a recipe, then use the button below."), BorderLayout.NORTH);
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

        JPanel toolRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        toolRow.add(buildScaleGroup());
        checkButton.setText("Check what's missing");
        checkButton.addActionListener(event -> checkMissingIngredients());
        toolRow.add(checkButton);
        recipeToolsResults.setRows(7);
        JScrollPane output = new JScrollPane(recipeToolsResults);
        output.setBorder(BorderFactory.createTitledBorder("Results"));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        shoppingButton.addActionListener(event -> makeShoppingList());
        saveFavouriteButton.addActionListener(event -> saveFavourite());
        actions.add(shoppingButton);
        actions.add(saveFavouriteButton);

        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.add(toolRow, BorderLayout.NORTH);
        center.add(output, BorderLayout.CENTER);
        center.add(actions, BorderLayout.SOUTH);
        recipeToolsPanel.add(center, BorderLayout.CENTER);
        return recipeToolsPanel;
    }

    private JPanel buildInventoryTab() {
        JPanel panel = tabPanel();
        JPanel introduction = new JPanel(new BorderLayout(4, 4));
        introduction.add(instruction("Ingredients you have"), BorderLayout.NORTH);
        introduction.add(new JLabel("Add what is in your kitchen. Recipe Tools uses this list to check what is missing."),
                BorderLayout.CENTER);
        panel.add(introduction, BorderLayout.NORTH);
        panel.add(buildIngredientsGroup(), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildFavouritesTab() {
        JPanel panel = tabPanel();
        panel.add(instruction("Your saved recipes are here. Choose one explicitly to work with it."), BorderLayout.NORTH);
        JPanel listPanel = new JPanel(new BorderLayout(6, 6));
        listPanel.add(emptyFavouritesMessage, BorderLayout.NORTH);
        listPanel.add(new JScrollPane(favouritesList), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton use = new JButton("Use this recipe");
        use.addActionListener(event -> useFavourite());
        JButton remove = new JButton("Remove");
        remove.addActionListener(event -> removeFavourite());
        actions.add(use);
        actions.add(remove);
        listPanel.add(actions, BorderLayout.SOUTH);
        panel.add(listPanel, BorderLayout.CENTER);
        refreshFavourites();
        return panel;
    }

    private JPanel buildAIHelpTab() {
        aiHelpPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        aiHelpPanel.add(instruction("Ask for recipe-specific advice. You can answer a clarification below."), BorderLayout.NORTH);
        JPanel actions = new JPanel(new GridLayout(0, 1, 8, 8));

        JPanel substituteGroup = titledGroup("Find a substitute");
        JPanel substituteRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        substituteRow.add(new JLabel("Ingredient"));
        substituteIngredient.setRenderer((list, value, index, selected, focus) -> {
            JLabel label = new JLabel(value == null ? "" : formatIngredient((Ingredient) value));
            if (selected) {
                label.setOpaque(true);
                label.setBackground(list.getSelectionBackground());
                label.setForeground(list.getSelectionForeground());
            }
            return label;
        });
        substituteRow.add(substituteIngredient);
        JButton substituteButton = new JButton("Find");
        substituteButton.addActionListener(event -> findSubstitute());
        substituteRow.add(substituteButton);
        substituteGroup.add(substituteRow, BorderLayout.CENTER);

        JPanel tasteGroup = titledGroup("Change the taste");
        JPanel tasteRow = new JPanel(new BorderLayout(8, 0));
        installPlaceholder(tasteField, "e.g. make it less sweet");
        tasteRow.add(tasteField, BorderLayout.CENTER);
        JButton tasteButton = new JButton("Customize");
        tasteButton.addActionListener(event -> customizeTaste());
        tasteRow.add(tasteButton, BorderLayout.EAST);
        tasteGroup.add(tasteRow, BorderLayout.CENTER);

        JPanel problemGroup = titledGroup("Fix a baking problem");
        problemField.setLineWrap(true);
        problemField.setWrapStyleWord(true);
        installPlaceholder(problemField, "e.g. my cookies spread too much");
        JScrollPane problemScroll = new JScrollPane(problemField);
        JButton troubleshootButton = new JButton("Troubleshoot");
        troubleshootButton.addActionListener(event -> troubleshoot());
        JPanel problemBody = new JPanel(new BorderLayout(8, 0));
        problemBody.add(problemScroll, BorderLayout.CENTER);
        problemBody.add(troubleshootButton, BorderLayout.EAST);
        problemGroup.add(problemBody, BorderLayout.CENTER);

        actions.add(substituteGroup);
        actions.add(tasteGroup);
        actions.add(problemGroup);
        aiInputError.setForeground(new java.awt.Color(170, 35, 35));
        actions.add(aiInputError);
        aiResults.setRows(6);
        JScrollPane resultsScroll = new JScrollPane(aiResults);
        resultsScroll.setBorder(BorderFactory.createTitledBorder("AI response"));

        JPanel answerRow = new JPanel(new BorderLayout(8, 0));
        answerRow.add(new JLabel("Answer a clarification:"), BorderLayout.WEST);
        answerRow.add(clarificationField, BorderLayout.CENTER);
        JButton sendAnswer = new JButton("Send answer");
        sendAnswer.addActionListener(event -> answerClarification());
        answerRow.add(sendAnswer, BorderLayout.EAST);
        JPanel responsePanel = new JPanel(new BorderLayout(6, 6));
        responsePanel.add(resultsScroll, BorderLayout.CENTER);
        responsePanel.add(answerRow, BorderLayout.SOUTH);
        aiHelpPanel.add(actions, BorderLayout.CENTER);
        aiHelpPanel.add(responsePanel, BorderLayout.SOUTH);
        return aiHelpPanel;
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
        JPanel group = new JPanel(new BorderLayout(7, 7));
        group.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        group.setLayout(new BorderLayout(7, 7));
        JPanel entry = new JPanel(new GridLayout(1, 6, 6, 4));
        entry.add(new JLabel("Ingredient"));
        installPlaceholder(ingredientNameField, "e.g. flour");
        entry.add(ingredientNameField);
        entry.add(new JLabel("Amount"));
        installPlaceholder(ingredientAmountField, "e.g. 2");
        entry.add(ingredientAmountField);
        entry.add(new JLabel("Unit"));
        entry.add(ingredientUnit);
        JButton addIngredient = new JButton("Add ingredient");
        addIngredient.addActionListener(event -> addPantryIngredient());
        JPanel entrySection = new JPanel(new BorderLayout(5, 5));
        entrySection.add(entry, BorderLayout.CENTER);
        entrySection.add(addIngredient, BorderLayout.EAST);

        JPanel listSection = new JPanel(new BorderLayout(5, 5));
        listSection.add(new JScrollPane(pantryList), BorderLayout.CENTER);
        JPanel listActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JLabel emptyPantry = new JLabel("Nothing added yet");
        emptyPantry.setName("emptyPantryMessage");
        pantryList.putClientProperty("emptyMessage", emptyPantry);
        JButton removeIngredient = new JButton("Remove selected");
        removeIngredient.addActionListener(event -> removePantryIngredient());
        JButton clearIngredients = new JButton("Clear all");
        clearIngredients.addActionListener(event -> clearPantry());
        listActions.add(emptyPantry);
        listActions.add(removeIngredient);
        listActions.add(clearIngredients);
        listSection.add(listActions, BorderLayout.SOUTH);

        JPanel center = new JPanel(new BorderLayout(5, 5));
        center.add(entrySection, BorderLayout.NORTH);
        center.add(listSection, BorderLayout.CENTER);
        group.add(center, BorderLayout.CENTER);
        ingredientsError.setForeground(new java.awt.Color(170, 35, 35));
        group.add(ingredientsError, BorderLayout.SOUTH);
        return group;
    }

    private void askAgent() {
        String request = placeholderValue(requestField, "e.g. chocolate chip cookies for 12 people");
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
        activateRecipe(recipe);
    }

    private void activateRecipe(Recipe recipe) {
        selectedRecipe = recipe;
        currentRecipeBanner.setText("Current recipe: " + selectedRecipe.getName());
        statusLine.setText("Now working on: " + selectedRecipe.getName());
        recipeToolsResults.setText("For " + selectedRecipe.getName() + ":");
        setRecipeToolsEnabled(true);
        setAIHelpEnabled(true);
        substituteIngredient.removeAllItems();
        selectedRecipe.getIngredients().forEach(substituteIngredient::addItem);
    }

    private void scaleRecipe() {
        servingsError.setText(" ");
        int servings;
        try { servings = positiveInteger(servingsField.getText()); }
        catch (IllegalArgumentException error) { servingsError.setText(error.getMessage()); return; }
        selectedRecipe = controller.scaleRecipe(requireSelectedRecipe(), servings);
        showRecipeResult("Scaled to " + servings + " servings: " + formatIngredients(selectedRecipe.getIngredients()));
    }

    private void checkMissingIngredients() {
        ingredientsError.setText(" ");
        List<Ingredient> missing = controller.checkIngredients(requireSelectedRecipe(), availableIngredients);
        showRecipeResult(availableIngredients.isEmpty()
                ? "Your Inventory is empty. Add ingredients in the Inventory tab. All ingredients are missing: "
                        + formatIngredients(selectedRecipe.getIngredients())
                : missing.isEmpty() ? "You have all required ingredients."
                : "Missing: " + formatIngredients(missing));
    }

    private void addPantryIngredient() {
        ingredientsError.setText(" ");
        String name = placeholderValue(ingredientNameField, "e.g. flour");
        String amountText = placeholderValue(ingredientAmountField, "e.g. 2");
        if (name.isBlank()) { ingredientsError.setText("Enter an ingredient name."); return; }
        double amount;
        try {
            amount = Double.parseDouble(amountText);
            if (!Double.isFinite(amount) || amount < 0) throw new NumberFormatException();
        } catch (NumberFormatException error) {
            ingredientsError.setText("Enter a valid non-negative amount.");
            return;
        }
        String unit = Objects.toString(ingredientUnit.getSelectedItem(), "cup");
        if (unit.equals("none (count)")) unit = "count";
        List<Ingredient> updated = controller.mergeIngredients(availableIngredients,
                List.of(new Ingredient(name, amount, unit)));
        availableIngredients.clear();
        availableIngredients.addAll(updated);
        ingredientNameField.setText("");
        ingredientAmountField.setText("");
        updatePantryList();
    }

    private void removePantryIngredient() {
        Ingredient selected = pantryList.getSelectedValue();
        if (selected == null) { ingredientsError.setText("Select an ingredient to remove."); return; }
        availableIngredients.remove(selected);
        updatePantryList();
    }

    private void clearPantry() {
        availableIngredients.clear();
        updatePantryList();
        ingredientsError.setText(" ");
    }

    private void updatePantryList() {
        pantryModel.clear();
        availableIngredients.forEach(pantryModel::addElement);
        Component emptyMessage = (Component) pantryList.getClientProperty("emptyMessage");
        if (emptyMessage != null) emptyMessage.setVisible(availableIngredients.isEmpty());
        pantryList.revalidate();
        pantryList.repaint();
    }

    private void makeShoppingList() {
        ShoppingList list = controller.generateShoppingList(requireSelectedRecipe(), availableIngredients);
        showRecipeResult(list.getItems().isEmpty() ? "Your shopping list is empty."
                : "Shopping list: " + formatIngredients(list.getItems()));
    }

    private void saveFavourite() {
        Recipe recipe = requireSelectedRecipe();
        recipeToolsResults.setText("For " + recipe.getName() + ":\n" + (controller.addFavourite(recipe)
                ? "Saved to favourites." : "This recipe is already in your favourites."));
        refreshFavourites();
    }

    private void useFavourite() {
        Recipe recipe = favouritesList.getSelectedValue();
        if (recipe == null) {
            emptyFavouritesMessage.setText("Choose a saved recipe first.");
            return;
        }
        activateRecipe(recipe);
    }

    private void removeFavourite() {
        Recipe recipe = favouritesList.getSelectedValue();
        if (recipe == null) {
            emptyFavouritesMessage.setText("Choose a saved recipe to remove.");
            return;
        }
        controller.removeFavourite(recipe);
        refreshFavourites();
        statusLine.setText("Removed " + recipe.getName() + " from favourites.");
    }

    private void refreshFavourites() {
        favouritesModel.clear();
        List<Recipe> saved = controller.getFavourites();
        saved.forEach(favouritesModel::addElement);
        emptyFavouritesMessage.setText(saved.isEmpty() ? "No saved favourites yet." : "");
    }

    private void findSubstitute() {
        aiInputError.setText(" ");
        Ingredient ingredient = (Ingredient) substituteIngredient.getSelectedItem();
        if (ingredient == null) {
            aiInputError.setText("Choose an ingredient from the selected recipe.");
            return;
        }
        runAIRequest("What can I use instead of " + ingredient.getName() + " in "
                + requireSelectedRecipe().getName() + "?");
    }

    private void customizeTaste() {
        aiInputError.setText(" ");
        String taste = placeholderValue(tasteField, "e.g. make it less sweet");
        if (taste.isBlank()) {
            aiInputError.setText("Describe the taste change you want.");
            return;
        }
        runAIRequest("Customize the taste of " + requireSelectedRecipe().getName() + " to " + taste + ".");
    }

    private void troubleshoot() {
        aiInputError.setText(" ");
        String problem = placeholderValue(problemField, "e.g. my cookies spread too much");
        if (problem.isBlank()) {
            aiInputError.setText("Describe the baking problem.");
            return;
        }
        runAIRequest("Troubleshoot this problem with " + requireSelectedRecipe().getName() + ": " + problem);
    }

    private void answerClarification() {
        aiInputError.setText(" ");
        String answer = clarificationField.getText().trim();
        if (answer.isEmpty()) {
            aiInputError.setText("Enter an answer to the clarification question.");
            return;
        }
        clarificationField.setText("");
        runAIRequest(answer);
    }

    private void runAIRequest(String request) {
        statusLine.setText("Thinking...");
        List<Ingredient> pantry = List.copyOf(availableIngredients);
        setAIHelpEnabled(false);
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() {
                return controller.processRequest(new BakingRequest(request, 0, 0, pantry, List.of(), ""));
            }
            @Override protected void done() {
                try { aiResults.setText(get()); }
                catch (Exception error) { aiResults.setText("Unable to complete the request: " + messageFor(error)); }
                statusLine.setText("Ready");
                setAIHelpEnabled(selectedRecipe != null);
            }
        }.execute();
    }

    private void showRecipeResult(String result) {
        recipeToolsResults.setText("For " + requireSelectedRecipe().getName() + ":\n" + result);
    }

    private void setRecipeToolsEnabled(boolean enabled) {
        setEnabledRecursively(recipeToolsPanel, enabled);
        // Keep the explanatory text and result area available for an empty-state message.
        recipeToolsResults.setEnabled(true);
    }

    private void setAIHelpEnabled(boolean enabled) {
        setEnabledRecursively(aiHelpPanel, enabled);
        aiResults.setEnabled(true);
        if (tabs.getTabCount() > 4) tabs.setEnabledAt(4, enabled);
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

    private static JList<Ingredient> ingredientList(DefaultListModel<Ingredient> model) {
        JList<Ingredient> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> owner, Object value, int index,
                    boolean selected, boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(owner, value, index, selected, focus);
                if (value instanceof Ingredient ingredient) label.setText(QuantityFormatter.formatIngredient(ingredient));
                return label;
            }
        });
        return list;
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
        return QuantityFormatter.formatIngredients(ingredients);
    }

    private static String formatIngredient(Ingredient ingredient) {
        return QuantityFormatter.formatIngredient(ingredient);
    }

    private static void installPlaceholder(JTextField field, String placeholder) {
        field.setText(placeholder);
        field.setForeground(java.awt.Color.GRAY);
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent event) {
                if (field.getText().equals(placeholder)) { field.setText(""); field.setForeground(java.awt.Color.BLACK); }
            }
            @Override public void focusLost(java.awt.event.FocusEvent event) {
                if (field.getText().isBlank()) { field.setText(placeholder); field.setForeground(java.awt.Color.GRAY); }
            }
        });
    }

    private static void installPlaceholder(JTextArea area, String placeholder) {
        area.setText(placeholder);
        area.setForeground(java.awt.Color.GRAY);
        area.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent event) {
                if (area.getText().equals(placeholder)) { area.setText(""); area.setForeground(java.awt.Color.BLACK); }
            }
            @Override public void focusLost(java.awt.event.FocusEvent event) {
                if (area.getText().isBlank()) { area.setText(placeholder); area.setForeground(java.awt.Color.GRAY); }
            }
        });
    }

    private static String placeholderValue(JTextField field, String placeholder) {
        return field.getText().equals(placeholder) ? "" : field.getText().trim();
    }

    private static String placeholderValue(JTextArea field, String placeholder) {
        return field.getText().equals(placeholder) ? "" : field.getText().trim();
    }

    private void showError(String message) { findResults.setText("Error: " + message); }
    private static String messageFor(Exception error) {
        Throwable cause = error.getCause();
        return Objects.toString(cause == null ? error.getMessage() : cause.getMessage(), "Unable to complete the request.");
    }

    public void start() { frame.setVisible(true); requestField.requestFocusInWindow(); }
}
