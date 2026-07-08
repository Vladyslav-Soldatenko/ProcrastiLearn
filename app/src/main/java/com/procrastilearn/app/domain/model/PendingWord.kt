package com.procrastilearn.app.domain.model

data class PendingWord(
    val id: Long = 0,
    val word: String,
    val direction: AiTranslationDirection,
    val createdAt: Long = System.currentTimeMillis(),
)
