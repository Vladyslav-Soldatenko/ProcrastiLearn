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

## Key Permissions

The app requires overlay and accessibility permissions. Document rationale for any permission changes in `AndroidManifest.xml`.

## Commit Style

Imperative present tense with conventional prefixes: `feat:`, `fix:`, `refactor:`, etc.
