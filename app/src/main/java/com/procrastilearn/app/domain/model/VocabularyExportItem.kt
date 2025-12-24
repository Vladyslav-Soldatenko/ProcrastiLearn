package com.procrastilearn.app.domain.model

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
)
