package com.procrastilearn.app.data.local.prefs

import android.content.Context
import android.content.ContextWrapper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class PreferencesDataStoreTest {
    private lateinit var filesRoot: File
    private lateinit var store: PreferencesDataStore

    @Before
    fun setUp() {
        if (!::store.isInitialized) {
            val baseContext = ApplicationProvider.getApplicationContext<Context>()
            filesRoot = File(baseContext.filesDir, "prefs-datastore-test").apply { mkdirs() }
            val dataStoreContext =
                object : ContextWrapper(baseContext) {
                    override fun getFilesDir(): File = filesRoot

                    override fun getApplicationContext(): Context = this
                }
            store = PreferencesDataStore(dataStoreContext)
        }

        clearDataStore()
    }

    @Test
    fun blockedAppsFlowEmitsEmptySetByDefault() =
        runTest {
            assertThat(store.blockedApps.first()).isEmpty()
        }

    @Test
    fun setBlockedAppsPersistsValues() =
        runTest {
            val apps = setOf("one", "two")
            store.setBlockedApps(apps)

            assertThat(store.blockedApps.first()).containsExactlyElementsIn(apps)
        }

    @Test
    fun addBlockedAppMergesWithExisting() =
        runTest {
            store.setBlockedApps(setOf("one"))
            store.addBlockedApp("two")

            assertThat(store.blockedApps.first()).containsExactly("one", "two")
        }

    @Test
    fun removeBlockedAppExcludesValue() =
        runTest {
            store.setBlockedApps(setOf("one", "two"))
            store.removeBlockedApp("one")

            assertThat(store.blockedApps.first()).containsExactly("two")
        }

    @Test
    fun toggleBlockedAppAddsWhenMissingAndRemovesWhenPresent() =
        runTest {
            store.toggleApp("pkg.one")
            assertThat(store.blockedApps.first()).containsExactly("pkg.one")

            store.toggleApp("pkg.one")
            assertThat(store.blockedApps.first()).isEmpty()
        }

    @Test
    fun procrastilearnEnabledFlowDefaultsToTrue() =
        runTest {
            assertThat(store.isProcrastilearnEnabled.first()).isTrue()
        }

    @Test
    fun setProcrastilearnEnabledUpdatesFlag() =
        runTest {
            store.setProcrastilearnEnabled(false)
            assertThat(store.isProcrastilearnEnabled.first()).isFalse()

            store.setProcrastilearnEnabled(true)
            assertThat(store.isProcrastilearnEnabled.first()).isTrue()
        }

    private fun clearDataStore() {
        val dataStore = extractDataStore()
        runBlocking {
            dataStore.updateData { preferencesOf() }
        }
        File(filesRoot, "datastore").mkdirs()
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractDataStore(): DataStore<Preferences> {
        val field = PreferencesDataStore::class.java.getDeclaredField("dataStore")
        field.isAccessible = true
        return field.get(store) as DataStore<Preferences>
    }
}
