package com.procrastilearn.app.data.local.prefs

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.counter.DayCounters
import com.procrastilearn.app.domain.model.AiTranslationDirection
import com.procrastilearn.app.domain.model.MixMode
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
class DayCountersStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var store: DayCountersStore

    @Before
    fun setUp() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val filesRoot = temporaryFolder.newFolder("datastore-root")
        val dataStoreContext =
            object : ContextWrapper(baseContext) {
                override fun getFilesDir(): File = filesRoot

                override fun getApplicationContext(): Context = this
            }
        store = DayCountersStore(dataStoreContext)
    }

    @Test
    fun readFlowsEmitDefaultsWhenPreferencesMissing() =
        runTest {
            val counters = store.read().first()
            assertThat(counters).isEqualTo(DayCounters(0, 0, 0, 0))

            val policy = store.readPolicy().first()
            assertThat(policy.newPerDay).isEqualTo(15)
            assertThat(policy.reviewPerDay).isEqualTo(99)
            assertThat(policy.overlayInterval).isEqualTo(0)
            assertThat(policy.mixMode).isEqualTo(MixMode.MIX)

            assertThat(store.readOpenAiApiKey().first()).isNull()
            assertThat(store.readOpenAiPrompt().first()).isEqualTo(OpenAiPromptDefaults.translationPrompt)
            assertThat(store.readOpenAiReversePrompt().first()).isEqualTo(OpenAiPromptDefaults.reverseTranslationPrompt)
            assertThat(store.readUseAiForTranslation().first()).isFalse()
            assertThat(store.readAiTranslationDirection().first()).isEqualTo(AiTranslationDirection.EN_TO_RU)
        }

    @Test
    fun counterMutationsUpdateValuesAsExpected() =
        runTest {
            store.resetFor(20240131)
            store.markReviewShown()
            store.markReviewShown()
            store.markNewShown()
            store.markReviewShown()

            val counters = store.read().first()
            assertThat(counters.yyyymmdd).isEqualTo(20240131)
            assertThat(counters.newShown).isEqualTo(1)
            assertThat(counters.reviewShown).isEqualTo(3)
            assertThat(counters.reviewsSinceLastNew).isEqualTo(1)
        }

    @Test
    fun policyLimitsAreClampedAndMixModePersists() =
        runTest {
            store.setMixMode(MixMode.NEW_FIRST)
            store.setNewPerDay(500)
            store.setReviewPerDay(5000)
            store.setOverlayInterval(5000)

            var policy = store.readPolicy().first()
            assertThat(policy.newPerDay).isEqualTo(200)
            assertThat(policy.reviewPerDay).isEqualTo(2000)
            assertThat(policy.overlayInterval).isEqualTo(2000)
            assertThat(policy.mixMode).isEqualTo(MixMode.NEW_FIRST)

            store.setNewPerDay(-5)
            store.setReviewPerDay(-10)
            store.setOverlayInterval(-1)

            policy = store.readPolicy().first()
            assertThat(policy.newPerDay).isEqualTo(0)
            assertThat(policy.reviewPerDay).isEqualTo(0)
            assertThat(policy.overlayInterval).isEqualTo(0)
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

            store.setAiTranslationDirection(AiTranslationDirection.RU_TO_EN)
            assertThat(store.readAiTranslationDirection().first()).isEqualTo(AiTranslationDirection.RU_TO_EN)

            store.setUseAiForTranslation(true)
            assertThat(store.readUseAiForTranslation().first()).isTrue()
        }
}
