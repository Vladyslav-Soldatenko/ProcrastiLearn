package com.procrastilearn.app.ui

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.R
import com.procrastilearn.app.data.counter.DayCounters
import com.procrastilearn.app.data.local.dao.VocabularyDao
import com.procrastilearn.app.data.local.entity.VocabularyEntity
import com.procrastilearn.app.data.local.prefs.DayCountersStore
import com.procrastilearn.app.data.local.prefs.OpenAiPreferencesStore
import com.procrastilearn.app.data.local.prefs.OpenAiPromptDefaults
import com.procrastilearn.app.domain.model.LearningPreferencesConfig
import com.procrastilearn.app.domain.model.MixMode
import com.procrastilearn.app.domain.model.VocabularyExportItem
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.parser.VocabularyExportParser
import com.procrastilearn.app.domain.parser.VocabularyParser
import com.procrastilearn.app.domain.repository.VocabularyRepository
import com.procrastilearn.app.utils.MainDispatcherRule
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var appContext: Context
    private lateinit var dayCountersStore: DayCountersStore
    private lateinit var openAiStore: OpenAiPreferencesStore
    private lateinit var vocabularyDao: VocabularyDao
    private lateinit var vocabularyRepository: VocabularyRepository
    private lateinit var policyFlow: MutableStateFlow<LearningPreferencesConfig>
    private lateinit var countersFlow: MutableStateFlow<DayCounters>
    private lateinit var apiKeyFlow: MutableStateFlow<String?>
    private lateinit var promptFlow: MutableStateFlow<String>
    private lateinit var reversePromptFlow: MutableStateFlow<String>
    private val defaultParser: VocabularyParser =
        object : VocabularyParser {
            override val id: String = "apkg"
            override val titleResId: Int = R.string.settings_import_option_anki_apkg
            override val descriptionResId: Int? = R.string.settings_import_option_anki_apkg_desc
            override val supportedExtensions: Set<String> = setOf("apkg")
            override val mimeTypes: List<String> = listOf("application/apkg")

            override fun parse(file: File): List<VocabularyItem> = emptyList()
        }

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        dayCountersStore = mockk(relaxed = true)
        openAiStore = mockk(relaxed = true)
        vocabularyDao = mockk()
        vocabularyRepository = mockk(relaxed = true)
        policyFlow =
            MutableStateFlow(
                LearningPreferencesConfig(
                    newPerDay = 20,
                    reviewPerDay = 150,
                    overlayInterval = 10,
                    mixMode = MixMode.MIX,
                ),
            )
        countersFlow =
            MutableStateFlow(
                DayCounters(
                    yyyymmdd = 20260716,
                    newShown = 0,
                    reviewShown = 0,
                    reviewsSinceLastNew = 0,
                    extraNewToday = 0,
                ),
            )
        apiKeyFlow = MutableStateFlow(null)
        promptFlow = MutableStateFlow(OpenAiPromptDefaults.translationPrompt)
        reversePromptFlow = MutableStateFlow(OpenAiPromptDefaults.reverseTranslationPrompt)

        every { dayCountersStore.readPolicy() } returns policyFlow
        every { dayCountersStore.read() } returns countersFlow
        every { openAiStore.readOpenAiApiKey() } returns apiKeyFlow
        every { openAiStore.readOpenAiPrompt() } returns promptFlow
        every { openAiStore.readOpenAiReversePrompt() } returns reversePromptFlow
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    private fun buildViewModel(parsers: Set<VocabularyParser> = setOf(defaultParser)): SettingsViewModel =
        SettingsViewModel(
            dayCountersStore = dayCountersStore,
            openAiStore = openAiStore,
            vocabularyDao = vocabularyDao,
            vocabularyRepository = vocabularyRepository,
            parsers = parsers,
            ioDispatcher = mainDispatcherRule.testDispatcher,
        )

    @Test
    fun `import options surface parser metadata`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()

            val options = viewModel.importOptions

            assertThat(options).hasSize(1)
            val option = options.first()
            assertThat(option.id).isEqualTo("apkg")
            assertThat(option.mimeTypes).contains("application/apkg")
        }

    @Test
    fun `uiState reflects values from store flows`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()

            viewModel.uiState.test {
                val initial = awaitItem()
                assertThat(initial).isEqualTo(SettingsUiState())

                val hydrated = awaitItem()
                assertThat(hydrated.mixMode).isEqualTo(MixMode.MIX)
                assertThat(hydrated.newPerDay).isEqualTo(20)
                assertThat(hydrated.reviewPerDay).isEqualTo(150)
                assertThat(hydrated.overlayInterval).isEqualTo(10)
                assertThat(hydrated.openAiApiKey).isNull()
                assertThat(hydrated.openAiPrompt).isEqualTo(OpenAiPromptDefaults.translationPrompt)
                assertThat(hydrated.openAiReversePrompt).isEqualTo(OpenAiPromptDefaults.reverseTranslationPrompt)

                policyFlow.value =
                    policyFlow.value.copy(
                        mixMode = MixMode.NEW_FIRST,
                        newPerDay = 5,
                        reviewPerDay = 80,
                        overlayInterval = 3,
                    )
                apiKeyFlow.value = "abc"
                promptFlow.value = "custom prompt"
                reversePromptFlow.value = "custom reverse prompt"

                val updated = awaitItem()
                assertThat(updated.mixMode).isEqualTo(MixMode.NEW_FIRST)
                assertThat(updated.newPerDay).isEqualTo(5)
                assertThat(updated.reviewPerDay).isEqualTo(80)
                assertThat(updated.overlayInterval).isEqualTo(3)
                assertThat(updated.openAiApiKey).isEqualTo("abc")
                assertThat(updated.openAiPrompt).isEqualTo("custom prompt")
                assertThat(updated.openAiReversePrompt).isEqualTo("custom reverse prompt")
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `loadAvailableNewCount queries dao and updates state`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            coEvery { vocabularyDao.countNewTotal() } returns 12

            viewModel.availableNewCount.test {
                assertThat(awaitItem()).isEqualTo(0)

                viewModel.loadAvailableNewCount()

                assertThat(awaitItem()).isEqualTo(12)
                cancelAndIgnoreRemainingEvents()
            }
            coVerify { vocabularyDao.countNewTotal() }
        }

    @Test
    fun `loadAvailableNewCount computes availableToAddToday from unseen total minus current quota`() =
        runTest(mainDispatcherRule.testDispatcher) {
            // newPerDay=20 (from policyFlow), newShown=0, extraNewToday=0 -> quota remaining = 20.
            val viewModel = buildViewModel()
            coEvery { vocabularyDao.countNewTotal() } returns 50

            viewModel.loadAvailableNewCount()
            advanceUntilIdle()

            // remaining quota = 20 (newPerDay) + 0 (extra) - 0 (shown) = 20
            // availableToAddToday = 50 (unseen) - 20 (remaining quota) = 30
            assertThat(viewModel.availableToAddToday.value).isEqualTo(30)
        }

    @Test
    fun `loadAvailableNewCount reports zero availableToAddToday when unseen count is at or below current quota`() =
        runTest(mainDispatcherRule.testDispatcher) {
            // Regression test for the reported bug: when the deck has fewer (or no) unseen
            // cards than the current quota already claims, there is no room to add more.
            val viewModel = buildViewModel()
            coEvery { vocabularyDao.countNewTotal() } returns 0

            viewModel.loadAvailableNewCount()
            advanceUntilIdle()

            assertThat(viewModel.availableToAddToday.value).isEqualTo(0)
        }

    @Test
    fun `loadAvailableNewCount accounts for extraNewToday already granted when computing capacity`() =
        runTest(mainDispatcherRule.testDispatcher) {
            // newPerDay=20, extraNewToday=15 already granted, newShown=0 -> quota remaining = 35.
            // Only 40 cards are unseen, so just 5 more can still be added before hitting the cap.
            countersFlow.value = countersFlow.value.copy(extraNewToday = 15)
            val viewModel = buildViewModel()
            coEvery { vocabularyDao.countNewTotal() } returns 40

            viewModel.loadAvailableNewCount()
            advanceUntilIdle()

            assertThat(viewModel.availableToAddToday.value).isEqualTo(5)
        }

    @Test
    fun `onMixModeChange delegates to store`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            coEvery { dayCountersStore.setMixMode(any()) } returns Unit

            viewModel.onMixModeChange(MixMode.NEW_FIRST)
            advanceUntilIdle()

            coVerify { dayCountersStore.setMixMode(MixMode.NEW_FIRST) }
        }

    @Test
    fun `onNewPerDayChange delegates to store`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            coEvery { dayCountersStore.setNewPerDay(any()) } returns Unit

            viewModel.onNewPerDayChange(42)
            advanceUntilIdle()

            coVerify { dayCountersStore.setNewPerDay(42) }
        }

    @Test
    fun `onAddCardsForToday delegates to store with current available new count`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            coEvery { vocabularyDao.countNewTotal() } returns 30
            coEvery { dayCountersStore.addExtraNewToday(any(), any()) } returns Unit

            viewModel.onAddCardsForToday(16)
            advanceUntilIdle()

            coVerify { dayCountersStore.addExtraNewToday(16, 30) }
        }

    @Test
    fun `onReviewPerDayChange delegates to store`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            coEvery { dayCountersStore.setReviewPerDay(any()) } returns Unit

            viewModel.onReviewPerDayChange(77)
            advanceUntilIdle()

            coVerify { dayCountersStore.setReviewPerDay(77) }
        }

    @Test
    fun `onOverlayIntervalChange delegates to store`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            coEvery { dayCountersStore.setOverlayInterval(any()) } returns Unit

            viewModel.onOverlayIntervalChange(9)
            advanceUntilIdle()

            coVerify { dayCountersStore.setOverlayInterval(9) }
        }

    @Test
    fun `onOpenAiApiKeyChange delegates to store`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            coEvery { openAiStore.setOpenAiApiKey(any()) } returns Unit

            viewModel.onOpenAiApiKeyChange("key")
            advanceUntilIdle()

            coVerify { openAiStore.setOpenAiApiKey("key") }
        }

    @Test
    fun `onOpenAiPromptChange delegates to store`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            coEvery { openAiStore.setOpenAiPrompt(any()) } returns Unit

            viewModel.onOpenAiPromptChange("prompt")
            advanceUntilIdle()

            coVerify { openAiStore.setOpenAiPrompt("prompt") }
        }

    @Test
    fun `onOpenAiReversePromptChange delegates to store`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            coEvery { openAiStore.setOpenAiReversePrompt(any()) } returns Unit

            viewModel.onOpenAiReversePromptChange("prompt")
            advanceUntilIdle()

            coVerify { openAiStore.setOpenAiReversePrompt("prompt") }
        }

    @Test
    fun `exportVocabularyToUri writes json and invokes callback with success`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            val context = appContext
            val tempFile =
                kotlin.io.path
                    .createTempFile(prefix = "export", suffix = ".json")
                    .toFile()
            val uri = Uri.fromFile(tempFile)
            val entity =
                VocabularyEntity(
                    id = 1,
                    word = "Haus",
                    translation = "House",
                    createdAt = 123L,
                    lastShownAt = null,
                    correctCount = 2,
                    incorrectCount = 1,
                    fsrsCardJson = "{\"c\":1}",
                    fsrsDueAt = 456L,
                )
            every { vocabularyDao.getAllVocabulary() } returns flowOf(listOf(entity))

            val completion = CompletableDeferred<Boolean>()

            viewModel.exportVocabularyToUri(context, uri) { completion.complete(it) }

            assertThat(completion.await()).isTrue()
            val payload = tempFile.readText()
            assertThat(payload).contains("\"id\":1")
            assertThat(payload).contains("\"word\":\"Haus\"")
            assertThat(payload).contains("\"translation\":\"House\"")
            assertThat(payload).contains("\"createdAt\":123")
            assertThat(payload).contains("\"lastShownAt\":null")
            assertThat(payload).contains("\"correctCount\":2")
            assertThat(payload).contains("\"incorrectCount\":1")
            assertThat(payload).contains("\"fsrsCardJson\":\"{\\\"c\\\":1}\"")
            assertThat(payload).contains("\"fsrsDueAt\":456")
        }

    @Test
    fun `exportVocabularyToUri reports failure on exception`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            val context = appContext
            val tempFile =
                kotlin.io.path
                    .createTempFile(prefix = "export", suffix = ".json")
                    .toFile()
            val uri = Uri.fromFile(tempFile)
            every { vocabularyDao.getAllVocabulary() } returns flow { throw IllegalStateException("boom") }

            val completion = CompletableDeferred<Boolean>()

            viewModel.exportVocabularyToUri(context, uri) { completion.complete(it) }

            assertThat(completion.await()).isFalse()
            assertThat(tempFile.readText()).isEmpty()
        }

    @Test
    fun `importVocabularyFromUri delegates parsed items to repository`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val parsedItem = VocabularyItem(id = 0, word = "Hallo", translation = "Hello", isNew = true)
            val parser =
                object : VocabularyParser {
                    override val id: String = "apkg"
                    override val titleResId: Int = R.string.settings_import_option_anki_apkg
                    override val descriptionResId: Int? = R.string.settings_import_option_anki_apkg_desc
                    override val supportedExtensions: Set<String> = setOf("apkg")
                    override val mimeTypes: List<String> = listOf("application/apkg")

                    override fun parse(file: File): List<VocabularyItem> = listOf(parsedItem)
                }
            coEvery { vocabularyRepository.addVocabularyItem(any()) } returns Unit
            val viewModel = buildViewModel(parsers = setOf(parser))
            val tempFile =
                kotlin.io.path
                    .createTempFile(prefix = "deck", suffix = ".apkg")
                    .toFile()
            tempFile.writeText("placeholder")
            val uri = Uri.fromFile(tempFile)

            var result: VocabularyImportResult? = null
            viewModel.importVocabularyFromUri(appContext, parser.id, uri) { result = it }
            advanceUntilIdle()

            assertThat(result).isEqualTo(VocabularyImportResult.Success(importedCount = 1))
            coVerify { vocabularyRepository.addVocabularyItem(parsedItem) }
        }

    @Test
    fun `importVocabularyFromUri uses export parser to insert full entities`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val exportItem =
                VocabularyExportItem(
                    id = 4,
                    word = "Wort",
                    translation = "Word",
                    createdAt = 10L,
                    lastShownAt = 20L,
                    correctCount = 1,
                    incorrectCount = 0,
                    fsrsCardJson = "{\"card\":1}",
                    fsrsDueAt = 30L,
                )
            val parser =
                object : VocabularyParser, VocabularyExportParser {
                    override val id: String = "json"
                    override val titleResId: Int = R.string.settings_import_option_json
                    override val descriptionResId: Int? = R.string.settings_import_option_json_desc
                    override val supportedExtensions: Set<String> = setOf("json")
                    override val mimeTypes: List<String> = listOf("application/json")

                    override fun parse(file: File): List<VocabularyItem> = emptyList()

                    override fun parseExport(file: File): List<VocabularyExportItem> = listOf(exportItem)
                }
            coEvery { vocabularyDao.insertAllVocabulary(any()) } returns Unit
            val viewModel = buildViewModel(parsers = setOf(parser))
            val tempFile =
                kotlin.io.path
                    .createTempFile(prefix = "deck", suffix = ".json")
                    .toFile()
            tempFile.writeText("placeholder")
            val uri = Uri.fromFile(tempFile)

            var result: VocabularyImportResult? = null
            viewModel.importVocabularyFromUri(appContext, parser.id, uri) { result = it }
            advanceUntilIdle()

            assertThat(result).isEqualTo(VocabularyImportResult.Success(importedCount = 1))
            coVerify { vocabularyDao.insertAllVocabulary(any()) }
            coVerify(exactly = 0) { vocabularyRepository.addVocabularyItem(any()) }
        }

    @Test
    fun `export then import json preserves all entity fields`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val parser = com.procrastilearn.app.data.parser.json.JsonVocabularyParser()
            val viewModel = buildViewModel(parsers = setOf(parser))
            val entity =
                VocabularyEntity(
                    id = 9,
                    word = "schule",
                    translation = "school",
                    createdAt = 111L,
                    lastShownAt = 222L,
                    correctCount = 3,
                    incorrectCount = 4,
                    fsrsCardJson = "{\"card\":2}",
                    fsrsDueAt = 333L,
                )
            every { vocabularyDao.getAllVocabulary() } returns flowOf(listOf(entity))
            coEvery { vocabularyDao.insertAllVocabulary(any()) } returns Unit

            val tempFile =
                kotlin.io.path
                    .createTempFile(prefix = "export", suffix = ".json")
                    .toFile()
            val uri = Uri.fromFile(tempFile)
            val exported = CompletableDeferred<Boolean>()

            viewModel.exportVocabularyToUri(appContext, uri) { exported.complete(it) }

            assertThat(exported.await()).isTrue()

            var importResult: VocabularyImportResult? = null
            viewModel.importVocabularyFromUri(appContext, parser.id, uri) { importResult = it }
            advanceUntilIdle()

            assertThat(importResult).isEqualTo(VocabularyImportResult.Success(importedCount = 1))
            val inserted = slot<List<VocabularyEntity>>()
            coVerify { vocabularyDao.insertAllVocabulary(capture(inserted)) }
            assertThat(inserted.captured).containsExactly(entity)
        }

    @Test
    fun `importVocabularyFromUri reports unsupported format`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel(parsers = emptySet())
            val tempFile =
                kotlin.io.path
                    .createTempFile(prefix = "deck", suffix = ".apkg")
                    .toFile()
            tempFile.writeText("placeholder")
            val uri = Uri.fromFile(tempFile)

            var result: VocabularyImportResult? = null
            viewModel.importVocabularyFromUri(appContext, "unknown", uri) { result = it }
            advanceUntilIdle()

            assertThat(result).isEqualTo(
                VocabularyImportResult.Failure(VocabularyImportFailureReason.UNSUPPORTED_FORMAT),
            )
            coVerify(exactly = 0) { vocabularyRepository.addVocabularyItem(any()) }
        }

    @Test
    fun `importVocabularyFromUri reports parser errors`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val parser =
                object : VocabularyParser {
                    override val id: String = "apkg"
                    override val titleResId: Int = R.string.settings_import_option_anki_apkg
                    override val descriptionResId: Int? = R.string.settings_import_option_anki_apkg_desc
                    override val supportedExtensions: Set<String> = setOf("apkg")
                    override val mimeTypes: List<String> = listOf("application/apkg")

                    override fun parse(file: File): List<VocabularyItem> = throw IllegalArgumentException("bad")
                }
            val viewModel = buildViewModel(parsers = setOf(parser))
            val tempFile =
                kotlin.io.path
                    .createTempFile(prefix = "deck", suffix = ".apkg")
                    .toFile()
            tempFile.writeText("placeholder")
            val uri = Uri.fromFile(tempFile)

            var result: VocabularyImportResult? = null
            viewModel.importVocabularyFromUri(appContext, parser.id, uri) { result = it }
            advanceUntilIdle()

            assertThat(result).isEqualTo(
                VocabularyImportResult.Failure(VocabularyImportFailureReason.PARSE_ERROR),
            )
            coVerify(exactly = 0) { vocabularyRepository.addVocabularyItem(any()) }
        }
}
