package com.procrastilearn.app.data.local.mapper

import com.procrastilearn.app.data.local.entity.UndoSnapshotEntity
import com.procrastilearn.app.data.local.entity.VocabularyEntity
import com.procrastilearn.app.data.local.entity.VocabularyFsrsState
import com.procrastilearn.app.domain.model.StudyDirection
import com.procrastilearn.app.domain.model.VocabularyExportItem
import com.procrastilearn.app.domain.model.VocabularyItem

fun VocabularyEntity.toDomain(direction: StudyDirection = StudyDirection.FORWARD): VocabularyItem {
    val isNew = fsrsDueAt == 0L && backwardFsrsDueAt == 0L
    return when (direction) {
        StudyDirection.FORWARD ->
            VocabularyItem(
                id = id,
                word = word,
                translation = translation,
                isNew = isNew,
                direction = StudyDirection.FORWARD,
                bidirectional = bidirectional,
                backwardPromptOverride = backwardPromptOverride,
                backwardAnswerOverride = backwardAnswerOverride,
            )
        StudyDirection.BACKWARD ->
            VocabularyItem(
                id = id,
                word = backwardPromptOverride ?: translation,
                translation = backwardAnswerOverride ?: word,
                isNew = isNew,
                direction = StudyDirection.BACKWARD,
                bidirectional = bidirectional,
                backwardPromptOverride = backwardPromptOverride,
                backwardAnswerOverride = backwardAnswerOverride,
            )
    }
}

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
        bidirectional = bidirectional,
        backwardPromptOverride = backwardPromptOverride,
        backwardAnswerOverride = backwardAnswerOverride,
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
        bidirectional = bidirectional,
        backwardFsrsCardJson = backwardFsrsCardJson,
        backwardFsrsDueAt = backwardFsrsDueAt,
        backwardCorrectCount = backwardCorrectCount,
        backwardIncorrectCount = backwardIncorrectCount,
        backwardPromptOverride = backwardPromptOverride,
        backwardAnswerOverride = backwardAnswerOverride,
    )

fun VocabularyEntity.toFsrsState(): VocabularyFsrsState =
    VocabularyFsrsState(
        fsrsCardJson = fsrsCardJson,
        fsrsDueAt = fsrsDueAt,
        lastShownAt = lastShownAt,
        correctCount = correctCount,
        incorrectCount = incorrectCount,
        backwardFsrsCardJson = backwardFsrsCardJson,
        backwardFsrsDueAt = backwardFsrsDueAt,
        backwardCorrectCount = backwardCorrectCount,
        backwardIncorrectCount = backwardIncorrectCount,
    )

fun UndoSnapshotEntity.toFsrsState(): VocabularyFsrsState =
    VocabularyFsrsState(
        fsrsCardJson = fsrsCardJson,
        fsrsDueAt = fsrsDueAt,
        lastShownAt = lastShownAt,
        correctCount = correctCount,
        incorrectCount = incorrectCount,
        backwardFsrsCardJson = backwardFsrsCardJson,
        backwardFsrsDueAt = backwardFsrsDueAt,
        backwardCorrectCount = backwardCorrectCount,
        backwardIncorrectCount = backwardIncorrectCount,
    )

fun VocabularyEntity.toExportItem(): VocabularyExportItem =
    VocabularyExportItem(
        id = id,
        word = word,
        translation = translation,
        createdAt = createdAt,
        lastShownAt = lastShownAt,
        correctCount = correctCount,
        incorrectCount = incorrectCount,
        fsrsCardJson = fsrsCardJson,
        fsrsDueAt = fsrsDueAt,
        bidirectional = bidirectional,
        backwardFsrsCardJson = backwardFsrsCardJson,
        backwardFsrsDueAt = backwardFsrsDueAt,
        backwardCorrectCount = backwardCorrectCount,
        backwardIncorrectCount = backwardIncorrectCount,
        backwardPromptOverride = backwardPromptOverride,
        backwardAnswerOverride = backwardAnswerOverride,
    )
