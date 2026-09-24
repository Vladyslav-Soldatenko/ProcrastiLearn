package com.procrastilearn.app.domain.model

enum class MixMode { MIX, REVIEWS_FIRST, NEW_FIRST }

enum class NewCardOrder { RANDOM, SEQUENTIAL }

enum class StudyDirectionMode { FORWARD, BACKWARD, BIDIRECTIONAL }

val StudyDirectionMode.includesForward: Boolean get() = this != StudyDirectionMode.BACKWARD
val StudyDirectionMode.includesBackward: Boolean get() = this != StudyDirectionMode.FORWARD
val StudyDirectionMode.isBackwardOnly: Boolean get() = this == StudyDirectionMode.BACKWARD

const val DEFAULT_MAXIMUM_INTERVAL_DAYS = 365
const val MIN_MAXIMUM_INTERVAL_DAYS = 1
const val MAX_MAXIMUM_INTERVAL_DAYS = 36500

data class LearningPreferencesConfig(
    val newPerDay: Int = 20,
    val reviewPerDay: Int = 200,
    val maximumIntervalDays: Int = DEFAULT_MAXIMUM_INTERVAL_DAYS,
    val mixMode: MixMode = MixMode.MIX, // MIX | REVIEWS_FIRST | NEW_FIRST
    val overlayInterval: Int = 6,
    val studyDirectionMode: StudyDirectionMode = StudyDirectionMode.FORWARD,
    val ratingDelaySeconds: Int = 0,
    val newCardOrder: NewCardOrder = NewCardOrder.SEQUENTIAL,
)
