# R8 Configuration Analysis

Findings from an R8 keep-radius analysis of `app/proguard-rules.pro` (release build, AGP 9.2.1). Denominator: 292,542 live classes/fields/methods.

## Scores

| Metric | Score | Blocked |
|---|---|---|
| Optimization | 33.49% | 66.51% |
| Shrinking | 33.55% | 66.45% |
| Obfuscation | 33.57% | 66.43% |

## Findings & actions

### 1. `-keep class com.openai.** { *; }` — Refine

- **Impact**: 155,774 items (53.25% of the codebase). Classes: 13,288, Fields: 30,756, Methods: 111,730.
- **Why it matters**: single biggest blocker in the app — over half the unoptimizable surface. Retains the *entire* openai-java SDK model catalog (e.g. `com.openai.models.AllModels`, `com.openai.core.JsonField/JsonValue`) regardless of which endpoints the app actually calls.
- **Action**: narrow to what Jackson reflection actually needs — constructors/builders and `@JsonProperty`/`@JsonCreator`-annotated members — instead of the whole `com.openai.**` package.

### 2. `-keep class com.fasterxml.jackson.** { *; }` — Refine

- **Impact**: 19,096 items (6.53%). Classes: 1,281, Fields: 3,976, Methods: 13,839.
- **Why it matters**: package-wide wildcard retains Jackson's internal implementation, not just the annotation-driven model classes that need reflection safety.
- **Action**: narrow to the app's actual serialized model classes plus Jackson's annotation/module SPI surface.

### 3. Redundant duplicate rules — Removed ✅

These rules were strict subsets (identical or smaller blast radius) of a broader `-keep` rule already in the file, so they added no protection and have been deleted from `app/proguard-rules.pro`:

| Rule | Subsumed by | Impact |
|---|---|---|
| `-keepnames class com.openai.** { *; }` | `-keep class com.openai.** { *; }` | 155,774 (identical) |
| `-keepclassmembers,allowobfuscation class com.openai.** { *; }` | `-keep class com.openai.** { *; }` | 142,486 (subset) |
| `-keepnames class com.fasterxml.jackson.** { *; }` | `-keep class com.fasterxml.jackson.** { *; }` | 19,096 (identical) |
| `-keepnames class io.github.openspacedrepetition.** { *; }` | `-keep class io.github.openspacedrepetition.** { *; }` | 187 (identical) |
| `-keepclassmembers,allowobfuscation class io.github.openspacedrepetition.** { *; }` | `-keep class io.github.openspacedrepetition.** { *; }` | 177 (subset) |

## Not flagged

- `-keep class io.github.openspacedrepetition.** { *; }` (FSRS) — small footprint (187 items, 0.06%) and genuinely load-bearing per the existing code comment (library ships no consumer rules, needed for Jackson builder reflection on `Card`). No action needed.
- AGP version (9.2.1) and R8 Full Mode config — already correctly configured, no `-dontoptimize`/`-dontobfuscate`/`-dontshrink` global disables present.

## Suggested order of work

1. ~~Delete the 5 redundant duplicate lines (finding 3) — zero risk, no behavior change.~~ Done.
2. Rebuild release + smoke-test the OpenAI translation flow and FSRS review/scheduling flow to confirm nothing regresses.
3. Investigate narrowing the `com.openai.**` and `com.fasterxml.jackson.**` keep rules (findings 1 & 2) — highest payoff but needs care since the existing broad rules were added to fix a real release-mode crash; re-run this analysis after any narrowing to confirm the scores improve and the app still works.
