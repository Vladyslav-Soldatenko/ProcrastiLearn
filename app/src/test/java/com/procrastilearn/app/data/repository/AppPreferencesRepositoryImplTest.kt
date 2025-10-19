package com.procrastilearn.app.data.repository

import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.local.prefs.PreferencesDataStore
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppPreferencesRepositoryImplTest {
    private lateinit var dataStore: PreferencesDataStore
    private lateinit var repository: AppPreferencesRepositoryImpl

    private lateinit var blockedAppsFlow: MutableStateFlow<Set<String>>
    private lateinit var enabledFlow: MutableStateFlow<Boolean>

    @Before
    fun setUp() {
        dataStore = mockk(relaxed = true)
        blockedAppsFlow = MutableStateFlow(emptySet())
        enabledFlow = MutableStateFlow(true)

        every { dataStore.blockedApps } returns blockedAppsFlow
        every { dataStore.isProcrastilearnEnabled } returns enabledFlow

        repository = AppPreferencesRepositoryImpl(dataStore)
    }

    @Test
    fun `getBlockedApps returns underlying flow`() {
        val result = repository.getBlockedApps()
        assertThat(result).isSameInstanceAs(blockedAppsFlow)
    }

    @Test
    fun `isProcrastilearnEnabled returns underlying flow`() {
        val result = repository.isProcrastilearnEnabled()
        assertThat(result).isSameInstanceAs(enabledFlow)
    }

    @Test
    fun `addBlockedApp delegates to data store`() =
        runTest {
            repository.addBlockedApp("pkg.one")
            coVerify(exactly = 1) { dataStore.addBlockedApp("pkg.one") }
        }

    @Test
    fun `removeBlockedApp delegates to data store`() =
        runTest {
            repository.removeBlockedApp("pkg.one")
            coVerify(exactly = 1) { dataStore.removeBlockedApp("pkg.one") }
        }

    @Test
    fun `setBlockedApps delegates to data store`() =
        runTest {
            val packages = setOf("pkg.one", "pkg.two")
            repository.setBlockedApps(packages)
            coVerify(exactly = 1) { dataStore.setBlockedApps(packages) }
        }

    @Test
    fun `isAppBlocked reflects latest flow value`() =
        runTest {
            blockedAppsFlow.value = setOf("pkg.one")

            val result = repository.isAppBlocked("pkg.one")
            val missing = repository.isAppBlocked("pkg.two")

            assertThat(result).isTrue()
            assertThat(missing).isFalse()
        }

    @Test
    fun `toggleApp delegates to data store`() =
        runTest {
            repository.toggleApp("pkg.one")
            coVerify(exactly = 1) { dataStore.toggleApp("pkg.one") }
        }

    @Test
    fun `setProcrastilearnEnabled delegates to data store`() =
        runTest {
            repository.setProcrastilearnEnabled(false)
            coVerify(exactly = 1) { dataStore.setProcrastilearnEnabled(false) }
        }
}
