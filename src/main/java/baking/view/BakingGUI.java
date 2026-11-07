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
        // Feature controls are installed in the subsequent GUI feature layers.
        controls.add(ingredients);
        controls.add(actions);
        return controls;
    }

    public void start() {
        frame.setVisible(true);
        requestField.requestFocusInWindow();
    }

    private void submitRequest() {
        String requestText = requestField.getText().trim();
        if (requestText.isEmpty()) { showError("Enter a baking request first."); return; }
        submitButton.setEnabled(false);
        statusLine.setText("Thinking...");
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() {
                return controller.processRequest(new BakingRequest(requestText, 0, 0,
                        List.copyOf(availableIngredients), List.of(), ""));
            }
            @Override protected void done() {
                submitButton.setEnabled(true);
                try {
                    resultsArea.setText(get());
                    refreshRecipeList(controller.getLastSearchResults());
                    statusLine.setText("Ready");
                } catch (Exception error) {
                    showError(messageFor(error));
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
