# Project instructions

These instructions apply to every AI agent working in this repository.

## Project Overview

ProcrastiLearn is an Android app (Kotlin + Jetpack Compose) that blocks access to distracting apps with a flashcard overlay. Users must review a spaced-repetition vocabulary card before accessing gated apps. Optional OpenAI integration provides AI-generated translations.

## Build and test instructions

For build, test, or emulator setup tasks, see the [build and test instructions](docs/build-and-test-instructions.md).

## Google Play release workflow

For Google Play listing or release tasks, follow the [Google Play release guide](docs/google-play-release.md) before acting.

## Architecture

The app follows clean architecture with layer separation:

- **`data/`** - Repository implementations, Room database (DAOs, entities), DataStore preferences, OpenAI translation client
- **`domain/`** - Business models, repository interfaces, use cases
- **`ui/`** - ViewModels and Compose screens/components
- **`overlay/`** - Flashcard overlay system that appears over gated apps
- **`service/`** - Accessibility service for detecting foreground app changes
- **`di/`** - Hilt dependency injection modules
- **`navigation/`** - Compose navigation setup

Key dependencies: Room (persistence), Hilt (DI), FSRS library (spaced-repetition scheduling), OpenAI Java SDK.

## Coding Conventions

- Kotlin with Jetpack Compose for UI
- Kotlin DSL for Gradle files
- Suffixes: `ViewModel`, `Repository`, `Dao`, `Entity`, `Module`, `UseCase`
- Composables: one per file when substantial; previews end with `Preview`
- Tests mirror source paths, end with `*Test.kt`

### Comments

Default to no comments. Code must be self-documenting through naming and structure — do not add comments that just narrate what the following code does. This includes:

- Restating the next line(s) in prose, e.g. `// Check for a duplicate before spending an AI request` above code that does exactly that, or `// Simulate leaving and re-entering the same word` above two `onWordChange` calls.
- Decorative section-divider comments (e.g. `// --- Setup ---`), including in test files.

99.9% of the time comments should not be added. Only add them in EXTREME EXCEPTIONS when something non-obvious happens.

## Key Permissions

The app requires overlay and accessibility permissions. Document rationale for any permission changes in `AndroidManifest.xml`.

## Commit Style

Imperative present tense with conventional prefixes: `feat:`, `fix:`, `refactor:`, etc.
