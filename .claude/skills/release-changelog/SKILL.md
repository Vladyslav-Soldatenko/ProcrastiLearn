---
name: release-changelog
description: Bump the app version with the gradle bumpVersion task, summarize the commits since the last version bump into a Play Store changelog, get the user's approval on the English text, then translate it into all other supported store locales with natural native-sounding phrasing (never a literal/robotic translation), and open a PR with the result. Use this whenever the user asks to cut a release, bump the version, prepare a changelog, or ship an update to the Play Store — trigger on phrases like "bump the version", "prepare a release", "cut a new version", "write the changelog", or "ship this update".
---

# Release changelog

Turns the commits since the last release into a version bump + a Play Store
changelog in every supported locale, then opens a PR. This is an interactive,
multi-step workflow — do not skip the approval gate in step 4, and do not
batch steps together just to move faster.

## Before you start

Confirm the working tree is clean (`git status`). If there are unrelated
uncommitted changes, stop and ask the user how to proceed rather than mixing
them into the release commit.

## Step 1 — Bump the version

Ask the user which bump type they want (patch/minor/major) unless they
already said so in their request. Default to `patch` — that's what nearly
every release in this repo's history has been. Then run:

```bash
./gradlew bumpVersion -PbumpType=<patch|minor|major>
```

This task (`gradle/scripts/bump-version.gradle.kts`) increments
`versionCode`/`versionName` in `app/build.gradle.kts` and creates an empty
`fastlane/metadata/android/<locale>/changelogs/<newVersionCode>.txt`
placeholder for every locale directory it finds (the `en-US` one gets a
generic placeholder — you'll overwrite it in step 3). Read the task's
`logger.lifecycle` output to get the new `versionCode` — you need it to know
which changelog files to write.

## Step 2 — Find what changed since the last release

Find the commit that last touched `versionCode`/`versionName` in
`app/build.gradle.kts` — that's the previous release boundary:

```bash
git log -S"versionCode = " --oneline -- app/build.gradle.kts
```

The most recent entry in that list (before the bump you just made, which is
still uncommitted) is the last release commit. Then list what happened since:

```bash
git log --no-merges --pretty=format:'%s' <lastReleaseCommit>..HEAD
```

If that comes back empty (e.g. everything landed as squash-merges with no
standalone commits), fall back to including merge commit subjects too. Skip
anything that's pure repo maintenance and invisible to a user — dependency
bumps, CI/workflow tweaks, test-only changes, refactors with no behavior
change, lint/formatting fixes. Keep anything a user would notice: new
features, fixed bugs, UX/copy changes, performance work they'd feel.

## Step 3 — Draft the English changelog

Write Play Store release notes, not a commit log. Look at existing entries
under `fastlane/metadata/android/en-US/changelogs/*.txt` first to match the
established voice: short plain-language sentences, one line per notable
change, no jargon, no ticket numbers, no "refactor"/"chore" commit-speak.
E.g. "Add undo for last dojo/overlay rating." not "feat: implement undo
stack for RatingViewModel".

Write the draft to
`fastlane/metadata/android/en-US/changelogs/<newVersionCode>.txt`, replacing
the placeholder the gradle task put there.

## Step 4 — Get approval (hard gate)

Show the user the drafted English changelog and ask them to approve it or
request changes. Do not proceed to translation until they explicitly approve
— loop on their edits as many times as needed. This step exists because the
rest of the workflow (translation, commit, PR) is expensive to redo, and
because the English text is the source of truth every translation derives
from — a mistake here propagates into every locale.

## Step 5 — Translate into every other locale

For each other locale directory under `fastlane/metadata/android/` (currently
`es-ES`, `fr-FR`, `it-IT`, `ru-RU`, `uk`, `zh-CN` — but read the actual
directory listing rather than assuming this list is current), write
`changelogs/<newVersionCode>.txt` with a translation of the approved English
text.

Translate for meaning and voice, not word-for-word. A literal translation of
app-store copy reads stiff and machine-generated to a native speaker — short
English sentences often need reordering, idiom substitution, or a different
sentence rhythm to sound natural in the target language. Concretely:

- Read a couple of that locale's existing changelog entries first (`git show
  HEAD:fastlane/metadata/android/<locale>/changelogs/<oldVersionCode>.txt` or
  just open the file) to match its established register — some locales in
  this repo use an informal tone, keep that consistent.
- Prefer the phrasing a native speaker would actually write in a Play Store
  changelog over a dictionary-accurate rendering — e.g. don't translate
  English idioms literally, and don't preserve English word order when the
  target language reads better reordered.
- Keep it tight — these are one or two short sentences, not paragraphs.
- Don't translate proper nouns, feature names in quotes, or `strings.xml`
  string references if the changelog text quotes one.

## Step 6 — Commit, push, and open a PR

Stage `app/build.gradle.kts` and every changed `fastlane/metadata/android/**/changelogs/*.txt`
file. Commit with a conventional message, e.g.:

```
chore: bump version to <versionName> and update changelog
```

Push to a new branch (don't push straight to the default branch) and open a
PR. In the PR body, include the approved English changelog so reviewers don't
have to dig through the diff to see what's being announced, and note the
bump type and old → new version.
