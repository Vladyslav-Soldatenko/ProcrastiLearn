package com.procrastilearn.app.ui

import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.local.prefs.OpenAiPreferencesStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the [resolveProcessTextPrefill] helper extracted out of [AddWordViewModel],
 * covering it directly rather than only through the ViewModel's behavior.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProcessTextPrefillResolverTest {
    private lateinit var openAiStore: OpenAiPreferencesStore
    private lateinit var openAiKeyFlow: MutableStateFlow<String?>
    private lateinit var useAiFlow: MutableStateFlow<Boolean>

    @Before
    fun setUp() {
        openAiStore = mockk()
        openAiKeyFlow = MutableStateFlow(null)
        useAiFlow = MutableStateFlow(false)
        every { openAiStore.readOpenAiApiKey() } returns openAiKeyFlow
        every { openAiStore.readUseAiForTranslation() } returns useAiFlow
    }

    @Test
    fun `returns null for blank text`() =
        runTest {
            val result = resolveProcessTextPrefill(openAiStore, "   ")

            assertThat(result).isNull()
        }

    @Test
    fun `returns null for empty text`() =
        runTest {
            val result = resolveProcessTextPrefill(openAiStore, "")

            assertThat(result).isNull()
        }

    @Test
    fun `trims surrounding whitespace from the word`() =
        runTest {
            val result = resolveProcessTextPrefill(openAiStore, "  Haus  \n")

            assertThat(result?.word).isEqualTo("Haus")
        }

    @Test
    fun `reports hasKey false when api key is missing`() =
        runTest {
            openAiKeyFlow.value = null

            val result = resolveProcessTextPrefill(openAiStore, "Haus")

            assertThat(result?.hasKey).isFalse()
        }

    @Test
    fun `reports hasKey false when api key is blank`() =
        runTest {
            openAiKeyFlow.value = "   "

            val result = resolveProcessTextPrefill(openAiStore, "Haus")

            assertThat(result?.hasKey).isFalse()
        }

    @Test
    fun `reports hasKey true when api key is present`() =
        runTest {
            openAiKeyFlow.value = "abc123"

            val result = resolveProcessTextPrefill(openAiStore, "Haus")

            assertThat(result?.hasKey).isTrue()
        }

    @Test
    fun `reports useAi from the preference store`() =
        runTest {
            useAiFlow.value = true

            val result = resolveProcessTextPrefill(openAiStore, "Haus")

            assertThat(result?.useAi).isTrue()
        }

    @Test
    fun `combines word key and toggle state into the result`() =
        runTest {
            openAiKeyFlow.value = "abc123"
            useAiFlow.value = true

            val result = resolveProcessTextPrefill(openAiStore, "Katze")

            assertThat(result).isEqualTo(ProcessTextPrefill(word = "Katze", hasKey = true, useAi = true))
        }
}
