# Build and test instructions

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
