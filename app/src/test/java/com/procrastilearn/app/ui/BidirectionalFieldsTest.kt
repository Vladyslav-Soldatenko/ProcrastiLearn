package com.procrastilearn.app.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BidirectionalFieldsTest {
    @Test
    fun `toggled true sets bidirectional and preserves existing customize state`() {
        val fields =
            BidirectionalFields(
                bidirectional = false,
                isCustomizingBackward = true,
                backwardPromptOverride = "prompt",
                backwardAnswerOverride = "answer",
            )

        val result = fields.toggled(true)

        assertThat(result.bidirectional).isTrue()
        assertThat(result.isCustomizingBackward).isTrue()
        assertThat(result.backwardPromptOverride).isEqualTo("prompt")
        assertThat(result.backwardAnswerOverride).isEqualTo("answer")
    }

    @Test
    fun `toggled false clears bidirectional customize flag and both overrides`() {
        val fields =
            BidirectionalFields(
                bidirectional = true,
                isCustomizingBackward = true,
                backwardPromptOverride = "prompt",
                backwardAnswerOverride = "answer",
            )

        val result = fields.toggled(false)

        assertThat(result.bidirectional).isFalse()
        assertThat(result.isCustomizingBackward).isFalse()
        assertThat(result.backwardPromptOverride).isEmpty()
        assertThat(result.backwardAnswerOverride).isEmpty()
    }

    @Test
    fun `toggled true after a prior toggled false does not restore previously cleared overrides`() {
        val fields =
            BidirectionalFields(
                bidirectional = true,
                isCustomizingBackward = true,
                backwardPromptOverride = "prompt",
                backwardAnswerOverride = "answer",
            )

        val result = fields.toggled(false).toggled(true)

        assertThat(result.bidirectional).isTrue()
        assertThat(result.isCustomizingBackward).isFalse()
        assertThat(result.backwardPromptOverride).isEmpty()
        assertThat(result.backwardAnswerOverride).isEmpty()
    }

    @Test
    fun `toCardOptions maps a blank prompt override to null`() {
        val fields = BidirectionalFields(bidirectional = true, backwardPromptOverride = "")

        val options = fields.toCardOptions()

        assertThat(options.backwardPromptOverride).isNull()
    }

    @Test
    fun `toCardOptions maps a whitespace-only answer override to null`() {
        val fields = BidirectionalFields(bidirectional = true, backwardAnswerOverride = "   ")

        val options = fields.toCardOptions()

        assertThat(options.backwardAnswerOverride).isNull()
    }

    @Test
    fun `toCardOptions preserves non-blank override text unchanged without trimming`() {
        val fields =
            BidirectionalFields(
                bidirectional = true,
                backwardPromptOverride = " custom prompt ",
                backwardAnswerOverride = " custom answer ",
            )

        val options = fields.toCardOptions()

        assertThat(options.backwardPromptOverride).isEqualTo(" custom prompt ")
        assertThat(options.backwardAnswerOverride).isEqualTo(" custom answer ")
    }

    @Test
    fun `toCardOptions carries bidirectional false through correctly`() {
        val fields = BidirectionalFields(bidirectional = false)

        val options = fields.toCardOptions()

        assertThat(options.bidirectional).isFalse()
    }
}
