package com.example.myapplication.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vocabulary")
data class VocabularyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val word: String,
    val translation: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastShownAt: Long? = null,
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,

    // --- FSRS persistence ---
    // JSON blob of io.github.openspacedrepetition.Card (see library README). :contentReference[oaicite:3]{index=3}
    val fsrsCardJson: String = "",

    // Cached next due time (epoch millis) for SQL filtering/sorting.
    val fsrsDueAt: Long = 0L
)
