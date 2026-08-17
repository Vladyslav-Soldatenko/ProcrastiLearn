package com.procrastilearn.app.ui

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.R
import com.procrastilearn.app.data.counter.DayCounters
import com.procrastilearn.app.data.export.VocabularyImportFailureReason
import com.procrastilearn.app.data.export.VocabularyImportResult
import com.procrastilearn.app.data.export.VocabularyTransferManager
import com.procrastilearn.app.data.local.dao.VocabularyDao
import com.procrastilearn.app.data.local.dao.VocabularyStatsDao
import com.procrastilearn.app.data.local.entity.VocabularyEntity
import com.procrastilearn.app.data.local.prefs.DayCountersStore
import com.procrastilearn.app.data.local.prefs.LanguagePreferencesStore
import com.procrastilearn.app.data.local.prefs.OpenAiPreferencesStore
import com.procrastilearn.app.data.local.prefs.OpenAiPromptDefaults
import com.procrastilearn.app.data.local.prefs.TranslationPreferences
import com.procrastilearn.app.domain.model.Language
import com.procrastilearn.app.domain.model.LanguagePair
import com.procrastilearn.app.domain.model.LearningPreferencesConfig
import com.procrastilearn.app.domain.model.MixMode
import com.procrastilearn.app.domain.model.NewCardOrder
import com.procrastilearn.app.domain.model.StudyDirectionMode
import com.procrastilearn.app.domain.model.VocabularyExportItem
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.parser.VocabularyExportParser
import com.procrastilearn.app.domain.parser.VocabularyParser
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
    private lateinit var languagePreferencesStore: LanguagePreferencesStore
    private lateinit var vocabularyDao: VocabularyDao
    private lateinit var vocabularyStatsDao: VocabularyStatsDao
    private lateinit var policyFlow: MutableStateFlow<LearningPreferencesConfig>
    private lateinit var countersFlow: MutableStateFlow<DayCounters>
    private lateinit var apiKeyFlow: MutableStateFlow<String?>
    private lateinit var promptFlow: MutableStateFlow<String>
    private lateinit var reversePromptFlow: MutableStateFlow<String>
    private lateinit var languagePairFlow: MutableStateFlow<LanguagePair?>
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
        languagePreferencesStore = mockk(relaxed = true)
        vocabularyDao = mockk()
        vocabularyStatsDao = mockk()
        policyFlow =
            MutableStateFlow(
                LearningPreferencesConfig(
                    newPerDay = 20,
                    reviewPerDay = 150,
                    overlayInterval = 10,
                    mixMode = MixMode.MIX,
                    ratingDelaySeconds = 4,
                ),
            )
        countersFlow =
            MutableStateFlow(
                DayCounters(
                    yyyymmdd = 20_260_716,
                    newShown = 0,
                    reviewShown = 0,
                    reviewsSinceLastNew = 0,
                    extraNewToday = 0,
                ),
            )
        apiKeyFlow = MutableStateFlow(null)
        promptFlow = MutableStateFlow(OpenAiPromptDefaults.translationPrompt)
        reversePromptFlow = MutableStateFlow(OpenAiPromptDefaults.reverseTranslationPrompt)
        languagePairFlow = MutableStateFlow(null)

        every { dayCountersStore.readPolicy() } returns policyFlow
        every { dayCountersStore.read() } returns countersFlow
        every { openAiStore.readOpenAiApiKey() } returns apiKeyFlow
        every { openAiStore.readOpenAiPrompt() } returns promptFlow
        every { openAiStore.readOpenAiReversePrompt() } returns reversePromptFlow
        every { languagePreferencesStore.readLanguagePair() } returns languagePairFlow
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    private fun buildViewModel(parsers: Set<VocabularyParser> = setOf(defaultParser)): SettingsViewModel =
        SettingsViewModel(
            dayCountersStore = dayCountersStore,
            translationPreferences = TranslationPreferences(openAiStore, languagePreferencesStore),
            vocabularyStatsDao = vocabularyStatsDao,
            transferManager =
                VocabularyTransferManager(
                    vocabularyDao = vocabularyDao,
                    parsers = parsers,
                    ioDispatcher = mainDispatcherRule.testDispatcher,
                ),
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
                assertThat(hydrated.ratingDelaySeconds).isEqualTo(4)
                assertThat(hydrated.newCardOrder).isEqualTo(NewCardOrder.SEQUENTIAL)
                assertThat(hydrated.openAiApiKey).isNull()
                assertThat(hydrated.openAiPrompt).isEqualTo(OpenAiPromptDefaults.translationPrompt)
                assertThat(hydrated.openAiReversePrompt).isEqualTo(OpenAiPromptDefaults.reverseTranslationPrompt)
                assertThat(hydrated.nativeLanguage).isEqualTo(Language.ENGLISH)
                assertThat(hydrated.targetLanguage).isEqualTo(Language.RUSSIAN)

                policyFlow.value =
                    policyFlow.value.copy(
                        mixMode = MixMode.NEW_FIRST,
                        newPerDay = 5,
                        reviewPerDay = 80,
                        overlayInterval = 3,
                        ratingDelaySeconds = 12,
                        newCardOrder = NewCardOrder.RANDOM,
                    )
                apiKeyFlow.value = "abc"
                promptFlow.value = "custom prompt"
                reversePromptFlow.value = "custom reverse prompt"
                languagePairFlow.value = LanguagePair(Language.GERMAN, Language.FRENCH)

                val updated = awaitItem()
                assertThat(updated.mixMode).isEqualTo(MixMode.NEW_FIRST)
                assertThat(updated.newPerDay).isEqualTo(5)
                assertThat(updated.reviewPerDay).isEqualTo(80)
                assertThat(updated.overlayInterval).isEqualTo(3)
                assertThat(updated.ratingDelaySeconds).isEqualTo(12)
                assertThat(updated.newCardOrder).isEqualTo(NewCardOrder.RANDOM)
                assertThat(updated.openAiApiKey).isEqualTo("abc")
                assertThat(updated.openAiPrompt).isEqualTo("custom prompt")
                assertThat(updated.openAiReversePrompt).isEqualTo("custom reverse prompt")
                assertThat(updated.nativeLanguage).isEqualTo(Language.GERMAN)
                assertThat(updated.targetLanguage).isEqualTo(Language.FRENCH)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `uiState falls back to English to Russian when no language pair is configured`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()

            viewModel.uiState.test {
                awaitItem()
                val hydrated = awaitItem()
                assertThat(hydrated.nativeLanguage).isEqualTo(Language.ENGLISH)
                assertThat(hydrated.targetLanguage).isEqualTo(Language.RUSSIAN)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `uiState reflects a configured language pair from the very first hydration`() =
        runTest(mainDispatcherRule.testDispatcher) {
            languagePairFlow.value = LanguagePair(Language.ITALIAN, Language.PORTUGUESE)
            val viewModel = buildViewModel()

            viewModel.uiState.test {
                awaitItem()
                val hydrated = awaitItem()
                assertThat(hydrated.nativeLanguage).isEqualTo(Language.ITALIAN)
                assertThat(hydrated.targetLanguage).isEqualTo(Language.PORTUGUESE)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `loadAvailableNewCount queries dao and updates state`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            coEvery { vocabularyStatsDao.countNewTotal() } returns 12

            viewModel.availableNewCount.test {
                assertThat(awaitItem()).isEqualTo(0)

                viewModel.loadAvailableNewCount()

                assertThat(awaitItem()).isEqualTo(12)
                cancelAndIgnoreRemainingEvents()
            }
            coVerify { vocabularyStatsDao.countNewTotal() }
        }

    @Test
    fun `loadAvailableNewCount computes availableToAddToday from unseen total minus current quota`() =
        runTest(mainDispatcherRule.testDispatcher) {
            // newPerDay=20 (from policyFlow), newShown=0, extraNewToday=0 -> quota remaining = 20.
            val viewModel = buildViewModel()
            coEvery { vocabularyStatsDao.countNewTotal() } returns 50

            viewModel.loadAvailableNewCount()
            advanceUntilIdle()

            // remaining quota = 20 (newPerDay) + 0 (extra) - 0 (shown) = 20
            // availableToAddToday = 50 (unseen) - 20 (remaining quota) = 30
            assertThat(viewModel.availableToAddToday.value).isEqualTo(30)
        }

    @Test
    fun `loadAvailableNewCount reports zero availableToAddToday when unseen count is at or below current quota`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            coEvery { vocabularyStatsDao.countNewTotal() } returns 0

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
            coEvery { vocabularyStatsDao.countNewTotal() } returns 40

            viewModel.loadAvailableNewCount()
            advanceUntilIdle()

            assertThat(viewModel.availableToAddToday.value).isEqualTo(5)
        }

    @Test
    fun `uiState reflects studyDirectionMode from the policy`() =
        runTest(mainDispatcherRule.testDispatcher) {
            policyFlow.value = policyFlow.value.copy(studyDirectionMode = StudyDirectionMode.BIDIRECTIONAL)
            val viewModel = buildViewModel()

            viewModel.uiState.test {
                awaitItem()
                val hydrated = awaitItem()
                assertThat(hydrated.studyDirectionMode).isEqualTo(StudyDirectionMode.BIDIRECTIONAL)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onStudyDirectionModeChange delegates to store`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            coEvery { dayCountersStore.setStudyDirectionMode(any()) } returns Unit

            viewModel.onStudyDirectionModeChange(StudyDirectionMode.BACKWARD)
            advanceUntilIdle()

            coVerify { dayCountersStore.setStudyDirectionMode(StudyDirectionMode.BACKWARD) }
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
            coEvery { vocabularyStatsDao.countNewTotal() } returns 30
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
    fun `onRatingDelayChange delegates to store`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            coEvery { dayCountersStore.setRatingDelaySeconds(any()) } returns Unit

            viewModel.onRatingDelayChange(15)
            advanceUntilIdle()

            coVerify { dayCountersStore.setRatingDelaySeconds(15) }
        }

    @Test
    fun `onNewCardOrderChange delegates to store`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            coEvery { dayCountersStore.setNewCardOrder(any()) } returns Unit

            viewModel.onNewCardOrderChange(NewCardOrder.RANDOM)
            advanceUntilIdle()

            coVerify { dayCountersStore.setNewCardOrder(NewCardOrder.RANDOM) }
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
    fun `onLanguagePairChange delegates to store`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = buildViewModel()
            coEvery { languagePreferencesStore.setLanguagePair(any(), any()) } returns Unit

            viewModel.onLanguagePairChange(Language.SPANISH, Language.ITALIAN)
            advanceUntilIdle()

            coVerify { languagePreferencesStore.setLanguagePair(Language.SPANISH, Language.ITALIAN) }
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

            val completion = CompletableDeferred<Result<Unit>>()

            viewModel.exportVocabularyToUri(context, uri) { completion.complete(it) }

            assertThat(completion.await().isSuccess).isTrue()
            val payload = tempFile.readText()
            assertThat(payload).contains("\"schemaVersion\": 4")
            assertThat(payload).contains("\"id\": 1")
            assertThat(payload).contains("\"word\": \"Haus\"")
            assertThat(payload).contains("\"translation\": \"House\"")
            assertThat(payload).contains("\"createdAt\": 123")
            assertThat(payload).doesNotContain("lastShownAt")
            assertThat(payload).contains("\"correctCount\": 2")
            assertThat(payload).contains("\"incorrectCount\": 1")
            assertThat(payload).contains("\"fsrsCardJson\": \"{\\\"c\\\":1}\"")
            assertThat(payload).contains("\"fsrsDueAt\": 456")
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

            val completion = CompletableDeferred<Result<Unit>>()

            viewModel.exportVocabularyToUri(context, uri) { completion.complete(it) }

            val result = completion.await()
            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()?.message).isEqualTo("boom")
            assertThat(tempFile.readText()).isEmpty()
        }

    @Test
    fun `importVocabularyFromUri inserts a genuinely new apkg item via applyImportBatch`() =
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
            coEvery { vocabularyDao.getMaxPosition() } returns 0L
            coEvery { vocabularyDao.getVocabularyByWords(any()) } returns emptyList()
            coEvery { vocabularyDao.applyImportBatch(any(), any()) } returns Unit
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
            val toInsert = slot<List<VocabularyEntity>>()
            coVerify { vocabularyDao.applyImportBatch(capture(toInsert), any()) }
            assertThat(toInsert.captured.single().word).isEqualTo("Hallo")
        }

    @Test
    fun `importVocabularyFromUri uses export parser to merge full entities via applyImportBatch`() =
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
            coEvery { vocabularyDao.getVocabularyByWords(any()) } returns emptyList()
            coEvery { vocabularyDao.applyImportBatch(any(), any()) } returns Unit
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
            coVerify { vocabularyDao.applyImportBatch(any(), any()) }
        }

    @Test
    fun `export then import json into an empty library re-inserts with a fresh id`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val parser =
                com.procrastilearn.app.data.parser.json
                    .JsonVocabularyParser()
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
            coEvery { vocabularyDao.getVocabularyByWords(any()) } returns emptyList()
            coEvery { vocabularyDao.applyImportBatch(any(), any()) } returns Unit

            val tempFile =
                kotlin.io.path
                    .createTempFile(prefix = "export", suffix = ".json")
                    .toFile()
            val uri = Uri.fromFile(tempFile)
            val exported = CompletableDeferred<Result<Unit>>()

            viewModel.exportVocabularyToUri(appContext, uri) { exported.complete(it) }

            assertThat(exported.await().isSuccess).isTrue()

            var importResult: VocabularyImportResult? = null
            viewModel.importVocabularyFromUri(appContext, parser.id, uri) { importResult = it }
            advanceUntilIdle()

            assertThat(importResult).isEqualTo(VocabularyImportResult.Success(importedCount = 1))
            val inserted = slot<List<VocabularyEntity>>()
            coVerify { vocabularyDao.applyImportBatch(capture(inserted), any()) }
            assertThat(inserted.captured).containsExactly(entity.copy(id = 0L))
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
            coVerify(exactly = 0) { vocabularyDao.applyImportBatch(any(), any()) }
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
            coVerify(exactly = 0) { vocabularyDao.applyImportBatch(any(), any()) }
        }
}
