# ProcrastiLearn

<a href="https://play.google.com/store/apps/details?id=com.procrastilearn.app"><img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="40"></a>
<a href="https://f-droid.org/packages/com.procrastilearn.app/"><img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="40"></a>

There's Anki on your phone, there's Duolingo, and you do mean to get around to them. Then you pick the phone up, and twenty minutes later you're somewhere in TikTok, not really sure how you got there. ProcrastiLearn steps in right at that moment and asks you to review before continuing.

ProcrastiLearn is an Android (Kotlin + Jetpack Compose) app that turns distracting moments into spaced-repetition reps. Pick the apps you tend to open mindlessly; when you launch one, a full-screen overlay asks you to review a configurable number of cards before you continue. Optional OpenAI-powered translation suggestions (your API key required) make adding new words faster.

## How It Works

- Select apps to gate in `Apps`. Opening one starts a study session when cards are available.
- Recall each card, reveal its answer, and rate it Again, Hard, Good, or Easy. After the configured number of cards, the app you opened comes back to the front.
- Set an optional interval to bring the overlay back while you remain in a gated app. You can also study without opening another app in the `Dojo` tab.
- FSRS schedules reviews. Daily new/review limits, card order, mix mode, and study direction control which cards appear.

## Features

- 📱 Gate selected apps using Accessibility and overlay permissions; choose how many cards to review per gate.
- 🧠 Practice in the Dojo, check today's available cards, and undo a rating. Study cards forward, backward, or in both directions.
- ➕ Add words manually or request an AI translation with your OpenAI key, language pair, and editable prompts. If you're offline, you can queue a word for translation when connectivity returns. Currently GPT-5.6-luna is used, which means that you can add hundreds of words for a few cents.
- 📂 Import Anki `.apkg` decks or ProcrastiLearn JSON exports; export your vocabulary and study progress to JSON.
- 📋 Search, reorder, edit, delete, and reset progress in the word list. Select text in another app and choose “ProcrastiLearn this” to bring it into Add Word.
- ⚙️ Configure daily limits, study order, gate cooldown, repeated-gate interval, and a master switch for gating.

## User Setup
1) Permissions: grant overlay (“draw over other apps”) and Accessibility when prompted on first launch. Accessibility permission is needed only to check what app is currently in foreground.
2) Pick apps to gate: `Apps` tab → toggle the packages you want blocked. Use the master switch to pause/enable ProcrastiLearn.
3) Add vocabulary: `Add Word` tab → enter a word and translation, or use AI translation after adding your OpenAI API key in Settings. You can also import a deck or JSON export in Settings.
4) Practice: open a gated app → recall the card → tap “Show translation” → rate it. Repeat until the gate is complete, or open `Dojo` to study directly. When no cards are available under your current limits, a new gate does not appear.

## Privacy
Blocked apps, vocabulary, study progress, and preferences are stored on-device. There are no analytics or ads. AI translation is optional; when used, the app sends the word and your configured prompt to OpenAI using your API key.

## Roadmap
- UI polish, improve color scheme and UX where needed.
- Auto-delete a word after a good streak.
- Stop TikTok media playback (or mute it at least) during gating; other apps mostly work.
- Let users choose GPT model or provider (e.g., via LangChain).
- Track progress properties and show analytics charts.
- Use AI to analyze existing words, estimate level and suggest new vocabulary based on that info.
- Offer an option to reset study progress when exporting a deck for sharing.
- Possibility to "buy" time beforehand in dojo.

### Long Term Roadmap
- Add user accounts with cloud backup (maybe even scheduled).
- Add support for different decks so that user can choose words of which deck they want to study now.
- Integrate with Gemini Assistant to add words by voice.
- Support rich text formatting for word/translation.
- Add pronunciation (TTS)
- Add some charts to view data of progress
- Generation of words with AI for given topic

### Installation
For now you have several options to choose:
1) Download the source code and build the app yourself
2) Download already built APK from github releases
3) Download from [F-Droid](https://f-droid.org/packages/com.procrastilearn.app/)
4) Download from [Google Play](https://play.google.com/store/apps/details?id=com.procrastilearn.app)
