package com.procrastilearn.app.domain.model

import io.github.openspacedrepetition.Rating

data class UndoResult(
    val item: VocabularyItem,
    val revertedRating: Rating,
)
