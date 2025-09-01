# Repository Guidelines

## Project Structure & Modules
- `app/`: Single Android app module (Kotlin + Jetpack Compose).
- `app/src/main/java/com/procrastilearn/app`: App code organized by layer: `data/`, `domain/`, `ui/`, `overlay/`, `service/`, `di/`, `navigation/`, `utils/`.
- `app/src/main/res`: Resources; update strings in `values/strings.xml` (+ localized folders).
- `app/src/test`: JVM unit tests (JUnit).
- `app/src/androidTest`: Instrumented tests (AndroidX Test).
- Key configs: `app/build.gradle.kts`, root `build.gradle.kts`, `settings.gradle.kts`, `detekt.yml`, `proguard-rules.pro`.

## Build, Test, and Dev Commands
- Build debug APK: `./gradlew assembleDebug`
- Run unit tests: `./gradlew testDebugUnitTest`
- Run instrumented tests (device/emulator): `./gradlew connectedDebugAndroidTest`
- Static analysis: `./gradlew detekt`
- Lint checks: `./gradlew lintDebug`
- Install debug build: `./gradlew :app:installDebug`

## Coding Style & Naming
- Language: Kotlin, Kotlin DSL for Gradle. Use Kotlin official code style (4‑space indent, max line length per IDE defaults).
- Naming: Classes/Composables `PascalCase` (e.g., `MainActivity`, `LearningCard`), functions/props `camelCase`, constants `UPPER_SNAKE_CASE`.
- Suffixes: `ViewModel`, `Repository`, `Dao`, `Entity`, `Module` for DI, `UseCase` in domain.
- UI: One Composable per file when substantial; preview names end with `Preview`.
- Keep Android permissions and manifests minimal; update `AndroidManifest.xml` with rationale comments for new permissions.

## Testing Guidelines
- Place unit tests under `app/src/test/...`, instrumented under `app/src/androidTest/...`.
- Test names: mirror source path and end with `*Test.kt` (e.g., `VocabularyRepositoryImplTest.kt`).
- Prefer constructor‑injected dependencies and fakes over singletons. Cover repository and use cases; add UI tests for critical flows.
- Run `detekt`, `lint`, and all tests before opening a PR.

## Commit & Pull Requests
- Commits: imperative present tense; group logical changes. Example: `feat: add FSRS module for scheduling`.
- PRs: include summary, linked issues, and test notes. Add screenshots/gifs for UI changes and note permission changes (overlay/accessibility).
- CI readiness: ensure `assembleDebug`, `testDebugUnitTest`, `detekt`, and `lintDebug` pass locally.

## Security & Configuration
- Do not commit secrets. Use `local.properties`/Gradle properties for local keys.
- Be mindful of accessibility/overlay scopes; document user‑visible behavior changes.
