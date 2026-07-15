package com.procrastilearn.app.data.sync

import com.procrastilearn.app.data.connectivity.NetworkConnectivityObserver
import com.procrastilearn.app.data.translation.TranslationErrorClassifier
import com.procrastilearn.app.domain.model.AiTranslationDirection
import com.procrastilearn.app.domain.model.PendingWord
import com.procrastilearn.app.domain.model.PendingWordStatus
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.repository.PendingWordRepository
import com.procrastilearn.app.domain.usecase.AddVocabularyItemUseCase
import com.procrastilearn.app.domain.usecase.GenerateAiTranslationUseCase
import com.procrastilearn.app.domain.usecase.GetVocabularyItemByWordUseCase
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PendingWordSyncManagerTest {
    private val connectivityObserver: NetworkConnectivityObserver = mockk()
    private val pendingWordRepository: PendingWordRepository = mockk()
    private val generateAiTranslationUseCase: GenerateAiTranslationUseCase = mockk()
    private val addVocabularyItemUseCase: AddVocabularyItemUseCase = mockk()
    private val getVocabularyItemByWordUseCase: GetVocabularyItemByWordUseCase = mockk()
    private val errorClassifier = TranslationErrorClassifier()
    private val timeProvider = FakeTimeProvider(BASE_TIME)
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var onlineFlow: MutableStateFlow<Boolean>
    private lateinit var pendingWordsFlow: MutableStateFlow<List<PendingWord>>
    private lateinit var manager: PendingWordSyncManager

    @Before
    fun setUp() {
        onlineFlow = MutableStateFlow(true)
        pendingWordsFlow = MutableStateFlow(emptyList())
        every { connectivityObserver.observe() } returns onlineFlow
        every { pendingWordRepository.observePendingWords() } returns pendingWordsFlow
        manager =
            PendingWordSyncManager(
                connectivityObserver,
                pendingWordRepository,
                generateAiTranslationUseCase,
                addVocabularyItemUseCase,
                getVocabularyItemByWordUseCase,
                errorClassifier,
                timeProvider,
                testDispatcher,
            )
    }

    // --- syncPendingWords: eligibility filtering ---

    @Test
    fun `syncPendingWords generates translation, adds the word and clears the pending entry`() =
        runTest {
            val pending = PendingWord(id = 1, word = "Haus", direction = AiTranslationDirection.EN_TO_RU)
            coEvery { pendingWordRepository.getAllPendingWordsSnapshot() } returns listOf(pending)
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns null
            coEvery { generateAiTranslationUseCase.invoke("Haus", AiTranslationDirection.EN_TO_RU) } returns "House"
            coEvery { addVocabularyItemUseCase.invoke("Haus", "House") } returns Result.success(Unit)
            coEvery { pendingWordRepository.deletePendingWord(pending) } just Runs

            manager.syncPendingWords()

            coVerify(exactly = 1) { addVocabularyItemUseCase.invoke("Haus", "House") }
            coVerify(exactly = 1) { pendingWordRepository.deletePendingWord(pending) }
        }

    @Test
    fun `syncPendingWords drops the entry without generating when the word already exists`() =
        runTest {
            val pending = PendingWord(id = 1, word = "Haus", direction = AiTranslationDirection.EN_TO_RU)
            val existing = VocabularyItem(id = 10, word = "Haus", translation = "House", isNew = false)
            coEvery { pendingWordRepository.getAllPendingWordsSnapshot() } returns listOf(pending)
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns existing
            coEvery { pendingWordRepository.deletePendingWord(pending) } just Runs

            manager.syncPendingWords()

            coVerify(exactly = 0) { generateAiTranslationUseCase.invoke(any(), any()) }
            coVerify(exactly = 0) { addVocabularyItemUseCase.invoke(any(), any()) }
            coVerify(exactly = 1) { pendingWordRepository.deletePendingWord(pending) }
        }

    @Test
    fun `syncPendingWords skips items whose backoff has not elapsed yet`() =
        runTest {
            val notYetEligible =
                PendingWord(
                    id = 1,
                    word = "Haus",
                    direction = AiTranslationDirection.EN_TO_RU,
                    nextAttemptAt = BASE_TIME + 1,
                )
            coEvery { pendingWordRepository.getAllPendingWordsSnapshot() } returns listOf(notYetEligible)

            manager.syncPendingWords()

            coVerify(exactly = 0) { getVocabularyItemByWordUseCase.invoke(any()) }
            coVerify(exactly = 0) { generateAiTranslationUseCase.invoke(any(), any()) }
        }

    @Test
    fun `syncPendingWords processes items exactly at their nextAttemptAt boundary`() =
        runTest {
            val eligible =
                PendingWord(
                    id = 1,
                    word = "Haus",
                    direction = AiTranslationDirection.EN_TO_RU,
                    nextAttemptAt = BASE_TIME,
                )
            coEvery { pendingWordRepository.getAllPendingWordsSnapshot() } returns listOf(eligible)
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns null
            coEvery { generateAiTranslationUseCase.invoke("Haus", any()) } returns "House"
            coEvery { addVocabularyItemUseCase.invoke("Haus", "House") } returns Result.success(Unit)
            coEvery { pendingWordRepository.deletePendingWord(eligible) } just Runs

            manager.syncPendingWords()

            coVerify(exactly = 1) { generateAiTranslationUseCase.invoke("Haus", any()) }
        }

    @Test
    fun `syncPendingWords never auto-retries an item already marked failed`() =
        runTest {
            val failed =
                PendingWord(
                    id = 1,
                    word = "Haus",
                    direction = AiTranslationDirection.EN_TO_RU,
                    status = PendingWordStatus.FAILED,
                )
            coEvery { pendingWordRepository.getAllPendingWordsSnapshot() } returns listOf(failed)

            manager.syncPendingWords()

            coVerify(exactly = 0) { getVocabularyItemByWordUseCase.invoke(any()) }
            coVerify(exactly = 0) { generateAiTranslationUseCase.invoke(any(), any()) }
        }

    // --- syncPendingWords: failure classification and rescheduling ---

    @Test
    fun `syncPendingWords marks the item failed immediately on a permanent error`() =
        runTest {
            val pending = PendingWord(id = 1, word = "Haus", direction = AiTranslationDirection.EN_TO_RU)
            coEvery { pendingWordRepository.getAllPendingWordsSnapshot() } returns listOf(pending)
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns null
            coEvery { generateAiTranslationUseCase.invoke("Haus", any()) } throws
                IllegalArgumentException("Missing OpenAI API key")
            coEvery { pendingWordRepository.updatePendingWord(any()) } just Runs

            manager.syncPendingWords()

            coVerify(exactly = 0) { addVocabularyItemUseCase.invoke(any(), any()) }
            coVerify(exactly = 0) { pendingWordRepository.deletePendingWord(any()) }
            coVerify(exactly = 1) {
                pendingWordRepository.updatePendingWord(
                    match {
                        it.status == PendingWordStatus.FAILED &&
                            it.retryCount == 0 &&
                            it.lastError == "Missing OpenAI API key"
                    },
                )
            }
        }

    @Test
    fun `syncPendingWords reschedules with exponential backoff on a transient error`() =
        runTest {
            val pending = PendingWord(id = 1, word = "Haus", direction = AiTranslationDirection.EN_TO_RU)
            coEvery { pendingWordRepository.getAllPendingWordsSnapshot() } returns listOf(pending)
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns null
            coEvery { generateAiTranslationUseCase.invoke("Haus", any()) } throws IllegalStateException("network blip")
            coEvery { pendingWordRepository.updatePendingWord(any()) } just Runs

            manager.syncPendingWords()

            coVerify(exactly = 1) {
                pendingWordRepository.updatePendingWord(
                    match {
                        it.status == PendingWordStatus.PENDING &&
                            it.retryCount == 1 &&
                            it.nextAttemptAt == BASE_TIME + PendingWordSyncManager.BASE_BACKOFF_MS &&
                            it.lastError == "network blip"
                    },
                )
            }
        }

    @Test
    fun `syncPendingWords doubles the backoff on successive transient failures`() =
        runTest {
            val pending =
                PendingWord(id = 1, word = "Haus", direction = AiTranslationDirection.EN_TO_RU, retryCount = 1)
            coEvery { pendingWordRepository.getAllPendingWordsSnapshot() } returns listOf(pending)
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns null
            coEvery { generateAiTranslationUseCase.invoke("Haus", any()) } throws IllegalStateException("blip")
            coEvery { pendingWordRepository.updatePendingWord(any()) } just Runs

            manager.syncPendingWords()

            coVerify(exactly = 1) {
                pendingWordRepository.updatePendingWord(
                    match {
                        it.retryCount == 2 && it.nextAttemptAt == BASE_TIME + 2 * PendingWordSyncManager.BASE_BACKOFF_MS
                    },
                )
            }
        }

    @Test
    fun `syncPendingWords marks the item failed once max attempts are exhausted`() =
        runTest {
            val pending =
                PendingWord(
                    id = 1,
                    word = "Haus",
                    direction = AiTranslationDirection.EN_TO_RU,
                    retryCount = PendingWordSyncManager.MAX_ATTEMPTS - 1,
                )
            coEvery { pendingWordRepository.getAllPendingWordsSnapshot() } returns listOf(pending)
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns null
            coEvery { generateAiTranslationUseCase.invoke("Haus", any()) } throws IllegalStateException("blip")
            coEvery { pendingWordRepository.updatePendingWord(any()) } just Runs

            manager.syncPendingWords()

            coVerify(exactly = 1) {
                pendingWordRepository.updatePendingWord(
                    match {
                        it.status == PendingWordStatus.FAILED && it.retryCount == PendingWordSyncManager.MAX_ATTEMPTS
                    },
                )
            }
        }

    @Test
    fun `syncPendingWords reschedules as transient when adding the vocabulary item fails`() =
        runTest {
            val pending = PendingWord(id = 1, word = "Haus", direction = AiTranslationDirection.EN_TO_RU)
            coEvery { pendingWordRepository.getAllPendingWordsSnapshot() } returns listOf(pending)
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns null
            coEvery { generateAiTranslationUseCase.invoke("Haus", AiTranslationDirection.EN_TO_RU) } returns "House"
            coEvery { addVocabularyItemUseCase.invoke("Haus", "House") } returns
                Result.failure(IllegalStateException("db busy"))
            coEvery { pendingWordRepository.updatePendingWord(any()) } just Runs

            manager.syncPendingWords()

            coVerify(exactly = 0) { pendingWordRepository.deletePendingWord(any()) }
            coVerify(exactly = 1) {
                pendingWordRepository.updatePendingWord(match { it.status == PendingWordStatus.PENDING })
            }
        }

    @Test
    fun `syncPendingWords reschedules as transient when the generated translation is blank`() =
        runTest {
            val pending = PendingWord(id = 1, word = "Haus", direction = AiTranslationDirection.EN_TO_RU)
            coEvery { pendingWordRepository.getAllPendingWordsSnapshot() } returns listOf(pending)
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns null
            coEvery { generateAiTranslationUseCase.invoke("Haus", AiTranslationDirection.EN_TO_RU) } returns "   "
            coEvery { pendingWordRepository.updatePendingWord(any()) } just Runs

            manager.syncPendingWords()

            coVerify(exactly = 0) { addVocabularyItemUseCase.invoke(any(), any()) }
            coVerify(exactly = 0) { pendingWordRepository.deletePendingWord(any()) }
            coVerify(exactly = 1) {
                pendingWordRepository.updatePendingWord(match { it.status == PendingWordStatus.PENDING })
            }
        }

    @Test
    fun `syncPendingWords keeps processing remaining words after one fails`() =
        runTest {
            val failing = PendingWord(id = 1, word = "Fail", direction = AiTranslationDirection.EN_TO_RU)
            val succeeding = PendingWord(id = 2, word = "Haus", direction = AiTranslationDirection.EN_TO_RU)
            coEvery { pendingWordRepository.getAllPendingWordsSnapshot() } returns listOf(failing, succeeding)
            coEvery { getVocabularyItemByWordUseCase.invoke("Fail") } returns null
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns null
            coEvery { generateAiTranslationUseCase.invoke("Fail", any()) } throws IllegalStateException("boom")
            coEvery { generateAiTranslationUseCase.invoke("Haus", any()) } returns "House"
            coEvery { addVocabularyItemUseCase.invoke("Haus", "House") } returns Result.success(Unit)
            coEvery { pendingWordRepository.deletePendingWord(succeeding) } just Runs
            coEvery { pendingWordRepository.updatePendingWord(any()) } just Runs

            manager.syncPendingWords()

            coVerify(exactly = 1) { pendingWordRepository.deletePendingWord(succeeding) }
            coVerify(exactly = 0) { pendingWordRepository.deletePendingWord(failing) }
            coVerify(exactly = 1) { pendingWordRepository.updatePendingWord(match { it.id == 1L }) }
        }

    // --- start(): polling behavior ---
    //
    // These tests give the manager its own TestCoroutineScheduler, deliberately NOT shared with
    // runTest's scheduler, and drive it directly via testDispatcher.scheduler. manager.start()
    // launches an infinite polling loop (by design, for as long as the app process is alive);
    // if it shared runTest's scheduler, runTest's implicit end-of-test drain would try to
    // exhaust that infinite loop and hang/OOM. Driving a separate scheduler by hand avoids that
    // entirely and still gives full deterministic control over virtual time.

    @Test
    fun `start syncs immediately when already online with pending words`() =
        runTest {
            val pending = PendingWord(id = 1, word = "Haus", direction = AiTranslationDirection.EN_TO_RU)
            pendingWordsFlow.value = listOf(pending)
            onlineFlow.value = true
            coEvery { pendingWordRepository.getAllPendingWordsSnapshot() } returns listOf(pending)
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns null
            coEvery { generateAiTranslationUseCase.invoke("Haus", any()) } returns "House"
            coEvery { addVocabularyItemUseCase.invoke("Haus", "House") } returns Result.success(Unit)
            coEvery { pendingWordRepository.deletePendingWord(pending) } just Runs

            manager.start()
            testDispatcher.scheduler.runCurrent()

            coVerify(exactly = 1) { addVocabularyItemUseCase.invoke("Haus", "House") }
        }

    @Test
    fun `start polls again after the interval while still online with pending items`() =
        runTest {
            val pending = PendingWord(id = 1, word = "Haus", direction = AiTranslationDirection.EN_TO_RU)
            pendingWordsFlow.value = listOf(pending)
            onlineFlow.value = true
            coEvery { pendingWordRepository.getAllPendingWordsSnapshot() } returns listOf(pending)
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns null
            coEvery { generateAiTranslationUseCase.invoke("Haus", any()) } throws IllegalStateException("boom")
            coEvery { pendingWordRepository.updatePendingWord(any()) } just Runs

            manager.start()
            testDispatcher.scheduler.runCurrent()
            coVerify(exactly = 1) { generateAiTranslationUseCase.invoke("Haus", any()) }

            testDispatcher.scheduler.advanceTimeBy(PendingWordSyncManager.POLL_INTERVAL_MS)
            testDispatcher.scheduler.runCurrent()

            coVerify(exactly = 2) { generateAiTranslationUseCase.invoke("Haus", any()) }
        }

    @Test
    fun `start does not sync while offline`() =
        runTest {
            val pending = PendingWord(id = 1, word = "Haus", direction = AiTranslationDirection.EN_TO_RU)
            pendingWordsFlow.value = listOf(pending)
            onlineFlow.value = false
            coEvery { pendingWordRepository.getAllPendingWordsSnapshot() } returns listOf(pending)

            manager.start()
            testDispatcher.scheduler.runCurrent()

            coVerify(exactly = 0) { generateAiTranslationUseCase.invoke(any(), any()) }
        }

    @Test
    fun `start does not poll when there are no pending words`() =
        runTest {
            pendingWordsFlow.value = emptyList()
            onlineFlow.value = true

            manager.start()
            testDispatcher.scheduler.runCurrent()

            coVerify(exactly = 0) { pendingWordRepository.getAllPendingWordsSnapshot() }
        }

    @Test
    fun `start is idempotent when called twice`() =
        runTest {
            val pending = PendingWord(id = 1, word = "Haus", direction = AiTranslationDirection.EN_TO_RU)
            coEvery { pendingWordRepository.getAllPendingWordsSnapshot() } returns listOf(pending)
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns null
            coEvery { generateAiTranslationUseCase.invoke("Haus", any()) } returns "House"
            coEvery { addVocabularyItemUseCase.invoke("Haus", "House") } returns Result.success(Unit)
            coEvery { pendingWordRepository.deletePendingWord(pending) } just Runs

            manager.start()
            manager.start()
            pendingWordsFlow.value = listOf(pending)
            onlineFlow.value = true
            testDispatcher.scheduler.runCurrent()

            coVerify(exactly = 1) { addVocabularyItemUseCase.invoke("Haus", "House") }
        }

    @Test
    fun `start resyncs promptly when the pending list changes without waiting for the poll interval`() =
        runTest {
            val first = PendingWord(id = 1, word = "Haus", direction = AiTranslationDirection.EN_TO_RU)
            val second = PendingWord(id = 2, word = "Auto", direction = AiTranslationDirection.EN_TO_RU)
            pendingWordsFlow.value = listOf(first)
            onlineFlow.value = true
            coEvery { pendingWordRepository.getAllPendingWordsSnapshot() } returns listOf(first)
            coEvery { getVocabularyItemByWordUseCase.invoke(any()) } returns null
            coEvery { generateAiTranslationUseCase.invoke("Haus", any()) } throws IllegalStateException("boom")
            coEvery { generateAiTranslationUseCase.invoke("Auto", any()) } returns "Car"
            coEvery { addVocabularyItemUseCase.invoke("Auto", "Car") } returns Result.success(Unit)
            coEvery { pendingWordRepository.updatePendingWord(any()) } just Runs
            coEvery { pendingWordRepository.deletePendingWord(second) } just Runs

            manager.start()
            testDispatcher.scheduler.runCurrent()
            coVerify(exactly = 1) { generateAiTranslationUseCase.invoke("Haus", any()) }

            coEvery { pendingWordRepository.getAllPendingWordsSnapshot() } returns listOf(first, second)
            pendingWordsFlow.value = listOf(first, second)
            testDispatcher.scheduler.advanceTimeBy(1_000L)
            testDispatcher.scheduler.runCurrent()

            coVerify(exactly = 1) { addVocabularyItemUseCase.invoke("Auto", "Car") }
        }

    @Test
    fun `start stops polling once connectivity drops`() =
        runTest {
            val pending = PendingWord(id = 1, word = "Haus", direction = AiTranslationDirection.EN_TO_RU)
            pendingWordsFlow.value = listOf(pending)
            onlineFlow.value = true
            coEvery { pendingWordRepository.getAllPendingWordsSnapshot() } returns listOf(pending)
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns null
            coEvery { generateAiTranslationUseCase.invoke("Haus", any()) } throws IllegalStateException("boom")
            coEvery { pendingWordRepository.updatePendingWord(any()) } just Runs

            manager.start()
            testDispatcher.scheduler.runCurrent()
            coVerify(exactly = 1) { generateAiTranslationUseCase.invoke("Haus", any()) }

            onlineFlow.value = false
            testDispatcher.scheduler.advanceTimeBy(PendingWordSyncManager.POLL_INTERVAL_MS * 3)
            testDispatcher.scheduler.runCurrent()

            coVerify(exactly = 1) { generateAiTranslationUseCase.invoke("Haus", any()) }
        }

    private class FakeTimeProvider(
        var currentTime: Long,
    ) : TimeProvider {
        override fun now(): Long = currentTime
    }

    private companion object {
        const val BASE_TIME = 1_000_000L
    }
}
