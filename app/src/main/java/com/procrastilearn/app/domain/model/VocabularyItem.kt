package com.procrastilearn.app.domain.model

data class VocabularyItem(
    val id: Long = 0,
    val word: String,
    val translation: String,
    val isNew: Boolean
)
