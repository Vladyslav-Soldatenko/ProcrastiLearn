# ProcrastiLearn

There's Anki on your phone, there's Duolingo, and you do mean to get around to them. Then you pick the phone up, and twenty minutes later you're somewhere in TikTok, not really sure how you got there. ProcrastiLearn steps in right at that moment and asks you for one word first.

ProcrastiLearn is an Android (Kotlin + Jetpack Compose) app that turns distracting moments into spaced‑repetition reps. Pick the apps you tend to open mindlessly; whenever you launch them, a full‑screen overlay blocks access until you learn/review a word. Optional OpenAI-powered translations (API key required) make adding new words fast.

## How It Works
- Select gated apps: choose which packages to guard in `Apps`. A gate session starts whenever one of them is opened.
- Learn before you scroll: the overlay shows a card over the target app. Reveal the translation, rate it, and the app you opened comes back to the front.
- In-app timer: if you tend to lose track of time once you're already inside a gated app, set an interval (say, every 3 minutes) and the overlay keeps coming back for as long as you stay there — each lingering session turns into extra reps.
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
- UI polish, improve color scheme and UX where needed.
- Auto-delete a word after a good streak.
- Stop TikTok media playback (or mute it at least) during gating; other apps mostly work.
- Let users choose GPT model or provider (e.g., via LangChain).
- Add/Update AI prompt to respect language pairs instead of hardcoded EN-RU.
- Add a kebab menu to the overlay to delete/edit the current word.
- Track progress properties and show analytics charts.
- Use AI to analyze existing words, estimate level and suggest new vocabulary based on that info.
- On export, ask to keep progress for transfer or reset it for sharing the dack.
- Possibility to "buy" time beforehand in dojo.

### Long Term Roadmap
- Add user accounts with cloud backup (maybe even scheduled).
- Expand import options and improve Anki support.
- Add support for different decks so that user can choose words of which deck they want to study now.
- Add tests.
- Add an "add word" option to the OS text selection popup.
- Integrate with Gemini Assistant to add words by voice.
- Support rich text formatting for word/translation.
- Add pronunciation (TTS)
- Add some charts to view data of progress

### Installation
For now you have several options to choose:
1) Download the source code and build the app yourself
2) Download already built APK from github releases
3) Download from [F-Droid](https://f-droid.org/packages/com.procrastilearn.app/)
