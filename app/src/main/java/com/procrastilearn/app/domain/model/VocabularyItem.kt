package com.procrastilearn.app.domain.model

enum class StudyDirection { FORWARD, BACKWARD }

data class VocabularyItem(
    val id: Long = 0,
    val word: String,
    val translation: String,
    val isNew: Boolean,
    val direction: StudyDirection = StudyDirection.FORWARD,
    val bidirectional: Boolean = false,
    val backwardPromptOverride: String? = null,
    val backwardAnswerOverride: String? = null,
    val position: Long = 0L,
)
