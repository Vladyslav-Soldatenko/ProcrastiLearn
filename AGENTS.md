# Project instructions

These instructions apply to every AI agent working in this repository.

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

## Running Instrumented Tests (Emulator)

The Android SDK is at `$HOME/Android/Sdk` but its tools aren't on `PATH` by default. Set up the environment before using `emulator`/`adb`:

```bash
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools
```

```bash
# List available AVDs
emulator -list-avds

# List running/connected devices
adb devices -l

# Check a running emulator's API level
adb -s emulator-5554 shell getprop ro.build.version.sdk

# Boot an AVD headless in the background if none is running
emulator -avd <avd-name> &
```

Always use the `Medium_Phone` AVD, API 36.1 — it's the only one installed, and it's the one to use. Don't expect or look for other API levels.

Once a device/emulator is attached, run instrumented tests the normal Gradle way (no special env beyond `adb` being reachable):

```bash
./gradlew connectedDebugAndroidTest
```

## Google Play release workflow

Run every command in this section from project root jolder.

### Responsibility boundaries

Gradle owns local listing and changelog validation, release signing configuration checks, the release AAB build, JVM-based bundle signature verification, and creation of one prepared release. These operations do not contact Google Play. Fastlane owns credential checks, Play release-context queries, validate-only listing uploads, listing submissions, and production-draft uploads. CI runs local validation and workflow tests without signing secrets or Play credentials.

A listing submission changes the shared Play store listing and sends those changes into the app's Play review flow. A production draft uploads an AAB and localized release notes to the production track with `release_status: "draft"`; it does not roll out, promote, complete, or publish that release. Keep the two workflows separate.

The enforced platform limits and their official Google sources are centralized in `buildSrc/src/main/kotlin/com/procrastilearn/play/PlayListingRequirements.kt`. Titles and assets may differ by locale. Paragraph counts may differ. Each locale may contain any valid number of phone screenshots from two through eight.

Fastlane upload behavior follows the stock [`upload_to_play_store` action](https://docs.fastlane.tools/actions/upload_to_play_store/).

### Prerequisites and environment

Use Ruby 3.4.7 from `.ruby-version`, Bundler 4.0.20 from `Gemfile.lock`, JDK 21, and the Android SDK referenced by `local.properties` or `ANDROID_HOME`. Install the locked Ruby dependencies once:

```bash
ruby --version
java -version
gem install bundler -v 4.0.20
bundle config set --local path vendor/bundle
bundle install
./gradlew --version
```

If `local.properties` is absent, create it locally with `sdk.dir=/absolute/path/to/Android/Sdk`, or export `ANDROID_HOME`. Do not commit `local.properties`.

Fastlane reads the ignored root `fastlane.env` through `fastlane/Appfile`. Copy `fastlane.env.example` to `fastlane.env` if the private file is not already present, then set paths and secret values locally. The public environment-variable names are:

- `PLAY_JSON_KEY_PATH`: readable Google Play service-account JSON key used by all Fastlane lanes.
- `PROCRASTILEARN_UPLOAD_STORE_FILE`: readable upload keystore used by release preparation.
- `PROCRASTILEARN_UPLOAD_STORE_PASSWORD`, `PROCRASTILEARN_UPLOAD_KEY_ALIAS`, and `PROCRASTILEARN_UPLOAD_KEY_PASSWORD`: private signing values used by the Android release build.
- `CONFIRM_METADATA_REVIEW_SUBMISSION`: assertion required before submitting store-listing changes for review.

Fastlane loads `fastlane.env` itself. Direct Gradle release preparation needs the signing values in its inherited process environment:

```bash
set -a
source fastlane.env
set +a
./gradlew :app:preparePlayRelease
```

Never commit `fastlane.env`, JSON keys, keystores, passwords, AABs, or prepared output. Ordinary Gradle validation, `check`, and CI require none of these secrets and do not require network access.

### Command reference

| Command | What it reads and produces | Builds | Credentials | Google contact | Remote commit | Success indicator |
|---|---|---:|---|---:|---:|---|
| `./gradlew :app:validatePlayMetadata` | Reads every locale under `fastlane/metadata/android`; no output artifact | No | None | No | No | `Play listing metadata is valid.` |
| `./gradlew :app:validatePlayChangelogs` | Reads `<versionCode>.txt` for every discovered locale using the Android `defaultConfig.versionCode`; no output artifact | No | None | No | No | `Play changelogs for version <code> are valid.` |
| `./gradlew :app:validatePlayReleaseSigning` | Checks the four signing variables and keystore path; does not read Play credentials | No | Signing | No | No | `Release signing configuration is present.` |
| `./gradlew :app:preparePlayRelease` | Invalidates old prepared output, validates current changelogs/signing, runs `bundleRelease`, verifies that the AAB is signed, then snapshots the AAB and changelogs | Yes, release AAB | Signing | No | No | Prints the prepared `manifest.json` path after all checks pass |
| `bundle exec fastlane android validate_credentials` | Queries the internal track to check service-account authentication and app access | No | Play | Yes | No | `Google Play credentials and app access are valid.` |
| `bundle exec fastlane android validate_metadata` | Chooses an existing standard-track release context and sends descriptions/images with `validate_only: true` | No | Play | Yes | No | Fastlane and Supply finish successfully with validate-only enabled |
| `bundle exec fastlane android upload_metadata_for_review` | Uploads source listing descriptions and images; skips APK, AAB, and changelogs | No | Play plus listing confirmation | Yes | Yes | Fastlane upload succeeds; then inspect listing/review state in Play Console |
| `bundle exec fastlane android upload_production_draft` | Reads only the prepared manifest, prepared AAB, and prepared changelogs; checks active version codes; uploads to production as a draft | No | Play | Yes | Yes, draft only | Fastlane upload succeeds; then Play Console shows the manifest version on production with draft status |

`validatePlayMetadata` and `validatePlayChangelogs` are dependencies of `:app:check`. `preparePlayRelease` does not run listing validation because listing submission is independent of signing and building.

### Metadata layout

Each immediate directory under `fastlane/metadata/android` is a listing locale and must contain:

```text
fastlane/metadata/android/<locale>/
├── title.txt
├── short_description.txt
├── full_description.txt
├── changelogs/<versionCode>.txt
└── images/
    ├── icon.png
    ├── featureGraphic.png (or .jpg/.jpeg)
    └── phoneScreenshots/
        ├── 1.png
        └── 2.png ... up to 8 files total
```

Text files must be nonempty valid UTF-8. Limits are 30 Unicode characters for the title, 80 for the short description, 4,000 for the full description, and 500 for each changelog. The icon must be a 512×512 32-bit PNG with alpha and at most 1,024 KB. The feature graphic must be a 1,024×500 JPEG or 24-bit PNG without alpha. Phone screenshots must be JPEG or 24-bit PNG without alpha, each dimension must be 320–3,840 pixels, and the long side must be no more than twice the short side. Use two through eight phone screenshot files. Filenames determine upload order, so retain a clear numeric naming sequence.

Individual file symlinks are supported and the current shared assets may remain symlinked. Broken symlinks are reported by relative path. Assets do not have to match across locales.

To add a locale, add a new immediate locale directory with all three text files, all required images, at least two screenshots, and the changelog for the current Android version code. To change a title, description, or image, edit only that locale's file. Run `./gradlew :app:validatePlayMetadata`; also run `./gradlew :app:validatePlayChangelogs` when adding a locale or changing current release notes.

### Listing workflow

1. Edit listing files under `fastlane/metadata/android/<locale>`.
2. Run `./gradlew :app:validatePlayMetadata` and fix every aggregated filename/error.
3. Review the diff, commit the intended files, and confirm `git status --porcelain` is empty. The upload lane rejects a dirty tree.
4. Optionally run `bundle exec fastlane android validate_credentials`, then `bundle exec fastlane android validate_metadata`. The latter contacts Play but uses `validate_only: true` and does not commit changes.
5. Obtain explicit authority to submit the listing changes for review. Set the assertion only for that authorized operation and run:

   ```bash
   CONFIRM_METADATA_REVIEW_SUBMISSION=YES bundle exec fastlane android upload_metadata_for_review
   ```

6. Inspect Main store listing and Publishing overview in Play Console. Confirm the intended locales/assets and resulting review state before reporting remote verification.

Do not retry an ambiguous failed upload blindly. Query or inspect Play Console first because the server may have accepted part or all of the request even when the client lost the response.

### Production draft workflow

1. Bump the version with the supported task:

   ```bash
   ./gradlew bumpVersion -PbumpType=patch --no-configuration-cache
   ```

   Use `minor` or `major` only when that is the intended semantic version change.
2. Fill every generated `fastlane/metadata/android/<locale>/changelogs/<newVersionCode>.txt` with localized nonempty release notes.
3. Run `./gradlew :app:validatePlayChangelogs` and the normal local verification relevant to the source change.
4. Review and commit the version, source, and changelog changes. Confirm `git status --porcelain` is empty.
5. Load the private signing environment and run `./gradlew :app:preparePlayRelease`. Do not use a raw `bundleRelease` output for publishing.
6. Create the production draft:

   ```bash
   bundle exec fastlane android upload_production_draft
   ```

7. Inspect the production track in Play Console. Match the version code/name to `manifest.json` and verify the release status is Draft. Draft creation is not authority to start or complete a rollout.

The Fastlane lane rejects a prepared version code that is not greater than every version code found on production, beta, alpha, and internal tracks.

### Prepared artifacts

`preparePlayRelease` creates one atomic snapshot under `app/build/play-release/prepared`:

```text
app/build/play-release/prepared/
├── manifest.json
├── aab/app-release-<versionCode>.aab
└── metadata/android/<locale>/changelogs/<versionCode>.txt
```

The manifest contains the Android package name, version code, version name, and AAB path relative to the prepared directory. Fastlane reads this manifest instead of parsing `app/build.gradle.kts`, rebuilding, inspecting signing tools, or reading source changelogs. The manifest is written only after the bundle signature check passes. Preparation deletes previous prepared output first; a failed preparation leaves no usable manifest.

Rerun preparation after any source, resource, dependency, version, signing configuration, or current-version changelog change. Old prepared bundles and notes are a snapshot and must not be reused after those inputs change. The prepared directory is ignored build output and does not make a dirty committed tree clean.

### Authority

`CONFIRM_METADATA_REVIEW_SUBMISSION=YES` asserts that the operator has checked the exact committed listing diff and intends to upload it and submit it through Play's review flow.

An environment value is a guard input, not proof of user authorization. An agent must have explicit authorization in the current task before running a mutating lane. Never infer rollout authority from draft-upload authority. No CI job may call a mutating lane.

### Troubleshooting

- Validation failures: read the aggregated relative filenames, fix every listed UTF-8, missing-file, limit, image, dimension, alpha, count, or broken-symlink problem, and rerun the same Gradle task.
- Missing Gradle/JDK/SDK tools: use JDK 21, check `./gradlew --version`, and set `sdk.dir` or `ANDROID_HOME` to an installed Android SDK.
- Missing Ruby dependencies: use Ruby 3.4.7 and run `bundle install`; always invoke the locked Fastlane with `bundle exec fastlane`.
- Missing Play credentials: set `PLAY_JSON_KEY_PATH` to a readable service-account JSON file and run `validate_credentials`. Authentication success does not imply sufficient Play app permissions.
- Missing signing credentials: load all four `PROCRASTILEARN_UPLOAD_*` values into the Gradle process. Check the keystore path without printing passwords.
- Dirty working tree: inspect `git status --short`, review and commit intended release/listing changes, and keep unrelated or secret files out of Git. Do not bypass the guard.
- Signature verification failure: stop. Confirm the intended signing variables and keystore were loaded, then rebuild the release bundle.
- Version-code conflict: bump to a code greater than every active Play-track code, complete the new changelogs, commit, and prepare again.
- Missing release context: `validate_metadata` and listing upload need an existing release on production, beta, alpha, or internal for Supply. Create or restore the intended Play release context; do not hard-code or silently substitute another version.
- Play review errors: preserve the error output, inspect Publishing overview and the affected listing/release, resolve the reported Play state, then rerun only after confirming whether the original request committed.

After any ambiguous upload failure, inspect remote state before retrying.

### CI and completion evidence

The CI verify job runs:

```bash
./gradlew ktlintCheck detekt lint testDebugUnitTest validatePlayMetadata validatePlayChangelogs
./gradlew -p buildSrc test
ruby fastlane/spec/fastfile_test.rb
```

The first two validation tasks require no signing or Play credentials. The build-plugin tests cover invalid UTF-8, missing files, text limits, invalid images, broken symlinks, localized titles/assets, variable paragraph/screenshot counts, changelog failures, and failed preparation without a manifest. The Ruby tests invoke lanes with stubbed Play actions and assert listing-only and draft-only upload options.

Report evidence according to the completed step:

- Local listing/changelog validation: exact command, successful Gradle result, validated Android version code for changelogs, and confirmation that no Play credentials were used.
- Prepared release: successful task, manifest path and parsed package/version/AAB relative path, signature verification success, and the complete set of copied locale changelogs. Do not expose secret values.
- Remote validate-only listing check: selected track/version release context, `validate_only: true`, successful Fastlane result, and confirmation that no remote commit was requested.
- Listing submission: explicit authority, clean-tree evidence, successful lane result, and independently inspected Play listing/review state. A local or validate-only pass is not submission evidence.
- Production draft: explicit authority, clean-tree evidence, prepared manifest version, remote version-code preflight, successful lane result, and independently inspected production Draft state. A successful upload response alone is not verified remote state, and a draft is not a rollout.

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
