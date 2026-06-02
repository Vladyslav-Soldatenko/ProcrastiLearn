# F-Droid Publication Plan — ProcrastiLearn

**App ID:** `com.procrastilearn.app`
**Source:** https://github.com/Vladyslav-Soldatenko/ProcrastiLearn
**Current version:** versionName `1.0`, versionCode `2`

---

## Overview

F-Droid is a free-software app store for Android. It **builds your app from source** using its own infrastructure and signs the APK with its own key. This means users coming from GitHub or Play Store cannot receive F-Droid updates without reinstalling — unless you set up reproducible builds (covered in Phase 6).

The submission path: prepare the app → add a metadata YAML to the `fdroiddata` GitLab repo → wait for volunteer review → app appears in F-Droid within ~48 hours of merge.

**Estimated total calendar time:** 1–2 days of preparation + 1–4 weeks of review queue.

---

## Anti-Feature Note

ProcrastiLearn uses optional OpenAI integration (user provides their own API key). Because the app's core functionality works without it and users opt in by providing their own key, this is unlikely to trigger the `NonFreeNet` anti-feature label. If reviewers do apply it, it is a cosmetic label on the app page — not a rejection reason.

---

## Phase 1 — Repository Hygiene

These changes are required for F-Droid acceptance.

### 1.1 Add a License File

- [x] Choose a license (Apache-2.0 is the most common for Android apps; MIT is simpler; GPL-3.0-only is the strongest copyleft)
- [x] Create a `LICENSE` file at the **repo root** with the full license text
- [x] Verify the license is SPDX-recognized: see https://spdx.org/licenses/
- [x] Note the chosen SPDX identifier (e.g., `Apache-2.0`) — you will use it exactly in the F-Droid metadata YAML

> **Why:** F-Droid requires all included apps to be Free and Open Source Software. The `LICENSE` file is the hard proof.

### 1.2 Disable Google Dependency Info Blob

- [x] Open `app/build.gradle.kts`
- [x] Inside the `android { }` block, add:
  ```kotlin
  dependenciesInfo {
      includeInApk = false
      includeInBundle = false
  }
  ```
- [x] Rebuild to confirm nothing breaks: `./gradlew assembleDebug`

> **Why:** Without this, Gradle embeds a proprietary Google-encrypted dependency blob in the APK. F-Droid's scanner flags this for new apps as of late 2024, and it breaks reproducible build verification.

### 1.3 Audit All Dependencies for FOSS Compliance

- [x] Open `gradle/libs.versions.toml` and review every library entry
- [x] Confirm **none** of the following are present (these are hard blockers):
  - `com.google.android.gms` (Google Play Services)
  - `com.google.firebase` (Firebase)
  - `com.crashlytics` / `com.google.firebase:firebase-crashlytics`
  - `com.google.android.gms:play-services-ads` (AdMob)
  - Any proprietary analytics SDK (Amplitude, Mixpanel, Segment, etc.)
- [x] Confirm `com.openai:openai-java` is the OpenAI Java SDK — it is MIT-licensed and FOSS ✓
- [x] Confirm all other libraries come from Maven Central, Google Maven (FOSS artifacts), OSS Sonatype, or JitPack
- [ ] Run F-Droid's local scanner (optional, after installing `fdroidserver`): `fdroid scanner /path/to/apk`

### 1.4 Verify No Hardcoded API Keys

- [x] Confirm the OpenAI API key is entered by the user at runtime and stored in DataStore — **not hardcoded** in any source file, `strings.xml`, or `BuildConfig`
- [x] Search the repo as a sanity check:
  ```bash
  grep -rE "sk-[a-zA-Z0-9]{20,}" app/src/
  grep -rE "OPENAI_API_KEY\s*=\s*\"[^\"]+\"" app/src/
  ```
- [x] Confirm no secrets in `local.properties` are ever committed (check `.gitignore`)

### 1.5 Check for Pre-Built Binaries in the Repo

- [x] Run: `find . -name "*.jar" -o -name "*.aar" -o -name "*.so" | grep -v build/ | grep -v .gradle/`
- [x] If any pre-built binaries exist in source (not in build output), remove them or justify why they are there
- [x] Confirm any `.aar` files are only being pulled via Gradle from trusted Maven repos, not committed to the repo

---

## Phase 2 — Release Tagging

F-Droid detects new versions via git tags. The tag system must be consistent.

- [x] Decide on a tag naming scheme and stick to it — recommended: `v1.0.0` (semver with `v` prefix)
- [x] Ensure `versionCode` in `app/build.gradle.kts` is an integer that increases with every release (current: `2`)
- [x] Ensure `versionName` in `app/build.gradle.kts` matches the tag (e.g., tag `v1.0.0` → `versionName = "1.0.0"`)
- [x] Create and push the release tag for the version you want F-Droid to build first:
  ```bash
  git tag v1.0.0
  git push origin v1.0.0
  ```
- [x] Verify the tag is visible on GitHub at `https://github.com/Vladyslav-Soldatenko/ProcrastiLearn/tags`
- [ ] **Invariant to maintain forever:** every time you release, bump `versionCode`, update `versionName`, commit, tag, push tag. F-Droid will auto-detect it within 1–2 build cycles (currently ~daily).

---

## Phase 3 — Fastlane Store Listing Metadata

F-Droid reads your app description, screenshots, and changelogs directly from your source repo if you follow the fastlane directory structure. This means you never need to update the fdroiddata repo just to change your description.

### 3.1 Create Directory Structure

- [x] Create the fastlane directory tree at the repo root:
  ```
  fastlane/
  └── metadata/
      └── android/
          └── en-US/
              ├── short_description.txt
              ├── full_description.txt
              ├── changelogs/
              │   └── 2.txt          ← use your versionCode as filename
              └── images/
                  ├── icon.png
                  ├── featureGraphic.png
                  └── phoneScreenshots/
                      ├── 1.png
                      └── 2.png      ← add more as needed
  ```
- [x] Commit and push the entire `fastlane/` directory

### 3.2 Write `short_description.txt`

- [x] Write a one-line description of the app — **max 80 characters** (69 chars used)
- [x] **Must not end with a period**
- Result: `Block distracting apps until you review a spaced-repetition flashcard`

### 3.3 Write `full_description.txt`

- [x] Write a full description — **max 4000 characters**, plain text (2303 chars used)
- [x] Covers: what the app does, key features, language support note (EN-RU default, editable AI prompt for other pairs), permissions rationale, privacy, open-source note
- [x] No markdown formatting

### 3.4 Write the Changelog

- [x] Created `fastlane/metadata/android/en-US/changelogs/2.txt` (197 chars, limit 500)
- [x] For each future release, create a new file named after the new `versionCode`

### 3.5 Add Images

- [x] **`icon.png`**: 512×512 px PNG — place at `fastlane/metadata/android/en-US/images/icon.png` (use your launcher icon)
- [x] **`featureGraphic.png`**: 1024×500 px PNG — place at `fastlane/metadata/android/en-US/images/featureGraphic.png`
- [x] **`phoneScreenshots/1.png`**, **`2.png`**, **`3.png`**: 1080×2400 px screenshots
- [x] EXIF data is automatically stripped by F-Droid — no need to pre-strip
- [x] After adding images: commit and push the entire `fastlane/` directory

---

## Phase 4 — fdroiddata Metadata YAML

This is the recipe file that tells F-Droid how to build your app.

### 4.1 Set Up the fdroiddata Repo

- [ ] Create a GitLab account at https://gitlab.com if you don't have one
- [ ] Fork `https://gitlab.com/VVS232/fdroiddata` to your GitLab account
- [ ] Clone your fork locally:
  ```bash
  git clone --depth=1 https://gitlab.com/VVS232/fdroiddata.git
  cd fdroiddata
  ```
- [ ] Create a branch named after your app ID:
  ```bash
  git checkout -b com.procrastilearn.app
  ```

### 4.2 Install fdroidserver (for local validation)

- [ ] Install fdroidserver:
  ```bash
  # Option A — Debian/Ubuntu package
  sudo apt-get install fdroidserver

  # Option B — pip (more up-to-date)
  python -m venv fdroidserver-env
  source fdroidserver-env/bin/activate
  pip install git+https://gitlab.com/fdroid/fdroidserver.git
  ```
- [ ] Verify: `fdroid --version`

### 4.3 Create the Metadata File

- [ ] Create `metadata/com.procrastilearn.app.yml` in your local fdroiddata clone with the following content (fill in the placeholders):

```yaml
Categories:
  - Education

License: Apache-2.0

AuthorName: Vladyslav Soldatenko
AuthorEmail: teenwolf23299@gmail.com
AuthorWebSite: https://github.com/Vladyslav-Soldatenko

WebSite: https://github.com/Vladyslav-Soldatenko/ProcrastiLearn
SourceCode: https://github.com/Vladyslav-Soldatenko/ProcrastiLearn
IssueTracker: https://github.com/Vladyslav-Soldatenko/ProcrastiLearn/issues
Changelog: https://github.com/Vladyslav-Soldatenko/ProcrastiLearn/releases

AutoName: ProcrastiLearn

RepoType: git
Repo: https://github.com/Vladyslav-Soldatenko/ProcrastiLearn.git

Builds:
  - versionName: '1.0.0'
    versionCode: 2
    commit: v1.0.0
    subdir: app
    gradle:
      - yes

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: '1.0.0'
CurrentVersionCode: 2
```

- [x] Replace `License:` value with the exact SPDX identifier — `Apache-2.0`
- [ ] Confirm `versionName`, `versionCode`, and `commit` (the tag name) all match what is in `app/build.gradle.kts` at that tag (do this after creating the tag)

### 4.4 Validate the Metadata Locally

- [ ] Run from inside the `fdroiddata` directory:
  ```bash
  fdroid lint com.procrastilearn.app
  ```
  Fix every warning — zero warnings required before submission.

- [ ] Run rewrite to ensure canonical field ordering:
  ```bash
  fdroid rewritemeta com.procrastilearn.app
  git diff metadata/com.procrastilearn.app.yml   # review changes
  ```
  Commit any changes the rewrite made.

- [ ] (Optional but recommended) Test a local build. Requires `ANDROID_HOME` set:
  ```bash
  fdroid build -v -l com.procrastilearn.app
  ```
  If this succeeds locally, it will almost certainly succeed on F-Droid's build server.

### 4.5 Commit and Push

- [ ] Stage and commit the metadata file:
  ```bash
  git add metadata/com.procrastilearn.app.yml
  git commit -m "New app: com.procrastilearn.app"
  git push origin com.procrastilearn.app
  ```

---

## Phase 5 — Submission

### 5.1 Open an RFP Issue (Optional but Recommended)

- [ ] Go to https://gitlab.com/fdroid/rfp/issues
- [ ] Click "New Issue" and use the provided template
- [ ] Fill in the app name, GitHub URL, description, and license
- [ ] Note the issue number — you will reference it in the MR

> This step is optional if you're submitting a full MR directly, but it documents the submission in F-Droid's tracker and lets the community flag concerns early before you invest time in the full MR.

### 5.2 Open the Merge Request

- [ ] Go to your fork on GitLab and open a Merge Request from `com.procrastilearn.app` branch → `fdroid/fdroiddata:master`
- [ ] Title: `New app: ProcrastiLearn`
- [ ] Fill in the MR description template completely:
  - Link to the source repo
  - Link to the LICENSE file
  - Link to the RFP issue (if created)
  - Brief description of what the app does
  - Note any anti-features that may apply (optional OpenAI integration)
- [ ] Submit the MR

### 5.3 Monitor the GitLab CI Pipeline

- [ ] Wait for the automated CI pipeline to run on your MR (takes a few minutes)
- [ ] The pipeline runs `fdroid lint` — it must pass green before any human reviewer will look at your MR
- [ ] If the pipeline fails, read the log, fix the issue in your fork, push, and the pipeline will re-run automatically

### 5.4 Respond to Reviewer Feedback

- [ ] Watch for comments from volunteer reviewers — they may request changes to:
  - Metadata formatting
  - Anti-feature annotations
  - Build recipe corrections
  - Dependency concerns
- [ ] Respond and push fixes within a reasonable time (days, not weeks) to keep the MR moving
- [ ] Common reviewer checks:
  - License file exists and is valid
  - No proprietary SDKs detected by scanner
  - Build recipe produces a working APK
  - Tags/versions are consistent

---

## Phase 6 — Reproducible Builds (Optional)

Reproducible builds let users who installed your app from GitHub Releases receive F-Droid updates (and vice versa) without reinstalling. **Skip this phase for your first submission** — do it after initial acceptance if you want cross-source updates.

- [ ] Ensure your release build is deterministic (pure Kotlin apps usually are)
- [ ] Build a signed release APK locally: `./gradlew assembleRelease`
- [ ] Sign it with your own keystore (set up `signingConfigs` in `build.gradle.kts`)
- [ ] Upload the signed APK to your GitHub Release for the tagged version
- [ ] Get the APK's signing key SHA-256 fingerprint:
  ```bash
  apksigner verify --print-certs app-release.apk
  # Copy the "SHA-256 digest" value
  ```
- [ ] Add to your fdroiddata metadata YAML:
  ```yaml
  Binaries: https://github.com/Vladyslav-Soldatenko/ProcrastiLearn/releases/download/v%v/app-release.apk
  AllowedAPKSigningKeys: <lowercase-hex-sha256-from-apksigner>
  ```
- [ ] Submit another MR to fdroiddata to add these fields
- [ ] After F-Droid verifies the match, your own-signed APK replaces the F-Droid-signed one in the index

---

## Phase 7 — After Acceptance

- [ ] App appears in F-Droid within 24–48 hours of the fdroiddata MR being merged
- [ ] Verify by searching for "ProcrastiLearn" in the F-Droid client
- [ ] Install from F-Droid and smoke-test the published version

### Ongoing Release Workflow

For every new release:

- [ ] Bump `versionCode` (increment integer) in `app/build.gradle.kts`
- [ ] Update `versionName` in `app/build.gradle.kts`
- [ ] Add `fastlane/metadata/android/en-US/changelogs/<newVersionCode>.txt` to your app repo
- [ ] Commit and push
- [ ] Create and push the new tag:
  ```bash
  git tag v1.1
  git push origin v1.1
  ```
- [ ] F-Droid's `checkupdates` bot will detect the new tag automatically within 1–2 build cycles (~daily) and queue a build — **no manual action required in fdroiddata**
- [ ] If your build process changes (new Gradle flags, new subdir, new flavor), open a new MR to fdroiddata to update the `Builds:` block

---

## Quick Reference: Common Rejection Reasons

| Issue | Fix |
|---|---|
| Missing `LICENSE` file | Add it to repo root |
| Wrong/missing `License:` SPDX identifier | Use exact SPDX id from spdx.org/licenses |
| `short_description.txt` ends with `.` | Remove the period |
| `versionCode` in YAML doesn't match `build.gradle` at that tag | Sync them — all three must match |
| Pre-built `.jar`/`.aar` in repo | Remove; use Gradle dependency instead |
| Proprietary SDK detected | Remove the dependency |
| Google dependency blob in APK | Add `dependenciesInfo { includeInApk = false }` |
| Hardcoded API key | Move to user-provided runtime input |
| Git tag doesn't exist on remote | `git push origin <tag>` |
| CI pipeline fails on lint | Run `fdroid lint` locally and fix all warnings |
| Fields out of canonical order | Run `fdroid rewritemeta` and commit |

---

## Key URLs

| Resource | URL |
|---|---|
| F-Droid Inclusion Policy | https://f-droid.org/en/docs/Inclusion_Policy/ |
| Anti-Features reference | https://f-droid.org/en/docs/Anti-Features/ |
| Inclusion How-To guide | https://f-droid.org/docs/Inclusion_How-To/ |
| Build Metadata YAML reference | https://f-droid.org/docs/Build_Metadata_Reference/ |
| Descriptions & Screenshots guide | https://f-droid.org/en/docs/All_About_Descriptions_Graphics_and_Screenshots/ |
| fdroiddata repo (fork this) | https://gitlab.com/fdroid/fdroiddata |
| Requests for Packaging (RFP) | https://gitlab.com/fdroid/rfp/issues |
| fdroidserver install docs | https://f-droid.org/docs/Installing_the_Server_and_Repo_Tools/ |
| Reproducible Builds docs | https://f-droid.org/docs/Reproducible_Builds/ |
| Signing Process docs | https://f-droid.org/docs/Signing_Process/ |
| SPDX License List | https://spdx.org/licenses/ |
| F-Droid Build Monitor | https://monitor.f-droid.org/builds/build |
| IzzyOnDroid (fast alternative repo) | https://izzyondroid.org/ |

---

## Alternative: IzzyOnDroid First

IzzyOnDroid is a large, well-maintained third-party F-Droid-compatible repository. Unlike the official F-Droid repo, it accepts developer-provided APKs (no source build) and reviews apps within days rather than weeks. It is pre-added in many alternative F-Droid clients (Neo Store, Droid-ify).

Consider submitting to IzzyOnDroid in parallel while waiting for official F-Droid review — it gets your app in front of F-Droid users much faster. Many apps stay in IzzyOnDroid permanently.

- [ ] (Optional) Submit to IzzyOnDroid at https://izzyondroid.org/ while the F-Droid MR is in review
