package com.procrastilearn.app.data.local.mapper

import com.procrastilearn.app.data.local.entity.VocabularyEntity
import com.procrastilearn.app.domain.model.VocabularyItem

fun VocabularyEntity.toDomainModel(): VocabularyItem =
    VocabularyItem(
        id = id,
        word = word,
        translation = translation,
    )

fun VocabularyItem.toEntity(): VocabularyEntity =
    VocabularyEntity(
        id = id,
        word = word,
        translation = translation,
    )
