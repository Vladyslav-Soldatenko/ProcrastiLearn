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
    fun `toggled false keeps both overrides`() {
        val fields =
            BidirectionalFields(
                bidirectional = true,
                isCustomizingBackward = true,
                backwardPromptOverride = "prompt",
                backwardAnswerOverride = "answer",
            )

        val result = fields.toggled(false)

        assertThat(result.bidirectional).isFalse()
        assertThat(result.backwardPromptOverride).isEqualTo("prompt")
        assertThat(result.backwardAnswerOverride).isEqualTo("answer")
    }

    @Test
    fun `toggled false keeps the customize flag`() {
        val fields =
            BidirectionalFields(
                bidirectional = true,
                isCustomizingBackward = true,
                backwardPromptOverride = "prompt",
                backwardAnswerOverride = "answer",
            )

        val result = fields.toggled(false)

        assertThat(result.isCustomizingBackward).isTrue()
    }

    @Test
    fun `toggled false then true round-trips back to the original fields`() {
        val fields =
            BidirectionalFields(
                bidirectional = true,
                isCustomizingBackward = true,
                backwardPromptOverride = "prompt",
                backwardAnswerOverride = "answer",
            )

        val result = fields.toggled(false).toggled(true)

        assertThat(result).isEqualTo(fields)
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

    @Test
    fun `toCardOptions emits overrides even when bidirectional is false`() {
        val fields =
            BidirectionalFields(
                bidirectional = false,
                backwardPromptOverride = "prompt",
                backwardAnswerOverride = "answer",
            )

        val options = fields.toCardOptions()

        assertThat(options.backwardPromptOverride).isEqualTo("prompt")
        assertThat(options.backwardAnswerOverride).isEqualTo("answer")
    }
}
