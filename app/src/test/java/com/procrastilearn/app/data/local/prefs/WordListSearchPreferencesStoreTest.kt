package com.procrastilearn.app.data.local.prefs

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.domain.model.SearchScope
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
class WordListSearchPreferencesStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var studyPreferences: StudyPreferencesDataStore
    private lateinit var store: WordListSearchPreferencesStore

    @Before
    fun setUp() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val filesRoot = temporaryFolder.newFolder("datastore-root")
        val dataStoreContext =
            object : ContextWrapper(baseContext) {
                override fun getFilesDir(): File = filesRoot

                override fun getApplicationContext(): Context = this
            }
        studyPreferences = StudyPreferencesDataStore(dataStoreContext)
        store = WordListSearchPreferencesStore(studyPreferences)
    }

    @Test
    fun readScopeEmitsBothTrueWhenNothingIsStored() =
        runTest {
            assertThat(store.readScope().first()).isEqualTo(SearchScope(matchWord = true, matchTranslation = true))
        }

    @Test
    fun setScopeWordOnlyPersistsAndReadsBack() =
        runTest {
            store.setScope(SearchScope(matchWord = true, matchTranslation = false))

            assertThat(store.readScope().first())
                .isEqualTo(SearchScope(matchWord = true, matchTranslation = false))
        }

    @Test
    fun setScopeTranslationOnlyPersistsAndReadsBack() =
        runTest {
            store.setScope(SearchScope(matchWord = false, matchTranslation = true))

            assertThat(store.readScope().first())
                .isEqualTo(SearchScope(matchWord = false, matchTranslation = true))
        }

    @Test
    fun setScopeOverwritesPreviouslyPersistedScope() =
        runTest {
            store.setScope(SearchScope(matchWord = true, matchTranslation = false))
            store.setScope(SearchScope(matchWord = false, matchTranslation = true))

            assertThat(store.readScope().first())
                .isEqualTo(SearchScope(matchWord = false, matchTranslation = true))
        }

    @Test
    fun readScopeReflectsLiveUpdatesToTheUnderlyingDataStore() =
        runTest {
            assertThat(store.readScope().first()).isEqualTo(SearchScope(matchWord = true, matchTranslation = true))

            store.setScope(SearchScope(matchWord = true, matchTranslation = false))
            assertThat(store.readScope().first())
                .isEqualTo(SearchScope(matchWord = true, matchTranslation = false))
        }

    @Test
    fun readScopeSurvivesRecreationOfTheStoreWrapper() =
        runTest {
            store.setScope(SearchScope(matchWord = false, matchTranslation = true))

            val recreatedStore = WordListSearchPreferencesStore(studyPreferences)
            assertThat(recreatedStore.readScope().first())
                .isEqualTo(SearchScope(matchWord = false, matchTranslation = true))
        }

    @Test
    fun setScopeWithBothFlagsFalseStillWritesWhatItIsGiven() =
        runTest {
            store.setScope(SearchScope(matchWord = false, matchTranslation = false))

            assertThat(store.readScope().first())
                .isEqualTo(SearchScope(matchWord = false, matchTranslation = false))
        }
}
