package com.procrastilearn.app.data.local.entity

// A vocabulary row's full bidirectional FSRS scheduling state: the forward and backward
// card JSON, due dates, and review counters. Shared shape between VocabularyEntity (the
// live row), UndoSnapshotEntity (the state captured before a rating), and the restore
// path that writes a snapshot back, so all three describe "this" the same way.
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
