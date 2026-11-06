# Baking Agent

## Prerequisites

- Java Development Kit (JDK) 21
- Apache Maven 3.9 or newer

## Configure the environment

Copy the example environment file to `.env`, then set your Gemini API key and model name:

```text
copy .env.example .env
```

On macOS or Linux, use `cp .env.example .env`. Replace the placeholders for `LLM_API_KEY` and `LLM_MODEL` with values for your Gemini API access. Environment variables with those names override values in `.env`. Keep `.env` private; it is excluded from version control.

## Build and run

Run the test suite and compile the project:

```text
mvn test
mvn compile
```

Start the command line interface with:

```text
mvn exec:java "-Dexec.args=--cli"
```

The CLI sends plain language baking, recipe search, timing, substitution, taste, and troubleshooting requests to Gemini. Type `/help` for its commands. For example:

```text
/ingredients Flour:2 cups; Sugar:1 cup
/select 1
/check
/scale 6
/shopping
/favourite
/favourites
```

`/ingredients` accepts semicolon separated items in `Name:quantity unit` format. `/select` selects a recipe by catalog ID; `/check`, `/scale`, and `/shopping` use that selection. Use `/favourite remove` to remove the selected recipe. `/quit` exits. Input and parsing errors are printed without terminating the CLI.

The GUI launch option is reserved for a later release:

```text
mvn exec:java "-Dexec.args=--gui"
```

To send the real Gemini model a small connectivity prompt and print its reply, run:

```text
mvn exec:java "-Dexec.args=--llm-test"
```

This command needs a valid `LLM_API_KEY` and `LLM_MODEL` in `.env` or in the process environment. Unit tests use a mock provider and never call the real API.
