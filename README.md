# Baking Agent

## Prerequisites

- Java Development Kit (JDK) 21
- Apache Maven 3.9 or newer

## Configure the environment

Copy the example environment file to `.env` and add your local API key when needed:

```text
copy .env.example .env
```

On macOS or Linux, use `cp .env.example .env`. Keep `.env` private; it is excluded from version control.

## Build and run

Run the test suite and compile the project:

```text
mvn test
mvn compile
```

Start the application with:

```text
mvn exec:java
```

The application currently prints `Baking Agent starting`.
