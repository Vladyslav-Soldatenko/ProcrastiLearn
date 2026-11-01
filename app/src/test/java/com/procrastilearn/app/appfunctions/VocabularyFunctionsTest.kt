package com.procrastilearn.app.appfunctions

import android.annotation.SuppressLint
import android.app.Application
import androidx.appfunctions.AppFunctionAppUnknownException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.local.prefs.LanguagePreferencesStore
import com.procrastilearn.app.data.local.prefs.OpenAiPreferencesStore
import com.procrastilearn.app.domain.model.AiTranslationDirection
import com.procrastilearn.app.domain.model.Language
import com.procrastilearn.app.domain.model.LanguagePair
import com.procrastilearn.app.domain.repository.VocabularyCatalogRepository
import com.procrastilearn.app.domain.usecase.GenerateAiTranslationUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
@SuppressLint("NewApi", "InlinedApi")
class VocabularyFunctionsTest {
    private val repository = mockk<VocabularyCatalogRepository>()
    private val settings = mockk<OpenAiPreferencesStore>()
    private val languageSettings = mockk<LanguagePreferencesStore>()
    private val generator = mockk<GenerateAiTranslationUseCase>()
    private lateinit var service: VocabularyAppFunctionService

    @Before
    fun setup() {
        service =
            VocabularyAppFunctionService().apply {
                repository = this@VocabularyFunctionsTest.repository
                openAiStore = settings
                languageStore = languageSettings
                generator = this@VocabularyFunctionsTest.generator
                ioDispatcher = Dispatchers.Unconfined
            }
        coEvery { repository.getVocabularyItemByWord(any()) } returns null
        coEvery { repository.addVocabularyItemIfAbsent(any()) } returns true
        every { languageSettings.readLanguagePair() } returns flowOf(LanguagePair(Language.ENGLISH, Language.UKRAINIAN))
    }

    @Test
    fun `blank word is rejected before lookup`() =
        runTest {
            assertThrows(AppFunctionInvalidArgumentException::class.java) { runBlocking { service.addWord("  ", "x") } }
            coVerify(exactly = 0) { repository.getVocabularyItemByWord(any()) }
        }

    @Test
    fun `manual translation is trimmed and skips AI`() =
        runTest {
            assertThat(service.addWord(" word ", " meaning ")).isEqualTo("Added \"word\" to your learning list.")
            coVerify {
                repository.addVocabularyItemIfAbsent(
                    match { it.word == "word" && it.translation == "meaning" },
                )
            }
            coVerify(exactly = 0) { generator(any(), any()) }
        }

    @Test
    fun `duplicate skips generation and persistence`() =
        runTest {
            coEvery { repository.getVocabularyItemByWord("word") } returns mockk()
            assertThat(service.addWord(" word ")).contains("already exists")
            coVerify(exactly = 0) { repository.addVocabularyItemIfAbsent(any()) }
            coVerify(exactly = 0) { generator(any(), any()) }
        }

    @Test
    fun `AI uses selected direction and trims output`() =
        runTest {
            every { settings.readUseAiForTranslation() } returns flowOf(true)
            every { settings.readOpenAiApiKey() } returns flowOf("key")
            for (direction in AiTranslationDirection.entries) {
                every { settings.readAiTranslationDirection() } returns flowOf(direction)
                coEvery { generator("word", direction) } returns " meaning "
                service.addWord(" word ")
                coVerify { generator("word", direction) }
            }
            coVerify(exactly = 2) { repository.addVocabularyItemIfAbsent(match { it.translation == "meaning" }) }
        }

    @Test
    fun `unavailable AI and missing language pair give configuration error`() =
        runTest {
            every { settings.readUseAiForTranslation() } returns flowOf(false)
            every { settings.readOpenAiApiKey() } returns flowOf(null)
            assertThrows(AppFunctionInvalidArgumentException::class.java) { runBlocking { service.addWord("word") } }
            every { settings.readUseAiForTranslation() } returns flowOf(true)
            every { settings.readOpenAiApiKey() } returns flowOf("key")
            every { languageSettings.readLanguagePair() } returns flowOf(null)
            assertThrows(AppFunctionInvalidArgumentException::class.java) { runBlocking { service.addWord("word") } }
            coVerify(exactly = 0) { repository.addVocabularyItemIfAbsent(any()) }
        }

    @Test
    fun `provider failures are sanitized`() =
        runTest {
            every { settings.readUseAiForTranslation() } returns flowOf(true)
            every { settings.readOpenAiApiKey() } returns flowOf("key")
            every { settings.readAiTranslationDirection() } returns flowOf(AiTranslationDirection.TARGET_TO_NATIVE)
            coEvery { generator(any(), any()) } throws IllegalStateException("secret")
            val error =
                assertThrows(AppFunctionAppUnknownException::class.java) { runBlocking { service.addWord("word") } }
            assertThat(error.message).doesNotContain("secret")
            coEvery { generator(any(), any()) } returns "  "
            assertThrows(AppFunctionAppUnknownException::class.java) { runBlocking { service.addWord("word") } }
            coVerify(exactly = 0) { repository.addVocabularyItemIfAbsent(any()) }
        }

    @Test
    fun `cancellation propagates without insertion`() =
        runTest {
            coEvery { repository.getVocabularyItemByWord(any()) } throws CancellationException("cancel")
            assertThrows(CancellationException::class.java) { runBlocking { service.addWord("word", "meaning") } }
            coEvery { repository.getVocabularyItemByWord(any()) } returns null
            every { settings.readUseAiForTranslation() } throws CancellationException("cancel")
            assertThrows(CancellationException::class.java) { runBlocking { service.addWord("word") } }
            every { settings.readUseAiForTranslation() } returns flowOf(true)
            every { settings.readOpenAiApiKey() } returns flowOf("key")
            every { settings.readAiTranslationDirection() } returns flowOf(AiTranslationDirection.TARGET_TO_NATIVE)
            coEvery { generator(any(), any()) } throws CancellationException("cancel")
            assertThrows(CancellationException::class.java) { runBlocking { service.addWord("word") } }
            coVerify(exactly = 0) { repository.addVocabularyItemIfAbsent(any()) }
            coEvery { repository.addVocabularyItemIfAbsent(any()) } throws CancellationException("cancel")
            assertThrows(CancellationException::class.java) { runBlocking { service.addWord("word", "meaning") } }
        }

    @Test
    fun `late duplicate and persistence errors are reported accurately`() =
        runTest {
            coEvery { repository.addVocabularyItemIfAbsent(any()) } returns false
            assertThat(service.addWord("word", "meaning")).contains("already exists")
            coEvery { repository.addVocabularyItemIfAbsent(any()) } throws IllegalStateException("secret")
            val error =
                assertThrows(
                    AppFunctionAppUnknownException::class.java,
                ) { runBlocking { service.addWord("word", "meaning") } }
            assertThat(error.message).doesNotContain("secret")
        }
}
