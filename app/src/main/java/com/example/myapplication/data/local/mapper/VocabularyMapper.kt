package com.example.myapplication.data.local.mapper

import com.example.myapplication.data.local.entity.VocabularyEntity
import com.example.myapplication.domain.model.VocabularyItem

fun VocabularyEntity.toDomainModel(): VocabularyItem {
    return VocabularyItem(
        word = word,
        translation = translation
    )
}

fun VocabularyItem.toEntity(): VocabularyEntity {
    return VocabularyEntity(
        word = word,
        translation = translation
    )
}