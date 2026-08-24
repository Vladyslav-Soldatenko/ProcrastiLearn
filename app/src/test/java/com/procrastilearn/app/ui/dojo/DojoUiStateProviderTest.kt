package com.procrastilearn.app.ui.dojo

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DojoUiStateProviderTest {
    private val values = DojoUiStateProvider().values.toList()

    @Test
    fun `provides four preview states`() {
        assertThat(values).hasSize(4)
    }

    @Test
    fun `first state is loading with no vocabulary item`() {
        val state = values[0]

        assertThat(state.isLoading).isTrue()
        assertThat(state.vocabularyItem).isNull()
        assertThat(state.hasNoWords).isFalse()
        assertThat(state.newQuotaRemaining).isEqualTo(15)
        assertThat(state.pendingReviewCount).isEqualTo(10)
    }

    @Test
    fun `second state shows a flashcard with the answer hidden`() {
        val state = values[1]

        assertThat(state.vocabularyItem).isNotNull()
        assertThat(state.showAnswer).isFalse()
        assertThat(state.isLoading).isFalse()
        assertThat(state.hasNoWords).isFalse()
    }

    @Test
    fun `third state shows a flashcard with the answer revealed`() {
        val state = values[2]

        assertThat(state.vocabularyItem).isNotNull()
        assertThat(state.showAnswer).isTrue()
        assertThat(state.isLoading).isFalse()
    }

    @Test
    fun `fourth state has no words left and reports hasNoWords`() {
        val state = values[3]

        assertThat(state.vocabularyItem).isNull()
        assertThat(state.isLoading).isFalse()
        assertThat(state.hasNoWords).isTrue()
        assertThat(state.newQuotaRemaining).isEqualTo(0)
        assertThat(state.pendingReviewCount).isEqualTo(0)
    }
}
