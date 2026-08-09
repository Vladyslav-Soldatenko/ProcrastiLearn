package com.procrastilearn.app.ui

internal data class BidirectionalFields(
    val bidirectional: Boolean = false,
    val isCustomizingBackward: Boolean = false,
    val backwardPromptOverride: String = "",
    val backwardAnswerOverride: String = "",
)

internal fun BidirectionalFields.toggled(checked: Boolean): BidirectionalFields =
    copy(
        bidirectional = checked,
        isCustomizingBackward = if (checked) isCustomizingBackward else false,
        backwardPromptOverride = if (checked) backwardPromptOverride else "",
        backwardAnswerOverride = if (checked) backwardAnswerOverride else "",
    )

internal fun BidirectionalFields.toCardOptions(): BidirectionalCardOptions =
    BidirectionalCardOptions(
        bidirectional = bidirectional,
        backwardPromptOverride = backwardPromptOverride.ifBlank { null },
        backwardAnswerOverride = backwardAnswerOverride.ifBlank { null },
    )
