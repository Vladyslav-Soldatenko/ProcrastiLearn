package com.procrastilearn.app.overlay

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OverlayUiStateProviderTest {
    private val values = OverlayUiStateProvider().values.toList()

    @Test
    fun `provides four preview states`() {
        assertThat(values).hasSize(4)
    }

    @Test
    fun `first state shows a word with the answer hidden`() {
        val state = values[0]

        assertThat(state.vocabularyItem).isNotNull()
        assertThat(state.showAnswer).isFalse()
        assertThat(state.unlocked).isFalse()
        assertThat(state.isLoading).isFalse()
    }

    @Test
    fun `second state shows a new word with the answer revealed`() {
        val state = values[1]

        assertThat(state.vocabularyItem?.isNew).isTrue()
        assertThat(state.showAnswer).isTrue()
        assertThat(state.isLoading).isFalse()
    }

    @Test
    fun `third state shows the answer revealed under an active rating lock`() {
        val state = values[2]

        assertThat(state.showAnswer).isTrue()
        assertThat(state.ratingDelaySeconds).isEqualTo(5)
        assertThat(state.ratingLockSecondsRemaining).isEqualTo(3)
    }

    @Test
    fun `fourth state is the loading state with no vocabulary item`() {
        val state = values[3]

        assertThat(state.vocabularyItem).isNull()
        assertThat(state.isLoading).isTrue()
    }
}
