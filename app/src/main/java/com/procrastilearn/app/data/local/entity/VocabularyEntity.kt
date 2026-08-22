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
        Index(value = ["normalizedWord"], unique = true),
        Index(value = ["backwardFsrsDueAt"]),
        Index(value = ["fsrsDueAt", "backwardFsrsDueAt"]),
        Index(value = ["fsrsDueAt", "backwardFsrsDueAt", "position"]),
        Index(value = ["position"], unique = true),
    ],
)
data class VocabularyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    // Avoid duplicate “Cat” vs “cat”
    @ColumnInfo(collate = ColumnInfo.NOCASE) val word: String,
    // Unicode-aware case fold of `word`, used for duplicate detection instead of SQLite's
    // COLLATE NOCASE (ASCII-only; doesn't fold e.g. é/É or Cyrillic case pairs). Kept in sync
    // via this default, except where `word` changes through .copy() - those call sites must
    // set this explicitly since .copy() does not re-run constructor default expressions.
    val normalizedWord: String = normalizeWord(word),
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
    val position: Long = 0L,
) {
    companion object {
        // No-arg lowercase() uses Locale.ROOT-equivalent rules, so folding is stable across
        // device locales (e.g. the Turkish dotless-I quirk can't apply here).
        fun normalizeWord(word: String): String = word.trim().lowercase()
    }
}
