package com.procrastilearn.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vocabulary",
    indices = [
        Index(value = ["fsrsDueAt"]),
        Index(value = ["correctCount", "incorrectCount"]),
        Index(value = ["word"], unique = true),
        Index(value = ["backwardFsrsDueAt"]),
        Index(value = ["fsrsDueAt", "backwardFsrsDueAt"]),
    ],
)
data class VocabularyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // Avoid duplicate “Cat” vs “cat”
    @ColumnInfo(collate = ColumnInfo.NOCASE) val word: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val translation: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastShownAt: Long? = null,
    val correctCount: Int = 0,
    val incorrectCount: Int = 0,
    // FSRS
    val fsrsCardJson: String = "",
    val fsrsDueAt: Long = 0L,
    // Bidirectional review: backward direction is scheduled independently on the same row.
    val bidirectional: Boolean = false,
    val backwardFsrsCardJson: String = "",
    val backwardFsrsDueAt: Long = 0L,
    val backwardCorrectCount: Int = 0,
    val backwardIncorrectCount: Int = 0,
    // null = derive backward prompt/answer by swapping translation/word at read time
    val backwardPromptOverride: String? = null,
    val backwardAnswerOverride: String? = null,
)
