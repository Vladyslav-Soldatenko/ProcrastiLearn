package com.procrastilearn.app.data.local.entity

data class VocabularyFsrsState(
    val fsrsCardJson: String,
    val fsrsDueAt: Long,
    val lastShownAt: Long?,
    val correctCount: Int,
    val incorrectCount: Int,
    val backwardFsrsCardJson: String,
    val backwardFsrsDueAt: Long,
    val backwardCorrectCount: Int,
    val backwardIncorrectCount: Int,
)
