package com.procrastilearn.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class VocabularyExportItem(
    val id: Long,
    val word: String,
    val translation: String,
    val createdAt: Long,
    val lastShownAt: Long?,
    val correctCount: Int,
    val incorrectCount: Int,
    val fsrsCardJson: String,
    val fsrsDueAt: Long,
    val bidirectional: Boolean = false,
    val backwardFsrsCardJson: String = "",
    val backwardFsrsDueAt: Long = 0L,
    val backwardCorrectCount: Int = 0,
    val backwardIncorrectCount: Int = 0,
    val backwardPromptOverride: String? = null,
    val backwardAnswerOverride: String? = null,
    val position: Long = 0L,
)
