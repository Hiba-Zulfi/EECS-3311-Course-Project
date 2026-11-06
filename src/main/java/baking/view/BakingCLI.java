package baking.view;

import baking.controller.AgentController;
import baking.model.BakingRequest;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Objects;
import java.util.Scanner;

/** Text interface that sends user requests through the controller facade. */
public final class BakingCLI {
    private final AgentController controller;
    private final Scanner scanner;
    private final PrintStream output;
    private final PrintStream errors;
    private boolean awaitingClarification;
    private boolean running = true;

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

    /** Sends free text to the agent; slash command handling is added with CLI utilities. */
    public void submitCommand(String command) {
        if (command == null || command.isBlank()) {
            displayError("Please enter a baking request or command.");
            return;
        }
        String text = command.trim();
        try {
            String response = controller.processRequest(new BakingRequest(text, 0, 0,
                    List.of(), List.of(), ""));
            displayResult(response);
            awaitingClarification = response != null && response.toLowerCase().contains("would you like");
        } catch (IllegalArgumentException | IllegalStateException e) {
            awaitingClarification = false;
            displayError(e.getMessage() == null ? "Unable to process that request." : e.getMessage());
        }
    }

    public void displayResult(String result) { output.println(Objects.requireNonNullElse(result, "")); }
    public void displayError(String error) { errors.println("Error: " + Objects.requireNonNullElse(error, "Unknown error.")); }
}
