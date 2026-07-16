package com.procrastilearn.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "undo_snapshot",
    indices = [
        Index(value = ["vocabId"]),
        Index(value = ["createdAt"]),
    ],
)
data class UndoSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vocabId: Long,
    val createdAt: Long,
    val snapshotDay: Int,
    val ratingName: String,
    // Row state before the rating
    val fsrsCardJson: String,
    val fsrsDueAt: Long,
    val lastShownAt: Long?,
    val correctCount: Int,
    val incorrectCount: Int,
    // Day-counter state before the rating
    val newShown: Int,
    val reviewShown: Int,
    val reviewsSinceLastNew: Int,
)
