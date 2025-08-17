package com.example.myapplication.domain.repository


import com.example.myapplication.domain.model.VocabularyItem
import kotlinx.coroutines.flow.Flow

interface VocabularyRepository {
    suspend fun getRandomVocabularyItem(): VocabularyItem
    fun observeCurrentItem(): Flow<VocabularyItem>
    suspend fun addVocabularyItem(item: VocabularyItem)
}