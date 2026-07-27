package com.procrastilearn.app.domain.model

enum class MixMode { MIX, REVIEWS_FIRST, NEW_FIRST }

enum class StudyDirectionMode { FORWARD, BACKWARD, BIDIRECTIONAL }

val StudyDirectionMode.includesForward: Boolean get() = this != StudyDirectionMode.BACKWARD
val StudyDirectionMode.includesBackward: Boolean get() = this != StudyDirectionMode.FORWARD
val StudyDirectionMode.isBackwardOnly: Boolean get() = this == StudyDirectionMode.BACKWARD

data class LearningPreferencesConfig(
    val newPerDay: Int = 20,
    val reviewPerDay: Int = 200,
    val mixMode: MixMode = MixMode.MIX, // MIX | REVIEWS_FIRST | NEW_FIRST
    val overlayInterval: Int = 6,
    val studyDirectionMode: StudyDirectionMode = StudyDirectionMode.FORWARD,
)
