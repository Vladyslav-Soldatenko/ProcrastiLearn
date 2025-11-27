package com.procrastilearn.app.domain.model

enum class MixMode { MIX, REVIEWS_FIRST, NEW_FIRST }

data class LearningPreferencesConfig(
    val newPerDay: Int = 20,
    val reviewPerDay: Int = 200,
    val mixMode: MixMode = MixMode.MIX, // MIX | REVIEWS_FIRST | NEW_FIRST
    val overlayInterval: Int = 6,
)
