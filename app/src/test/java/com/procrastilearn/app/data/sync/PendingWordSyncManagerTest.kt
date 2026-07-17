package com.procrastilearn.app.data.sync

import com.procrastilearn.app.data.connectivity.NetworkConnectivityObserver
import com.procrastilearn.app.domain.model.AiTranslationDirection
import com.procrastilearn.app.domain.model.PendingWord
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.repository.PendingWordRepository
import com.procrastilearn.app.domain.usecase.AddVocabularyItemUseCase
import com.procrastilearn.app.domain.usecase.GenerateAiTranslationUseCase
import com.procrastilearn.app.domain.usecase.GetVocabularyItemByWordUseCase
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class PendingWordSyncManagerTest {
    private val connectivityObserver: NetworkConnectivityObserver = mockk()
    private val pendingWordRepository: PendingWordRepository = mockk()
    private val generateAiTranslationUseCase: GenerateAiTranslationUseCase = mockk()
    private val addVocabularyItemUseCase: AddVocabularyItemUseCase = mockk()
    private val getVocabularyItemByWordUseCase: GetVocabularyItemByWordUseCase = mockk()
    private lateinit var manager: PendingWordSyncManager

    @Before
    fun setUp() {
        manager =
            PendingWordSyncManager(
                connectivityObserver,
                pendingWordRepository,
                generateAiTranslationUseCase,
                addVocabularyItemUseCase,
                getVocabularyItemByWordUseCase,
            )
    }

    @Test
    fun `syncPendingWords generates translation, adds the word and clears the pending entry`() =
        runTest {
            val pending = PendingWord(id = 1, word = "Haus", direction = AiTranslationDirection.TARGET_TO_NATIVE)
            coEvery { pendingWordRepository.getAllPendingWordsSnapshot() } returns listOf(pending)
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns null
            coEvery { generateAiTranslationUseCase.invoke("Haus", AiTranslationDirection.TARGET_TO_NATIVE) } returns "House"
            coEvery { addVocabularyItemUseCase.invoke("Haus", "House") } returns Result.success(Unit)
            coEvery { pendingWordRepository.deletePendingWord(pending) } just Runs

            manager.syncPendingWords()

            coVerify(exactly = 1) { addVocabularyItemUseCase.invoke("Haus", "House") }
            coVerify(exactly = 1) { pendingWordRepository.deletePendingWord(pending) }
        }

    @Test
    fun `syncPendingWords drops the entry without generating when the word already exists`() =
        runTest {
            val pending = PendingWord(id = 1, word = "Haus", direction = AiTranslationDirection.TARGET_TO_NATIVE)
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
    fun `syncPendingWords leaves the word pending when translation generation fails`() =
        runTest {
            val pending = PendingWord(id = 1, word = "Haus", direction = AiTranslationDirection.TARGET_TO_NATIVE)
            coEvery { pendingWordRepository.getAllPendingWordsSnapshot() } returns listOf(pending)
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns null
            coEvery { generateAiTranslationUseCase.invoke(any(), any()) } throws IllegalStateException("network blip")

            manager.syncPendingWords()

            coVerify(exactly = 0) { addVocabularyItemUseCase.invoke(any(), any()) }
            coVerify(exactly = 0) { pendingWordRepository.deletePendingWord(any()) }
        }

    @Test
    fun `syncPendingWords leaves the word pending when adding it fails`() =
        runTest {
            val pending = PendingWord(id = 1, word = "Haus", direction = AiTranslationDirection.TARGET_TO_NATIVE)
            coEvery { pendingWordRepository.getAllPendingWordsSnapshot() } returns listOf(pending)
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns null
            coEvery { generateAiTranslationUseCase.invoke("Haus", AiTranslationDirection.TARGET_TO_NATIVE) } returns "House"
            coEvery { addVocabularyItemUseCase.invoke("Haus", "House") } returns
                Result.failure(IllegalStateException("db busy"))

            manager.syncPendingWords()

            coVerify(exactly = 0) { pendingWordRepository.deletePendingWord(any()) }
        }

    @Test
    fun `syncPendingWords leaves the word pending when the generated translation is blank`() =
        runTest {
            val pending = PendingWord(id = 1, word = "Haus", direction = AiTranslationDirection.TARGET_TO_NATIVE)
            coEvery { pendingWordRepository.getAllPendingWordsSnapshot() } returns listOf(pending)
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns null
            coEvery { generateAiTranslationUseCase.invoke("Haus", AiTranslationDirection.TARGET_TO_NATIVE) } returns "   "

            manager.syncPendingWords()

            coVerify(exactly = 0) { addVocabularyItemUseCase.invoke(any(), any()) }
            coVerify(exactly = 0) { pendingWordRepository.deletePendingWord(any()) }
        }

    @Test
    fun `syncPendingWords keeps processing remaining words after one fails`() =
        runTest {
            val failing = PendingWord(id = 1, word = "Fail", direction = AiTranslationDirection.TARGET_TO_NATIVE)
            val succeeding = PendingWord(id = 2, word = "Haus", direction = AiTranslationDirection.TARGET_TO_NATIVE)
            coEvery { pendingWordRepository.getAllPendingWordsSnapshot() } returns listOf(failing, succeeding)
            coEvery { getVocabularyItemByWordUseCase.invoke("Fail") } returns null
            coEvery { getVocabularyItemByWordUseCase.invoke("Haus") } returns null
            coEvery { generateAiTranslationUseCase.invoke("Fail", any()) } throws IllegalStateException("boom")
            coEvery { generateAiTranslationUseCase.invoke("Haus", any()) } returns "House"
            coEvery { addVocabularyItemUseCase.invoke("Haus", "House") } returns Result.success(Unit)
            coEvery { pendingWordRepository.deletePendingWord(succeeding) } just Runs

            manager.syncPendingWords()

            coVerify(exactly = 1) { pendingWordRepository.deletePendingWord(succeeding) }
            coVerify(exactly = 0) { pendingWordRepository.deletePendingWord(failing) }
        }
}
