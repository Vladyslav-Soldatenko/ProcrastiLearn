package com.procrastilearn.app.data.local.prefs

import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.domain.model.Language
import com.procrastilearn.app.domain.model.LanguagePair
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
class LanguagePreferencesStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var studyPreferences: StudyPreferencesDataStore
    private lateinit var store: LanguagePreferencesStore

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
        store = LanguagePreferencesStore(studyPreferences)
    }

    @Test
    fun readLanguagePairEmitsNullWhenNothingIsStored() =
        runTest {
            assertThat(store.readLanguagePair().first()).isNull()
        }

    @Test
    fun setLanguagePairPersistsAndReadsBackThePair() =
        runTest {
            store.setLanguagePair(Language.ENGLISH, Language.SPANISH)

            val pair = store.readLanguagePair().first()
            assertThat(pair).isEqualTo(LanguagePair(Language.ENGLISH, Language.SPANISH))
        }

    @Test
    fun setLanguagePairOverwritesPreviousPair() =
        runTest {
            store.setLanguagePair(Language.ENGLISH, Language.SPANISH)
            store.setLanguagePair(Language.GERMAN, Language.CHINESE)

            val pair = store.readLanguagePair().first()
            assertThat(pair).isEqualTo(LanguagePair(Language.GERMAN, Language.CHINESE))
        }

    @Test
    fun setLanguagePairAllowsSameNativeAndTargetAtStoreLevel() =
        runTest {
            store.setLanguagePair(Language.RUSSIAN, Language.RUSSIAN)

            val pair = store.readLanguagePair().first()
            assertThat(pair).isEqualTo(LanguagePair(Language.RUSSIAN, Language.RUSSIAN))
        }

    @Test
    fun readLanguagePairEmitsNullWhenOnlyNativeCodeIsStored() =
        runTest {
            val nativeKey = stringPreferencesKey("native_language_code")
            studyPreferences.ds.edit { it[nativeKey] = Language.ENGLISH.code }

            assertThat(store.readLanguagePair().first()).isNull()
        }

    @Test
    fun readLanguagePairEmitsNullWhenOnlyTargetCodeIsStored() =
        runTest {
            val targetKey = stringPreferencesKey("target_language_code")
            studyPreferences.ds.edit { it[targetKey] = Language.RUSSIAN.code }

            assertThat(store.readLanguagePair().first()).isNull()
        }

    @Test
    fun readLanguagePairEmitsNullWhenStoredCodesAreUnrecognized() =
        runTest {
            val nativeKey = stringPreferencesKey("native_language_code")
            val targetKey = stringPreferencesKey("target_language_code")
            studyPreferences.ds.edit {
                it[nativeKey] = "xx"
                it[targetKey] = "yy"
            }

            assertThat(store.readLanguagePair().first()).isNull()
        }

    @Test
    fun readLanguagePairEmitsNullWhenOneStoredCodeIsUnrecognized() =
        runTest {
            val nativeKey = stringPreferencesKey("native_language_code")
            val targetKey = stringPreferencesKey("target_language_code")
            studyPreferences.ds.edit {
                it[nativeKey] = Language.ENGLISH.code
                it[targetKey] = "not-a-real-code"
            }

            assertThat(store.readLanguagePair().first()).isNull()
        }

    @Test
    fun readLanguagePairReflectsLiveUpdatesToTheUnderlyingDataStore() =
        runTest {
            assertThat(store.readLanguagePair().first()).isNull()

            store.setLanguagePair(Language.ITALIAN, Language.PORTUGUESE)
            assertThat(store.readLanguagePair().first())
                .isEqualTo(LanguagePair(Language.ITALIAN, Language.PORTUGUESE))
        }
}
