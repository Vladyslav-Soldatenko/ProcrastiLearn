# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

ProcrastiLearn is an Android app (Kotlin + Jetpack Compose) that blocks access to distracting apps with a flashcard overlay. Users must review a spaced-repetition vocabulary card before accessing gated apps. Optional OpenAI integration provides AI-generated translations.

## Build & Development Commands

```bash
# Build
./gradlew assembleDebug

# Install on device/emulator
./gradlew :app:installDebug

# Tests
./gradlew testDebugUnitTest                # Unit tests (JVM)
./gradlew connectedDebugAndroidTest        # Instrumented tests (requires device)

# Code quality (all run as part of `check`)
./gradlew detekt                           # Static analysis
./gradlew lintDebug                        # Android lint
./gradlew ktlintCheck                      # Kotlin style check
./gradlew ktlintFormat                     # Auto-fix style issues

# Full check (includes ktlint and detekt)
./gradlew check
```

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

A comment is worth writing only when it captures something the code itself can't: a non-obvious constraint, a subtle invariant, the reason for a workaround, or a "why" a reader can't derive by reading the surrounding lines. For example, `@Suppress("LongParameterList") // arity from composing already-decomposed collaborators, not an undecomposed monolith` explains *why* the suppression is justified — that's fine. Rationale for *why* a change was made belongs in the commit message or PR description, not as a source comment.

## Key Permissions

The app requires overlay and accessibility permissions. Document rationale for any permission changes in `AndroidManifest.xml`.

## Commit Style

Imperative present tense with conventional prefixes: `feat:`, `fix:`, `refactor:`, etc.
