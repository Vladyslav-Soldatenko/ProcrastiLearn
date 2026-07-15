package com.procrastilearn.app.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.annotation.VisibleForTesting
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps a single, lazily-created [TextToSpeech] engine binding. All public methods are expected
 * to be called from the main thread (matches viewModelScope's default dispatcher), so the
 * internal state isn't guarded beyond that confinement.
 */
@Singleton
class AndroidTtsSpeaker
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : Speaker {
        private companion object {
            const val TAG = "AndroidTtsSpeaker"
            const val IDLE_TIMEOUT_MS = 60_000L
        }

        @VisibleForTesting
        internal var engineFactory: (Context, TextToSpeech.OnInitListener) -> TextToSpeech =
            { ctx, listener -> TextToSpeech(ctx, listener) }

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        private var engine: TextToSpeech? = null
        private var isReady = false
        private var pendingUtterance: PendingUtterance? = null
        private var idleShutdownJob: Job? = null

        private data class PendingUtterance(
            val text: String,
            val locale: Locale,
        )

        override fun speak(
            text: String,
            locale: Locale,
        ) {
            armIdleShutdown()

            val currentEngine = engine
            when {
                currentEngine == null -> {
                    pendingUtterance = PendingUtterance(text, locale)
                    initEngine()
                }
                !isReady -> pendingUtterance = PendingUtterance(text, locale)
                else -> speakNow(currentEngine, text, locale)
            }
        }

        private fun initEngine() {
            if (engine != null) return
            engine =
                engineFactory(context) { status ->
                    scope.launch { onInitResult(status) }
                }
        }

        private fun onInitResult(status: Int) {
            val currentEngine = engine
            if (status != TextToSpeech.SUCCESS || currentEngine == null) {
                Log.w(TAG, "TextToSpeech init failed with status=$status")
                isReady = false
                pendingUtterance = null
                // Reset so the next speak() retries initialization instead of silently
                // queuing utterances that would never be flushed.
                currentEngine?.shutdown()
                engine = null
                return
            }

            isReady = true
            pendingUtterance?.let { pending ->
                pendingUtterance = null
                speakNow(currentEngine, pending.text, pending.locale)
            }
        }

        private fun speakNow(
            engine: TextToSpeech,
            text: String,
            locale: Locale,
        ) {
            val languageResult = engine.setLanguage(locale)
            if (languageResult == TextToSpeech.LANG_MISSING_DATA ||
                languageResult == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                Log.w(TAG, "Locale $locale not supported by TTS engine, result=$languageResult")
                return
            }
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
        }

        private fun armIdleShutdown() {
            idleShutdownJob?.cancel()
            idleShutdownJob =
                scope.launch {
                    delay(IDLE_TIMEOUT_MS)
                    shutdown()
                }
        }

        override fun shutdown() {
            idleShutdownJob?.cancel()
            idleShutdownJob = null
            pendingUtterance = null
            isReady = false
            engine?.let {
                it.stop()
                it.shutdown()
            }
            engine = null
        }
    }
