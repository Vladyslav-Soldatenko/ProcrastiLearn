# Fastlane–Google Play Initial Local Integration Plan

## Goal and boundaries

This plan takes the project from its current state to a first working local Fastlane flow that can:

1. Validate and upload the Google Play store listing for all 16 supported locales, explicitly submitting the metadata changes for Google Play review.
2. Build a signed Android App Bundle and place it in a production release with `release_status: "draft"`. The lane must run only when Google Play Console has no unrelated changes waiting to be sent for review, because committing an API edit can submit those unrelated pending changes.
3. Leave production release submission and rollout as manual actions in Google Play Console. Metadata review submission is a separate, explicit Fastlane operation.

This initial integration does **not** add CI/CD, automatic public releases, track promotion, localized screenshot capture, tablet assets, preview video, staged rollout, or automatic version bumps.

## Current-state audit

- [x] Android package name is `com.procrastilearn.app`.
- [x] Current source version is code `18`, name `1.4.4`.
- [x] The application targets Android API 36.
- [x] Store descriptions and changelogs already exist under `fastlane/metadata/android`.
- [x] Metadata exists for 16 locales.
- [x] All full descriptions currently have the same 19-paragraph structure.
- [x] Existing title, short-description, and full-description lengths fit Google Play limits.
- [x] The existing `bumpVersion` Gradle task already creates changelog files under the Fastlane metadata tree.
- [x] The repository has `Gemfile`, `Gemfile.lock`, `.ruby-version`, `fastlane/Appfile`, and `fastlane/Fastfile`.
- [x] Fedora 44 has the pinned Ruby, Bundler, and Fastlane toolchain installed.
- [x] Every locale has `title.txt` containing `ProcrastiLearn`.
- [x] All 16 locales resolve the complete shared image set through seven canonical `en-US` files and file-level relative symlinks.
- [x] Corresponding locale image paths have identical SHA-256 hashes without duplicating image bytes in the repository.
- [x] The temporary canonical screenshots are 1350×2400 24-bit PNGs without alpha. They are technically compliant but will be replaced with native 9:16 captures for final promotional quality.
- [x] The canonical feature graphic is a 1024×500 24-bit PNG without alpha.
- [x] The canonical icon is a 32-bit 512×512 PNG with alpha and is below 1,024KB.
- [x] `app/build.gradle.kts` has environment-backed reproducible release-signing configuration.
- [x] Google Play Developer API credentials are stored outside the repository and scoped to this app.

The local integration and read-only Google Play credential validation are complete, but both mutating workflows remain unverified.

## Phase 1: Verify the Play app and signing key

- [x] Opened Google Play Console and selected `com.procrastilearn.app`.
- [x] Recorded the highest version code currently known to Google Play: `17` (`1.4.3`), the latest closed-testing (Alpha) release, fully rolled out and available to testers.
- [x] Confirmed that Play App Signing is enabled; Google Play signs releases with the app-signing key.
- [x] Opened the app-integrity page and recorded the SHA-256 fingerprint under **Upload key certificate**, not the separate Play app-signing certificate.
- [x] Compared the Play upload-certificate fingerprint with the certificate on the existing repository AAB; they match exactly:

  ```text
  C6:AA:76:E0:C2:20:D2:19:F5:42:B2:AE:6B:72:58:C3:0C:38:8D:27:CA:91:D4:0E:CD:6D:8D:85:4C:BD:73:52
  ```

- [x] Fingerprints match, so no upload-key recovery or reset is required.
- [x] Confirm that the upload keystore, alias, store password, and key password are available.
- [x] Make a separate encrypted backup of the upload keystore and its credentials. A second copy on the same laptop is not a backup.
- [x] Do not export or manage the Play app-signing private key through Fastlane. The local workflow needs the upload key.

## Phase 2: Create Google Play Developer API credentials

- [x] Create a dedicated Google Cloud project for this Fastlane integration, unless a suitable dedicated project already exists.
- [x] Enable the **Google Play Android Developer API** in that project.
- [x] Create a service account with a clear name such as `procrastilearn-fastlane`.
- [x] Create a JSON key for the service account.
- [x] Create a protected local directory:

  ```bash
  mkdir -p ~/.config/procrastilearn/google-play
  chmod 700 ~/.config/procrastilearn
  chmod 700 ~/.config/procrastilearn/google-play
  ```

- [x] Store the JSON key at:

  ```text
  ~/.config/procrastilearn/google-play/service-account.json
  ```

- [x] Restrict access to it:

  ```bash
  chmod 600 ~/.config/procrastilearn/google-play/service-account.json
  ```

- [x] In Play Console, invite the service-account email with access only to `com.procrastilearn.app`.
- [x] Grant these app permissions:
  - **View app information (read-only)**
  - **Manage store presence**
  - **Release to production, exclude devices, and use Play App Signing**
- [x] Do not grant account-wide administrator, financial, order-management, review-reply, policy-management, or user-management permissions.
- [x] Record the security consequence: Google does not expose a permission that allows creating production drafts while forbidding production rollout. The production permission is therefore more powerful than the planned lane.
- [x] Never commit, print, or paste the JSON key into an issue, pull request, chat, or shell command.

Reference: [Google Play Developer API setup](https://developers.google.com/android-publisher/getting_started?hl=en) and [Play Console permission definitions](https://support.google.com/googleplay/android-developer/answer/9844686?hl=en).

## Phase 3: Install a reproducible Ruby and Fastlane toolchain

- [x] Install rbenv, ruby-build, and Ruby build dependencies on Fedora 44:

  ```bash
  sudo dnf install \
    rbenv ruby-build gcc gcc-c++ make patch \
    openssl-devel libyaml-devel libffi-devel readline-devel \
    zlib-ng-compat-devel gdbm-devel ncurses-devel
  ```

- [x] Initialize rbenv:

  ```bash
  rbenv init
  ```

- [x] Restart the terminal and verify rbenv:

  ```bash
  rbenv --version
  ```

- [x] Install and pin Ruby 3.4.7:

  ```bash
  rbenv install 3.4.7
  cd /home/apocalypse/Odin/ProcrastiLearn
  rbenv local 3.4.7
  ruby --version
  ```

- [x] Commit the generated `.ruby-version`.
- [x] Create a root `Gemfile`:

  ```ruby
  source "https://rubygems.org"

  gem "fastlane"
  ```

- [x] Install Bundler and resolve the Fastlane dependency graph:

  ```bash
  gem install bundler
  bundle install
  ```

- [x] Commit `Gemfile` and `Gemfile.lock`.
- [x] Run Fastlane only through Bundler:

  ```bash
  bundle exec fastlane ...
  ```

- [x] Add `vendor/bundle/` to `.gitignore` in case Bundler is later configured to install dependencies inside the project.
- [x] Verify the installation:

  ```bash
  bundle exec fastlane --version
  ```

Fastlane supports Ruby 3.1 or newer and prefers Ruby 3.3 or newer. Its documentation recommends Bundler and a committed lockfile: [Fastlane Android setup](https://docs.fastlane.tools/getting-started/android/setup/).

## Phase 4: Configure local secrets

- [x] Store the upload keystore outside the repository:

  ```text
  ~/.config/procrastilearn/google-play/upload-key.jks
  ```

- [x] Create the ignored repository-root environment file:

  ```text
  fastlane.env
  ```

- [x] Add the local values:

  ```bash
  PLAY_JSON_KEY_PATH="$HOME/.config/procrastilearn/google-play/service-account.json"
  PROCRASTILEARN_UPLOAD_STORE_FILE="$HOME/.config/procrastilearn/google-play/upload-key.jks"
  PROCRASTILEARN_UPLOAD_STORE_PASSWORD="..."
  PROCRASTILEARN_UPLOAD_KEY_ALIAS="..."
  PROCRASTILEARN_UPLOAD_KEY_PASSWORD="..."
  ```

- [x] Restrict access to the environment file:

  ```bash
  chmod 600 fastlane.env
  ```

- [ ] Restrict access to the upload keystore:

  ```bash
  chmod 600 ~/.config/procrastilearn/google-play/upload-key.jks
  ```

- [x] Load the variables automatically from `fastlane.env` in `fastlane/Appfile` before Fastlane commands run.

- [x] Extend `.gitignore` defensively:

  ```gitignore
  *.jks
  *.keystore
  *service-account*.json
  .env
  .env.*
  vendor/bundle/
  fastlane.env
  ```

- [ ] Do not put literal secrets in `Appfile`, `Fastfile`, Gradle files, committed documentation, or shell history.
- [ ] Confirm that no secret is tracked:

  ```bash
  git status --short
  git ls-files
  ```

## Phase 5: Make release signing reproducible

- [x] Update `app/build.gradle.kts` with a release signing configuration that reads:
  - `PROCRASTILEARN_UPLOAD_STORE_FILE`
  - `PROCRASTILEARN_UPLOAD_STORE_PASSWORD`
  - `PROCRASTILEARN_UPLOAD_KEY_ALIAS`
  - `PROCRASTILEARN_UPLOAD_KEY_PASSWORD`
- [x] Configure `buildTypes.release.signingConfig` only when all four variables are available, so Android Studio project sync still works without release secrets.
- [x] Make the Fastlane draft lane reject missing signing variables before invoking Gradle. Do not allow the lane to silently produce an unsigned bundle.
- [x] Load `fastlane.env` through Dotenv and build once:

  ```bash
  bundle exec ruby -e 'require "dotenv"; Dotenv.load(File.expand_path("fastlane.env", Dir.pwd)); exec("./gradlew", "bundleRelease")'
  ```

- [x] Confirm that this artifact exists:

  ```text
  app/build/outputs/bundle/release/app-release.aab
  ```

- [x] Verify its signature:

  ```bash
  jarsigner -verify -verbose -certs \
    app/build/outputs/bundle/release/app-release.aab
  ```

- [x] Confirm that the resulting SHA-256 certificate matches the upload certificate shown in Play Console:

  ```text
  C6:AA:76:E0:C2:20:D2:19:F5:42:B2:AE:6B:72:58:C3:0C:38:8D:27:CA:91:D4:0E:CD:6D:8D:85:4C:BD:73:52
  ```
- [x] Do not use `app/release/app-release.aab` as the Fastlane input. It is a historical ignored artifact, not a reproducible build output.

## Phase 6: Complete all 16 localized listings

The exact managed locale set is:

```text
de-DE
en-US
es-ES
fr-FR
hi-IN
id
it-IT
ja-JP
ko-KR
pl-PL
pt-BR
ru-RU
tr-TR
uk
vi
zh-CN
```

- [x] Add `title.txt` to every locale with exactly:

  ```text
  ProcrastiLearn
  ```

- [ ] Treat the repository metadata as authoritative. Do not run `fastlane supply init` over `fastlane/metadata/android`, because it could replace or mix the repository content with the existing Play listing.
- [x] Check every localized full description against the same semantic structure:
  - Introduction and value proposition
  - How the app works
  - Vocabulary management
  - Language support
  - Privacy
  - Permission explanations
  - Open-source information
- [x] Preserve the existing 19 non-empty paragraph blocks in every full description.
- [x] Confirm that every translation makes the same substantive claims. One locale must not advertise different features, privacy behavior, pricing, permissions, or availability.
- [x] Review references to Anki, Duolingo, TikTok, YouTube, and OpenAI for accuracy and necessity. They do not imply affiliation or endorsement.
- [x] Enforce Google Play limits after trimming surrounding whitespace:
  - Title: no more than 30 Unicode characters
  - Short description: no more than 80 Unicode characters
  - Full description: no more than 4,000 Unicode characters
  - Release notes: no more than 500 Unicode characters
- [x] Keep short descriptions free of line breaks, emoji, calls to action, repeated punctuation, ranking claims, and promotional pricing.
- [ ] Keep changelog filenames equal to Android version codes, for example `18.txt`, not version names such as `1.4.4.txt`.

References: [Google Play listing limits](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en) and [Fastlane metadata layout](https://docs.fastlane.tools/actions/upload_to_play_store/).

## Phase 7: Repair and replicate Play assets

- [x] Treat the current `en-US` images as the canonical initial image set.
- [x] Do not copy the original screenshots unchanged. They violate Google Play’s aspect-ratio and alpha-channel requirements.
- [ ] Replace the temporary padded screenshots with native 9:16 emulator captures, preferably 1080×1920, encoded as 24-bit sRGB PNGs without alpha. Do not stretch or compress screenshots. Crop only as a fallback after visual inspection confirms that no meaningful UI is lost.
- [x] Use ImageMagick for deterministic temporary conversion and verification before overwriting the canonical assets.
- [ ] Visually inspect every final screenshot. Reject cropped controls, stretched text, unexpected bars, transparency, obsolete UI, or misleading content.
- [x] Re-encode `featureGraphic.png` as a 24-bit 1024×500 PNG without alpha.
- [x] Re-encode `icon.png` as a 32-bit 512×512 PNG with alpha and keep it below 1,024KB.
- [x] Store the repaired feature graphic, icon, and five screenshots once under `en-US`, then add file-level relative symlinks for those seven assets in each other locale directory. Do not symlink an entire `images` directory: Fastlane does not traverse directory symlinks when it discovers screenshots.
- [x] Use the same names and order everywhere:

  ```text
  images/icon.png
  images/featureGraphic.png
  images/phoneScreenshots/1.png
  images/phoneScreenshots/2.png
  images/phoneScreenshots/3.png
  images/phoneScreenshots/4.png
  images/phoneScreenshots/5.png
  ```

- [x] Confirm that corresponding files have identical SHA-256 hashes across all locales.
- [x] Accept English UI in these shared screenshots for the initial integration. Google permits untranslated in-app UI where there are no added untranslated marketing overlays.
- [ ] Do not add tablet, Chromebook, TV, Wear OS, Automotive, XR, promo-video, or preview-video assets in this initial flow.

Google requires screenshots to be JPEG or 24-bit PNG without alpha, from 320px to 3840px, with the long dimension no more than twice the short dimension: [Google Play preview asset requirements](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en).

## Phase 8: Add Fastlane configuration

- [x] Create `fastlane/Appfile` without literal secrets and load the ignored root environment file:

  ```ruby
  require "dotenv"

  Dotenv.load(File.expand_path("../fastlane.env", __dir__))

  package_name("com.procrastilearn.app")
  json_key_file(ENV["PLAY_JSON_KEY_PATH"]) if ENV["PLAY_JSON_KEY_PATH"]
  ```

- [x] Create `fastlane/Fastfile`.
- [x] Set:

  ```ruby
  default_platform(:android)
  ```

- [x] Define the exact 16-locale allowlist once and reuse it for every metadata check.
- [x] Add a local listing-metadata validator that fails before network access when:
  - A required locale is missing.
  - An unexpected locale exists.
  - `title.txt`, `short_description.txt`, or `full_description.txt` is missing.
  - A title differs from `ProcrastiLearn`.
  - A text field exceeds its Google Play limit.
  - A full description does not contain 19 non-empty paragraph blocks.
  - An icon, feature graphic, or any of the five screenshots is missing.
  - Image dimensions, format, alpha handling, size, or screenshot count is invalid.
  - Corresponding shared assets differ between locales.
- [x] Keep release-changelog validation in the production-draft lane so listing-only operations are not coupled to a release version.

### Lane: `android validate_credentials`

- [x] Require `PLAY_JSON_KEY_PATH`.
- [x] Perform a read-only `google_play_track_version_codes` request. Do not use Fastlane 2.239.0's `validate_play_store_json_key`, which swallows connection exceptions and returns no usable success value.
- [x] Perform no metadata or release mutation.

### Lane: `android validate_metadata`

- [x] Run the local metadata validator.
- [x] Resolve an existing release dynamically from production, beta, alpha, then internal because Supply 2.239.0 requires a release context even when changelog upload is skipped. Never hardcode an app version.
- [x] Call `upload_to_play_store` with `validate_only: true`.
- [x] Skip APK, AAB, and changelog upload.
- [x] Include listing text, images, and screenshots in server-side validation.

### Lane: `android upload_metadata_for_review`

- [x] Run the local metadata validator.
- [x] Resolve the same dynamic release context, preferring the production draft once it exists; store-listing changes remain global rather than track-specific.
- [x] Upload listing titles, descriptions, images, and screenshots for all 16 locales.
- [x] Require a clean Git tree and `CONFIRM_METADATA_REVIEW_SUBMISSION=YES` before changing Play data.
- [x] Set:

  ```ruby
  skip_upload_apk: true
  skip_upload_aab: true
  skip_upload_changelogs: true
  sync_image_upload: true
  ```

- [x] Commit the metadata edit without `changes_not_sent_for_review`; this app automatically sends committed changes for review.

### Lane: `android upload_production_draft`

- [x] Require all service-account and release-signing environment variables.
- [x] Require an explicit guard:

  ```bash
  CONFIRM_PRODUCTION_DRAFT=NO_PENDING_REVIEW_CHANGES
  ```

- [x] Require a clean Git working tree.
- [x] Read version code and version name from `app/build.gradle.kts`.
- [x] Query the four standard active Play tracks and reject an obviously reused or lower version code. This is a best-effort preflight; Play remains authoritative for historical and custom-track version codes.
- [x] Require a non-empty `<versionCode>.txt` changelog in all 16 locales.
- [x] Run only `bundleRelease`. Do not run lint, unit tests, or emulator tests inside this lane.
- [x] Verify that the expected AAB exists, is signed, and uses the recorded upload-certificate SHA-256 fingerprint.
- [x] Upload using:

  ```ruby
  track: "production"
  release_status: "draft"
  skip_upload_metadata: true
  skip_upload_images: true
  skip_upload_screenshots: true
  skip_upload_changelogs: false
  ```

- [x] Do not set `changes_not_sent_for_review` for the production draft. This app rejects that parameter because changes are handled automatically; the draft lifecycle state itself prevents deployment.
- [x] Require the operator to confirm that Play Console contains no unrelated changes waiting for review before running the lane.

- [x] Never call track promotion or use `release_status: "completed"`.
- [x] Do not add a lane that completes, submits, promotes, or rolls out a production release.
- [x] Document every lane’s side effects in its Fastlane description.

Reference: [Fastlane `upload_to_play_store`](https://docs.fastlane.tools/actions/upload_to_play_store/).

## Phase 9: Validate and upload the listings

- [x] Load the external secret environment.
- [x] Validate service-account access:

  ```bash
  bundle exec fastlane android validate_credentials
  ```

- [ ] Fix API and permission failures before proceeding. Do not grant account-wide administrator access merely to bypass a 403.
- [x] Run local and server-side metadata validation:

  ```bash
  bundle exec fastlane android validate_metadata
  ```

- [ ] Confirm that validation did not create a visible Play Console change.
- [x] Upload the listing and submit the metadata changes for review:

  ```bash
  export CONFIRM_METADATA_REVIEW_SUBMISSION=YES
  bundle exec fastlane android upload_metadata_for_review
  ```

- [ ] In Play Console, inspect all 16 language selectors.
- [ ] For every locale, verify:
  - Title
  - Short description
  - Full description
  - Icon
  - Feature graphic
  - Five ordered screenshots
- [x] Confirm that the metadata changes were submitted for review.
- [x] Remember that store-listing changes are shared across tracks; they are not isolated to internal testing.

The original staged-upload approach was exercised against production draft 18. Google rejected the edit commit with `Changes are sent for review automatically. The query parameter changesNotSentForReview must not be set.` The temporary edit was not committed. The lane therefore commits without that parameter and requires an explicit review-submission confirmation.

The production-track API confirmed that draft 18 already contained all 16 localized release notes even though Play Console initially showed only `en-US` and reported one language. After the 16 translated store listings were committed, Play Console showed all 16 release-note languages. Release-note localization visibility therefore depends on the corresponding store-listing languages existing in Play Console. Upload listings before creating future drafts.

## Phase 10: Create the first production draft

- [x] Choose the next semantic-version bump type.
- [x] Run the existing explicit bump task, for example:

  ```bash
  ./gradlew bumpVersion -PbumpType=patch
  ```

- [x] Confirm that version code and version name changed correctly.
- [x] Confirm that the new version code is greater than the highest version code already known to Play.
- [x] Fill the newly created changelog file in every locale.
- [x] Reject empty or untranslated placeholder changelogs.
- [x] Keep each localized changelog at or below 500 Unicode characters.
- [x] Run normal project checks separately if required. The selected draft lane intentionally performs only the bundle build and release preflights.
- [x] Commit the version and changelog changes.
- [x] Confirm a clean working tree:

  ```bash
  git status --short
  ```

- [x] Load secrets and set the explicit draft guard:

  ```bash
  set -a
  source ~/.config/procrastilearn/google-play/fastlane.env
  set +a
  export CONFIRM_PRODUCTION_DRAFT=NO_PENDING_REVIEW_CHANGES
  ```

- [x] Create the production draft:

  ```bash
  bundle exec fastlane android upload_production_draft
  ```

- [ ] In Play Console, verify:
  - Track is production.
  - Release status is draft.
  - Nothing is rolling out.
  - Nothing was sent for review.
  - Package is `com.procrastilearn.app`.
  - Version code and version name match Gradle.
  - The AAB was accepted with the correct upload certificate.
  - All 16 localized release notes are present.
- [ ] Do not click **Start rollout to production** as part of integration verification.
- [ ] Perform final review submission and production rollout manually as a later, separate decision.

Google permits up to 500 Unicode characters per locale for release notes: [Google Play release preparation](https://support.google.com/googleplay/android-developer/answer/9859348?hl=en).

## Files and commands introduced by the integration

- [x] `.ruby-version` pins Ruby 3.4.7.
- [x] `Gemfile` and `Gemfile.lock` pin Fastlane and its dependency graph.
- [x] `fastlane/Appfile` defines the package and environment-provided credential path.
- [x] `fastlane/Fastfile` exposes:
  - `android validate_credentials`
  - `android validate_metadata`
  - `android upload_metadata_for_review`
  - `android upload_production_draft`
- [x] Every locale gains `title.txt` and a complete technically compliant shared image set. Native 9:16 screenshots remain a final promotional-quality improvement.
- [x] `app/build.gradle.kts` gains environment-backed upload-key signing.
- [x] `.gitignore` blocks credentials, keystores, environment files, and optional local Bundler output.

No application runtime API, database schema, package name, or user-facing feature behavior changes.

## Acceptance tests

- [x] `ruby --version`, `bundle --version`, and `bundle exec fastlane --version` work in a new terminal.
- [x] `bundle exec fastlane lanes` lists exactly the four intended Android lanes.
- [x] Unsetting a required credential variable makes the relevant lane fail before network access.
- [x] An invalid JSON-key path makes credential validation fail clearly.
- [x] Metadata validation accepts the current exact set of 16 required locales; rejection tests for missing and unexpected locales remain pending.
- [ ] Temporarily exceeding any 30, 80, 4000, or 500-character limit causes local validation to fail.
- [ ] Missing or malformed images fail locally before Fastlane contacts Play.
- [x] `validate_metadata` completes successfully using `validate_only: true` and does not commit its temporary Play edit.
- [x] `upload_metadata_for_review` changes only listing text and images and submits them for Google Play review.
- [x] A fresh `bundleRelease` output is signed with the expected upload-certificate fingerprint.
- [x] A dirty working tree blocks both mutating lanes.
- [ ] A missing or empty localized changelog blocks the production-draft lane.
- [x] A missing confirmation guard blocks each mutating lane.
- [x] `upload_production_draft` created production draft 18 with its 16 localized release notes.
- [x] No Fastlane lane can submit the production release for review, promote a track, complete a rollout, or publish to users.
- [x] `git status` shows no credentials, keystores, generated bundles, or local environment files.

## Definition of done

- [x] All 16 localized listings pass local and Google Play validation.
- [ ] All 16 listings contain the same title, equivalent description structure, and a complete shared image set.
- [x] Listing changes can be uploaded and explicitly submitted for Google Play review.
- [x] A new signed AAB can be built from the Fedora laptop using external secrets.
- [x] The AAB can be uploaded into a production draft with localized release notes.
- [x] No production release submission or public rollout happens automatically.
- [ ] The entire routine is documented by committed, reproducible project configuration while all private keys and passwords remain outside the repository.
