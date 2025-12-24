package com.procrastilearn.app.data.local.mapper

import com.procrastilearn.app.data.local.entity.VocabularyEntity
import com.procrastilearn.app.domain.model.VocabularyExportItem
import com.procrastilearn.app.domain.model.VocabularyItem

fun VocabularyEntity.toDomain(): VocabularyItem =
    VocabularyItem(
        id = id,
        word = word,
        translation = translation,
        isNew =
            this.correctCount == 0 && this.incorrectCount == 0,
    )

fun VocabularyItem.toEntity(
    fsrsCardJson: String = "",
    fsrsDueAt: Long = 0L,
): VocabularyEntity =
    VocabularyEntity(
        id = id,
        word = word,
        translation = translation,
        fsrsCardJson = fsrsCardJson,
        fsrsDueAt = fsrsDueAt,
    )

fun VocabularyExportItem.toEntity(): VocabularyEntity =
    VocabularyEntity(
        id = id,
        word = word,
        translation = translation,
        createdAt = createdAt,
        lastShownAt = lastShownAt,
        correctCount = correctCount,
        incorrectCount = incorrectCount,
        fsrsCardJson = fsrsCardJson,
        fsrsDueAt = fsrsDueAt,
    )
