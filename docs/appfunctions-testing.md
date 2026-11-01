# AppFunctions testing

The public function is `com.procrastilearn.app.appfunctions.VocabularyFunctions#addWord`. It accepts a required `word` and optional `translation`. Manual translations take precedence; duplicates retain their translation and progress. AI translation requires the saved toggle, key and language pair.

Use the disposable `Medium_Phone` API 36.1 emulator and a test user with no provider key or queued words. Do not clear or uninstall an existing user's data. Set `ANDROID_HOME=$HOME/Android/Sdk` and add its `platform-tools` and `emulator` directories to `PATH`. Build with `./gradlew :app:assembleDebug`, then install the debug APK in the disposable user. Record the device fingerprint and `adb shell cmd app_function help`; shell capabilities vary by image.

List functions using `adb shell cmd app_function list-app-functions --user USER_ID`. Inspect the actual JSON for the identifier, instructions, parameters and enabled state before execution. Use the discovered ID in the following remote shell command, replacing the user and function placeholders:

```sh
adb shell "cmd app_function execute-app-function --user USER_ID --package com.procrastilearn.app --function 'FUNCTION_ID' --parameters '{\"word\":\"AF-smoke\",\"translation\":\"manual fixture\"}'"
```

Repeat the word with another translation and confirm the original is unchanged. Exercise blank words and a new word without a translation, confirming invalid-argument responses and no insertion. Test non-ASCII text and quotes with properly serialized JSON. Inspect the vocabulary UI for persistence. Disable the function using the help-advertised `set-enabled` command, verify rejection, and restore the default state even on failure. Kill the background process using `am kill` (not force-stop), then execute a fresh addition before opening the activity to verify cold service startup. Remove only test fixtures afterwards.

Run `./gradlew ktlintCheck detekt lintDebug testDebugUnitTest :app:assembleDebug :app:assembleRelease`. Inspect generated metadata and both APKs for the schema, service XML and stable ID. A local unit test cannot prove system indexing or service binding; run the device checks for that. The optional official Android testing agent provides independent caller coverage, and real provider or LLM evaluation requires separate authorization. Debug success does not establish minified release behavior or assistant availability.

## Verification record: 2026-11-01

On base commit `8c791bb` with the migration changes, the full debug unit run reported 1,284 tests and no failures; the focused repository, function, contract and translation tests passed again after the test-only lint annotation change. `ktlintCheck`, `detekt`, `lintDebug`, debug assembly and minified release assembly passed. The release APK contains the schema and function XML, and its manifest contains the protected generated service and both metadata properties.

On `Medium_Phone`, API 36 / Android 16, fingerprint `google/sdk_gphone64_x86_64/emu64xa:16/BE4B.251210.005/14574095:user/release-keys`, the platform discovered the expected ID and parameter contract. Debug ADB calls verified manual insertion, duplicate preservation, invalid and omitted arguments, disabled-state rejection and restoration, Unicode and quotes, and cold-process startup. The disposable user's database confirmed the stored translations. A locally debug-signed copy of the minified release APK passed discovery, addition, duplicate and cold-process execution. The official testing agent at sample commit `0bb367a2b7ef276218ac37f8eb45f6c9a81ca0cd` independently discovered and invoked the release function for addition and duplicate cases. The disposable user and fixtures were removed afterwards.

API 30 instrumentation was not run locally: the prescribed local AVD is API 36.1. Existing API 30 CI remains in place. Real provider calls and LLM-driven evaluation were not performed; neither device shell nor testing-agent success establishes Gemini availability.
