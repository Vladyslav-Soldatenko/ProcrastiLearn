# Fastlane–Google Play Initial Local Integration Plan

## Goal and boundaries

This plan takes the project from its current state to a first working local Fastlane flow that can:

1. Validate and upload the Google Play store listing for all 16 supported locales while leaving the changes unsubmitted for manual review.
2. Build a signed Android App Bundle and place it in a production release with `release_status: "draft"`.
3. Leave the final review submission and production rollout as manual actions in Google Play Console.

This initial integration does **not** add CI/CD, automatic public releases, track promotion, localized screenshot capture, tablet assets, preview video, staged rollout, or automatic version bumps.

## Current-state audit

- [x] Android package name is `com.procrastilearn.app`.
- [x] Current source version is code `17`, name `1.4.3`.
- [x] The application targets Android API 36.
- [x] Store descriptions and changelogs already exist under `fastlane/metadata/android`.
- [x] Metadata exists for 16 locales.
- [x] All full descriptions currently have the same 19-paragraph structure.
- [x] Existing title, short-description, and full-description lengths fit Google Play limits.
- [x] The existing `bumpVersion` Gradle task already creates changelog files under the Fastlane metadata tree.
- [ ] There is no `Gemfile`, `.ruby-version`, `fastlane/Appfile`, or `fastlane/Fastfile`.
- [ ] Fedora 44 currently has no Ruby, Bundler, or Fastlane installed.
- [x] Every locale has `title.txt` containing `ProcrastiLearn`.
- [ ] Nine locales have no listing images.
- [ ] The seven populated locales contain byte-for-byte identical images rather than localized images.
- [ ] Existing screenshots are not Google Play compliant: their long edge is more than twice the short edge, and the PNG files contain alpha.
- [ ] The existing feature graphic is correctly sized at 1024×500 but needs re-encoding as a 24-bit PNG without alpha.
- [ ] The existing icon needs verification or re-encoding as a 32-bit 512×512 PNG with alpha.
- [ ] `app/build.gradle.kts` has no reproducible release-signing configuration.
- [ ] No Google Play Developer API credentials exist yet.

The metadata directory is a useful foundation, but it is not a working Fastlane integration by itself.

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
- [ ] Make the Fastlane draft lane reject missing signing variables before invoking Gradle. Do not allow the lane to silently produce an unsigned bundle.
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
- [ ] Do not use `app/release/app-release.aab` as the Fastlane input. It is a historical ignored artifact, not a reproducible build output.

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

- [ ] Treat the current `en-US` images as the canonical initial image set.
- [ ] Do not copy the current screenshots unchanged. They violate Google Play’s aspect-ratio and alpha-channel requirements.
- [ ] Convert each screenshot without stretching:
  - Scale proportionally to 2,400 pixels high.
  - Center on a 1350×2400 canvas, producing a 9:16 portrait image.
  - Fill the side padding with a color matching the app UI.
  - Remove alpha.
  - Export as a 24-bit sRGB PNG.
- [ ] Use ImageMagick for deterministic conversion. Test the command on a temporary copy before overwriting committed assets. The intended shape is:

  ```bash
  magick input.png \
    -resize x2400 \
    -background '#F9F7FF' \
    -gravity center \
    -extent 1350x2400 \
    -alpha remove \
    -alpha off \
    -colorspace sRGB \
    -type TrueColor \
    output.png
  ```

- [ ] Visually inspect every converted screenshot. Reject cropped controls, stretched text, unexpected bars, transparency, obsolete UI, or misleading content.
- [ ] Re-encode `featureGraphic.png` as a 24-bit 1024×500 PNG without alpha.
- [ ] Re-encode `icon.png` as a 32-bit 512×512 PNG with alpha and keep it below 1,024KB.
- [x] Store the repaired feature graphic, icon, and five screenshots once under `en-US`, then add file-level relative symlinks for those seven assets in each other locale directory. Do not symlink an entire `images` directory: Fastlane does not traverse directory symlinks when it discovers screenshots.
- [ ] Use the same names and order everywhere:

  ```text
  images/icon.png
  images/featureGraphic.png
  images/phoneScreenshots/1.png
  images/phoneScreenshots/2.png
  images/phoneScreenshots/3.png
  images/phoneScreenshots/4.png
  images/phoneScreenshots/5.png
  ```

- [ ] Confirm that corresponding files have identical SHA-256 hashes across all locales.
- [ ] Accept English UI in these shared screenshots for the initial integration. Google permits untranslated in-app UI where there are no added untranslated marketing overlays.
- [ ] Do not add tablet, Chromebook, TV, Wear OS, Automotive, XR, promo-video, or preview-video assets in this initial flow.

Google requires screenshots to be JPEG or 24-bit PNG without alpha, from 320px to 3840px, with the long dimension no more than twice the short dimension: [Google Play preview asset requirements](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en).

## Phase 8: Add Fastlane configuration

- [x] Create `fastlane/Appfile` without literal secrets and load the ignored root environment file:

  ```ruby
  require "dotenv"

  Dotenv.load(File.expand_path("../fastlane.env", __dir__))

  package_name("com.procrastilearn.app")
  json_key_file(ENV.fetch("PLAY_JSON_KEY_PATH"))
  ```

- [ ] Create `fastlane/Fastfile`.
- [ ] Set:

  ```ruby
  default_platform(:android)
  ```

- [ ] Define the exact 16-locale allowlist once and reuse it for every metadata check.
- [ ] Add a local metadata validator that fails before network access when:
  - A required locale is missing.
  - An unexpected locale exists.
  - `title.txt`, `short_description.txt`, or `full_description.txt` is missing.
  - A title differs from `ProcrastiLearn`.
  - A text field exceeds its Google Play limit.
  - A full description does not contain 19 non-empty paragraph blocks.
  - An icon, feature graphic, or any of the five screenshots is missing.
  - Image dimensions, format, alpha handling, size, or screenshot count is invalid.
  - Corresponding shared assets differ between locales.
  - A required release changelog is empty, missing, or longer than 500 characters.

### Lane: `android validate_credentials`

- [ ] Require `PLAY_JSON_KEY_PATH`.
- [ ] Call `validate_play_store_json_key`.
- [ ] Perform no metadata or release mutation.

### Lane: `android validate_metadata`

- [ ] Run the local metadata validator.
- [ ] Call `upload_to_play_store` with `validate_only: true`.
- [ ] Skip APK, AAB, and changelog upload.
- [ ] Include listing text, images, and screenshots in server-side validation.

### Lane: `android upload_metadata_staged`

- [ ] Run the local metadata validator.
- [ ] Upload listing titles, descriptions, images, and screenshots for all 16 locales.
- [ ] Set:

  ```ruby
  skip_upload_apk: true
  skip_upload_aab: true
  skip_upload_changelogs: true
  sync_image_upload: true
  changes_not_sent_for_review: true
  rescue_changes_not_sent_for_review: false
  ```

- [ ] If Play refuses to keep changes unsubmitted, fail. Do not retry using a setting that might send them for review.

### Lane: `android upload_production_draft`

- [ ] Require all service-account and release-signing environment variables.
- [ ] Require an explicit guard:

  ```bash
  CONFIRM_PRODUCTION_DRAFT=YES
  ```

- [ ] Require a clean Git working tree.
- [ ] Read version code and version name from `app/build.gradle.kts`.
- [ ] Query active Play track version codes and reject an obviously reused or lower version code.
- [ ] Require a non-empty `<versionCode>.txt` changelog in all 16 locales.
- [ ] Run only `bundleRelease`. Do not run lint, unit tests, or emulator tests inside this lane.
- [ ] Verify that the expected AAB exists and is signed.
- [ ] Upload using:

  ```ruby
  track: "production"
  release_status: "draft"
  skip_upload_metadata: true
  skip_upload_images: true
  skip_upload_screenshots: true
  skip_upload_changelogs: false
  ```

- [ ] Never call track promotion or use `release_status: "completed"`.
- [ ] Do not add a lane that completes, submits, promotes, or rolls out a production release.
- [ ] Document every lane’s side effects at the top of the Fastfile.

Reference: [Fastlane `upload_to_play_store`](https://docs.fastlane.tools/actions/upload_to_play_store/).

## Phase 9: Validate and upload the listings

- [ ] Load the external secret environment.
- [ ] Validate service-account access:

  ```bash
  bundle exec fastlane android validate_credentials
  ```

- [ ] Fix API and permission failures before proceeding. Do not grant account-wide administrator access merely to bypass a 403.
- [ ] Run local and server-side metadata validation:

  ```bash
  bundle exec fastlane android validate_metadata
  ```

- [ ] Confirm that validation did not create a visible Play Console change.
- [ ] Upload the listing as unsubmitted changes:

  ```bash
  bundle exec fastlane android upload_metadata_staged
  ```

- [ ] In Play Console, inspect all 16 language selectors.
- [ ] For every locale, verify:
  - Title
  - Short description
  - Full description
  - Icon
  - Feature graphic
  - Five ordered screenshots
- [ ] Confirm that the changes are marked as not sent for review.
- [ ] Submit the listing changes manually only after inspection.
- [ ] Remember that store-listing changes are shared across tracks; they are not isolated to internal testing.

## Phase 10: Create the first production draft

- [ ] Choose the next semantic-version bump type.
- [ ] Run the existing explicit bump task, for example:

  ```bash
  ./gradlew bumpVersion -PbumpType=patch
  ```

- [ ] Confirm that version code and version name changed correctly.
- [ ] Confirm that the new version code is greater than the highest version code already known to Play.
- [ ] Fill the newly created changelog file in every locale.
- [ ] Reject empty or untranslated placeholder changelogs.
- [ ] Keep each localized changelog at or below 500 Unicode characters.
- [ ] Run normal project checks separately if required. The selected draft lane intentionally performs only the bundle build and release preflights.
- [ ] Commit the version and changelog changes.
- [ ] Confirm a clean working tree:

  ```bash
  git status --short
  ```

- [ ] Load secrets and set the explicit draft guard:

  ```bash
  set -a
  source ~/.config/procrastilearn/google-play/fastlane.env
  set +a
  export CONFIRM_PRODUCTION_DRAFT=YES
  ```

- [ ] Create the production draft:

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

- [ ] `.ruby-version` pins Ruby 3.4.7.
- [ ] `Gemfile` and `Gemfile.lock` pin Fastlane and its dependency graph.
- [ ] `fastlane/Appfile` defines the package and environment-provided credential path.
- [ ] `fastlane/Fastfile` exposes:
  - `android validate_credentials`
  - `android validate_metadata`
  - `android upload_metadata_staged`
  - `android upload_production_draft`
- [ ] Every locale gains `title.txt` and a complete compliant image set.
- [ ] `app/build.gradle.kts` gains environment-backed upload-key signing.
- [ ] `.gitignore` blocks credentials, keystores, environment files, and optional local Bundler output.

No application runtime API, database schema, package name, or user-facing feature behavior changes.

## Acceptance tests

- [ ] `ruby --version`, `bundle --version`, and `bundle exec fastlane --version` work in a new terminal.
- [ ] `bundle exec fastlane lanes` lists exactly the four intended Android lanes.
- [ ] Unsetting a required credential variable makes the relevant lane fail before network access.
- [ ] An invalid JSON-key path makes credential validation fail clearly.
- [ ] Metadata validation accepts exactly the 16 required locales and rejects missing or unexpected locales.
- [ ] Temporarily exceeding any 30, 80, 4000, or 500-character limit causes local validation to fail.
- [ ] Missing or malformed images fail locally before Fastlane contacts Play.
- [ ] `validate_metadata` leaves no Play Console changes.
- [ ] `upload_metadata_staged` changes only listing text and images and leaves them unsubmitted.
- [ ] `bundleRelease` creates a signed AAB with the expected upload-certificate fingerprint.
- [ ] A dirty working tree blocks the production-draft lane.
- [ ] A missing or empty localized changelog blocks the production-draft lane.
- [ ] A missing confirmation guard blocks the production-draft lane.
- [ ] `upload_production_draft` changes only the production draft and its localized release notes.
- [ ] No Fastlane lane can submit a release for review, promote a track, complete a rollout, or publish to users.
- [ ] `git status` shows no credentials, keystores, generated bundles, or local environment files.

## Definition of done

- [ ] All 16 localized listings pass local and Google Play validation.
- [ ] All 16 listings contain the same title, equivalent description structure, and a complete shared image set.
- [ ] Listing changes can be uploaded and left for manual review.
- [ ] A new signed AAB can be built from the Fedora laptop using external secrets.
- [ ] The AAB can be uploaded into a production draft with localized release notes.
- [ ] No public release or review submission happens automatically.
- [ ] The entire routine is documented by committed, reproducible project configuration while all private keys and passwords remain outside the repository.
