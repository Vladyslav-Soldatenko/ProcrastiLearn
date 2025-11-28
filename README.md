# ProcrastiLearn

ProcrastiLearn is an Android (Kotlin + Jetpack Compose) app that turns distracting moments into spaced‑repetition reps. Pick the apps you tend to open mindlessly; whenever you launch them, a full‑screen overlay blocks access until you learn/review a word. Optional OpenAI-powered translations (API key required) make adding new words fast.

## How It Works
- Select gated apps: choose which packages to guard in `Apps`. A gate session starts whenever one of them is opened.
- Learn before you scroll: the overlay shows a card over the target app. Reveal the translation, rate it, and the app you opened comes back to the front.
- In-app timer: optionally re-show the overlay every X minutes while you stay inside a gated app.
- Smart scheduling: FSRS (Again/Hard/Good/Easy) with daily limits for new/review cards and mix modes (mixed, reviews-first, new-first).
- All local: vocabulary, preferences, and progress stay fully on-device (opt-in possibility for cloud sync may be added later). For now - no external traffic except optional OpenAI calls you initiate (API key required).

## Features
- 📱 Overlay gate on chosen apps using Accessibility + overlay permissions.
- 🧠 Spaced repetition via FSRS with daily caps.
- ➕ Add words manually or let AI draft translations (prompt is editable). Currently GPT-5-mini is used, which means that you can add hundreds of words for a few cents.
- 📂 Import Anki `.apkg` decks.
- 📋 Word list with search, edit, delete, and “reset progress”.
- ⚙️ Configurable overlay interval, OpenAI key/prompt, and enable/disable switch for gating.

## User Setup
1) Permissions: grant overlay (“draw over other apps”) and Accessibility when prompted on first launch. Accessibility permission is needed only to check what app is currently in foreground.
2) Pick apps to gate: `Apps` tab → toggle the packages you want blocked. Use the master switch to pause/enable ProcrastiLearn.  
3) Add vocabulary: `Add Word` tab → type word/translation or toggle “use AI to generate translation” (first supply your OpenAI API key in Settings). Preview the AI output before saving if you want.  
4) Practice flow: open a gated app → overlay appears → → try to remember what the presented word means → tap “Show translation” → rate. If daily limits are reached, the overlay won’t appear.  

## Privacy
All data (blocked apps, vocabulary, progress, and preferences) is stored locally (opt-in possibility for cloud sync may be added later). No analytics or ads. OpenAI calls use your key directly.

## Roadmap
