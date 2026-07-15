package com.procrastilearn.app.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.utils.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Locale

/**
 * [kotlinx.coroutines.test.TestCoroutineScheduler.advanceUntilIdle] fast-forwards through *any*
 * pending delay, including the speaker's 60s idle-shutdown timer - so these tests use
 * [runCurrent] to flush only the zero-delay init-callback dispatch, and reserve explicit
 * [advanceTimeBy] for the tests that actually exercise the idle timeout.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidTtsSpeakerTest {
    private companion object {
        const val IDLE_TIMEOUT_MS = 60_000L
    }

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val context: Context = mockk(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun buildSpeaker(
        engine: TextToSpeech,
        onListenerCaptured: (TextToSpeech.OnInitListener) -> Unit,
    ): AndroidTtsSpeaker {
        val speaker = AndroidTtsSpeaker(context)
        speaker.engineFactory = { _, listener ->
            onListenerCaptured(listener)
            engine
        }
        return speaker
    }

    private fun readyEngine(): TextToSpeech {
        val engine = mockk<TextToSpeech>(relaxed = true)
        every { engine.setLanguage(any()) } returns TextToSpeech.LANG_AVAILABLE
        return engine
    }

    @Test
    fun `speak before init completes queues the utterance`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val engine = readyEngine()
            var listener: TextToSpeech.OnInitListener? = null
            val speaker = buildSpeaker(engine) { listener = it }

            speaker.speak("hello", Locale.ENGLISH)
            runCurrent()

            verify(exactly = 0) { engine.speak(any(), any(), any(), any()) }

            listener?.onInit(TextToSpeech.SUCCESS)
            runCurrent()

            verify(exactly = 1) { engine.setLanguage(Locale.ENGLISH) }
            verify(exactly = 1) { engine.speak("hello", TextToSpeech.QUEUE_FLUSH, null, any()) }
        }

    @Test
    fun `speak only flushes the most recent pending utterance`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val engine = readyEngine()
            var listener: TextToSpeech.OnInitListener? = null
            val speaker = buildSpeaker(engine) { listener = it }

            speaker.speak("first", Locale.ENGLISH)
            speaker.speak("second", Locale.ENGLISH)
            runCurrent()

            listener?.onInit(TextToSpeech.SUCCESS)
            runCurrent()

            verify(exactly = 0) { engine.speak("first", any(), any(), any()) }
            verify(exactly = 1) { engine.speak("second", TextToSpeech.QUEUE_FLUSH, null, any()) }
        }

    @Test
    fun `speak on an already-ready engine speaks immediately without re-init`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val engine = readyEngine()
            var listener: TextToSpeech.OnInitListener? = null
            var factoryCalls = 0
            val speaker = AndroidTtsSpeaker(context)
            speaker.engineFactory = { _, l ->
                factoryCalls++
                listener = l
                engine
            }

            speaker.speak("warm-up", Locale.ENGLISH)
            runCurrent()
            listener?.onInit(TextToSpeech.SUCCESS)
            runCurrent()

            speaker.speak("second call", Locale.ENGLISH)
            runCurrent()

            assertThat(factoryCalls).isEqualTo(1)
            verify(exactly = 1) { engine.speak("second call", TextToSpeech.QUEUE_FLUSH, null, any()) }
        }

    @Test
    fun `init failure drops the pending utterance and retries engine creation on next speak`() =
        runTest(mainDispatcherRule.testDispatcher) {
            var factoryCalls = 0
            val failingEngine = mockk<TextToSpeech>(relaxed = true)
            val workingEngine = readyEngine()
            var listener: TextToSpeech.OnInitListener? = null
            val speaker = AndroidTtsSpeaker(context)
            speaker.engineFactory = { _, l ->
                factoryCalls++
                listener = l
                if (factoryCalls == 1) failingEngine else workingEngine
            }

            speaker.speak("hello", Locale.ENGLISH)
            runCurrent()
            listener?.onInit(TextToSpeech.ERROR)
            runCurrent()

            verify(exactly = 0) { failingEngine.speak(any(), any(), any(), any()) }

            // A later tap should retry initialization rather than staying stuck forever.
            speaker.speak("hello again", Locale.ENGLISH)
            runCurrent()
            listener?.onInit(TextToSpeech.SUCCESS)
            runCurrent()

            assertThat(factoryCalls).isEqualTo(2)
            verify(exactly = 1) { workingEngine.speak("hello again", TextToSpeech.QUEUE_FLUSH, null, any()) }
        }

    @Test
    fun `unsupported locale does not invoke engine speak`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val engine = mockk<TextToSpeech>(relaxed = true)
            every { engine.setLanguage(any()) } returns TextToSpeech.LANG_NOT_SUPPORTED
            var listener: TextToSpeech.OnInitListener? = null
            val speaker = buildSpeaker(engine) { listener = it }

            speaker.speak("bonjour", Locale.FRENCH)
            runCurrent()
            listener?.onInit(TextToSpeech.SUCCESS)
            runCurrent()

            verify(exactly = 1) { engine.setLanguage(Locale.FRENCH) }
            verify(exactly = 0) { engine.speak(any(), any(), any(), any()) }
        }

    @Test
    fun `missing language data does not invoke engine speak`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val engine = mockk<TextToSpeech>(relaxed = true)
            every { engine.setLanguage(any()) } returns TextToSpeech.LANG_MISSING_DATA
            var listener: TextToSpeech.OnInitListener? = null
            val speaker = buildSpeaker(engine) { listener = it }

            speaker.speak("bonjour", Locale.FRENCH)
            runCurrent()
            listener?.onInit(TextToSpeech.SUCCESS)
            runCurrent()

            verify(exactly = 0) { engine.speak(any(), any(), any(), any()) }
        }

    @Test
    fun `idle timeout shuts down the engine automatically`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val engine = readyEngine()
            var listener: TextToSpeech.OnInitListener? = null
            val speaker = buildSpeaker(engine) { listener = it }

            speaker.speak("hello", Locale.ENGLISH)
            runCurrent()
            listener?.onInit(TextToSpeech.SUCCESS)
            runCurrent()

            advanceTimeBy(IDLE_TIMEOUT_MS + 1_000L)
            runCurrent()

            verify(exactly = 1) { engine.stop() }
            verify(exactly = 1) { engine.shutdown() }
        }

    @Test
    fun `repeated speak calls reset the idle timer`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val engine = readyEngine()
            var listener: TextToSpeech.OnInitListener? = null
            val speaker = buildSpeaker(engine) { listener = it }

            speaker.speak("hello", Locale.ENGLISH)
            runCurrent()
            listener?.onInit(TextToSpeech.SUCCESS)
            runCurrent()

            // Tap again just before the idle window would expire.
            advanceTimeBy(IDLE_TIMEOUT_MS - 1_000L)
            speaker.speak("hello", Locale.ENGLISH)
            runCurrent()

            // Original deadline has passed, but the timer was reset - engine must still be alive.
            advanceTimeBy(2_000L)
            runCurrent()
            verify(exactly = 0) { engine.shutdown() }

            // Now let the reset timer actually expire.
            advanceTimeBy(IDLE_TIMEOUT_MS)
            runCurrent()
            verify(exactly = 1) { engine.shutdown() }
        }

    @Test
    fun `explicit shutdown releases the engine and next speak re-initializes`() =
        runTest(mainDispatcherRule.testDispatcher) {
            var factoryCalls = 0
            val firstEngine = readyEngine()
            val secondEngine = readyEngine()
            var listener: TextToSpeech.OnInitListener? = null
            val speaker = AndroidTtsSpeaker(context)
            speaker.engineFactory = { _, l ->
                factoryCalls++
                listener = l
                if (factoryCalls == 1) firstEngine else secondEngine
            }

            speaker.speak("hello", Locale.ENGLISH)
            runCurrent()
            listener?.onInit(TextToSpeech.SUCCESS)
            runCurrent()

            speaker.shutdown()

            verify(exactly = 1) { firstEngine.stop() }
            verify(exactly = 1) { firstEngine.shutdown() }

            speaker.speak("hello again", Locale.ENGLISH)
            runCurrent()
            listener?.onInit(TextToSpeech.SUCCESS)
            runCurrent()

            assertThat(factoryCalls).isEqualTo(2)
            verify(exactly = 1) { secondEngine.speak("hello again", TextToSpeech.QUEUE_FLUSH, null, any()) }
        }

    @Test
    fun `shutdown is safe to call before any speak`() {
        val speaker = AndroidTtsSpeaker(context)

        speaker.shutdown()
        speaker.shutdown()
    }
}
