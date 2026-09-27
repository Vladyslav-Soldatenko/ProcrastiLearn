# Project instructions

These instructions apply to every AI agent working in this repository.

## Project Overview

ProcrastiLearn is a local-first Android app that turns opening a distracting app into a vocabulary review opportunity. Users choose which apps to gate. When one comes to the foreground, an overlay asks them to recall, reveal, and rate scheduled flashcards before access resumes. The number of cards per gate is configurable, and an optional interval can bring the gate back while the user remains in that app.

Users can also study directly in the Dojo, manage their vocabulary, and study cards forward, backward, or in both directions. FSRS schedules reviews; daily limits and study preferences control which cards are available. Vocabulary can be added manually, captured from selected text in another app, imported from Anki or app JSON, and exported to app JSON. Translation suggestions are optional and use a user-provided OpenAI key. Vocabulary, preferences, and review progress are stored on-device.

## Build and test instructions

For build, test, or emulator setup tasks, see the [build and test instructions](docs/build-and-test-instructions.md).

## Google Play release workflow

For Google Play listing or release tasks, follow the [Google Play release guide](docs/google-play-release.md) before acting.

## Architecture

The Android Accessibility service detects selected foreground apps and manages gate sessions. It hosts a Compose overlay outside the normal Activity UI; treat its lifecycle and dependency access accordingly.

Compose screens and ViewModels present observable state. Domain models, repository contracts, and use cases express study operations. Data repositories connect those contracts to Room for vocabulary and review state, DataStore for preferences and counters, and integrations for translation and vocabulary transfer. Hilt supplies dependencies across these boundaries.

Preserve existing study progress when changing stored vocabulary or review state. Room schema changes need migrations, and the versioned JSON transfer format may need a compatible migration too.

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
