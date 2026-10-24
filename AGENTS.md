Dummlaberer — Agent Guidance
- Kotlin Android app with Jetpack Compose and Gradle Kotlin DSL; preserve minSdk = 28 and existing project versions and namespace.
- UI and documentation in English; generated content is German-sounding dadaistic text.
- Remain fully offline: no network, analytics or unnecessary dependencies.
- Prefer simple maintainable design; keep generator and JSON validation testable without Android UI.
- Word Count (1–50) counts word components, not characters or connectors. Connectors occur only between components.
- The bundled app/src/main/assets/dada.json is the Git-tracked default vocabulary and connector configuration; runtime imports must not modify the asset.
- Use the project Gradle wrapper; run relevant tests and assembleDebug after changes where possible.
- Do not commit or push unless explicitly requested.
