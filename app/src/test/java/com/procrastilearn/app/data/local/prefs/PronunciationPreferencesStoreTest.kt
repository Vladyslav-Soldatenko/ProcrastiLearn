package com.procrastilearn.app.data.local.prefs

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
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
class PronunciationPreferencesStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var store: PronunciationPreferencesStore

    @Before
    fun setUp() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()
        val filesRoot = temporaryFolder.newFolder("datastore-root")
        val dataStoreContext =
            object : ContextWrapper(baseContext) {
                override fun getFilesDir(): File = filesRoot

                override fun getApplicationContext(): Context = this
            }
        store = PronunciationPreferencesStore(StudyPreferencesDataStore(dataStoreContext))
    }

    @Test
    fun `defaults to disabled when preference missing`() =
        runTest {
            assertThat(store.readEnabled().first()).isFalse()
        }

    @Test
    fun `enabling and disabling persists`() =
        runTest {
            store.setEnabled(true)
            assertThat(store.readEnabled().first()).isTrue()

            store.setEnabled(false)
            assertThat(store.readEnabled().first()).isFalse()
        }
}
