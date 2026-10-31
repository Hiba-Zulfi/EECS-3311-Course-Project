# Project rules
- Language: Java 21. Build: Maven. GUI: Swing. Tests: JUnit 5.
- Implement the design in docs/. Package root: baking.
- Read the API key from the .env file at runtime. Never hardcode it.
- LLM: Google Gemini via java.net.http.HttpClient, behind an
  LLMProvider interface with a GeminiAdapter (Adapter pattern).
- Patterns required: MVC, Strategy, Factory Method (abstract
  ToolCreator + concrete creators), Facade (AgentController), Adapter.
- GUI and CLI must both call AgentController only.
- Recipes come from a JSON file with about 25 sample recipes.
- Deterministic features (ingredient check, scaling, shopping list,
  favourites) must NOT use the LLM.
- Never commit .env or any real API key.
- After each milestone, run `mvn test`, then make a git commit with a
  descriptive message and push.