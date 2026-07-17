package com.procrastilearn.app.data.local.prefs

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.domain.model.AiTranslationDirection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class OpenAiPreferencesStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var store: OpenAiPreferencesStore

    @Before
    fun setUp() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val filesRoot = temporaryFolder.newFolder("datastore-root")
        val dataStoreContext =
            object : ContextWrapper(baseContext) {
                override fun getFilesDir(): File = filesRoot

                override fun getApplicationContext(): Context = this
            }
        store = OpenAiPreferencesStore(StudyPreferencesDataStore(dataStoreContext))
    }

    @Test
    fun readFlowsEmitDefaultsWhenPreferencesMissing() =
        runTest {
            assertThat(store.readOpenAiApiKey().first()).isNull()
            assertThat(store.readOpenAiPrompt().first()).isEqualTo(OpenAiPromptDefaults.translationPrompt)
            assertThat(store.readOpenAiReversePrompt().first()).isEqualTo(OpenAiPromptDefaults.reverseTranslationPrompt)
            assertThat(store.readUseAiForTranslation().first()).isFalse()
            assertThat(store.readAiTranslationDirection().first()).isEqualTo(AiTranslationDirection.FOREIGN_TO_NATIVE)
        }

    @Test
    fun openAiPreferencesPersistAndResetToDefaults() =
        runTest {
            store.setOpenAiApiKey("test-key")
            assertThat(store.readOpenAiApiKey().first()).isEqualTo("test-key")

            store.setOpenAiPrompt("  custom prompt  ")
            assertThat(store.readOpenAiPrompt().first()).isEqualTo("custom prompt")

            store.setOpenAiPrompt(OpenAiPromptDefaults.translationPrompt)
            assertThat(store.readOpenAiPrompt().first()).isEqualTo(OpenAiPromptDefaults.translationPrompt)

            store.setOpenAiPrompt("   ")
            assertThat(store.readOpenAiPrompt().first()).isEqualTo(OpenAiPromptDefaults.translationPrompt)

            store.setOpenAiReversePrompt("  custom reverse prompt  ")
            assertThat(store.readOpenAiReversePrompt().first()).isEqualTo("custom reverse prompt")

            store.setOpenAiReversePrompt(OpenAiPromptDefaults.reverseTranslationPrompt)
            assertThat(store.readOpenAiReversePrompt().first()).isEqualTo(OpenAiPromptDefaults.reverseTranslationPrompt)

            store.setOpenAiReversePrompt("   ")
            assertThat(store.readOpenAiReversePrompt().first()).isEqualTo(OpenAiPromptDefaults.reverseTranslationPrompt)

            store.setAiTranslationDirection(AiTranslationDirection.NATIVE_TO_FOREIGN)
            assertThat(store.readAiTranslationDirection().first()).isEqualTo(AiTranslationDirection.NATIVE_TO_FOREIGN)

            store.setUseAiForTranslation(true)
            assertThat(store.readUseAiForTranslation().first()).isTrue()
        }
}
