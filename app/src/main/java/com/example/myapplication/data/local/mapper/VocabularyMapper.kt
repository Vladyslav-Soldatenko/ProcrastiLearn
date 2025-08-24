package com.example.myapplication.data.local.mapper

import com.example.myapplication.data.local.entity.VocabularyEntity
import com.example.myapplication.domain.model.VocabularyItem

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
