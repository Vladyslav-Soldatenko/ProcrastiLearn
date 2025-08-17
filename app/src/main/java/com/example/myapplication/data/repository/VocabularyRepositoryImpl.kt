package com.example.myapplication.data.repository

import com.example.myapplication.domain.model.VocabularyItem
import com.example.myapplication.domain.repository.VocabularyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
@Singleton
class VocabularyRepositoryImpl @Inject constructor() : VocabularyRepository {
    // Temporary hardcoded data - will be replaced with database later
    private val vocabularyDatabase = listOf(
        VocabularyItem("Cat", "кот"),
        VocabularyItem("Dog", "собака"),
        VocabularyItem("Water", "вода"),
        VocabularyItem("Book", "книга"),
        VocabularyItem("Phone", "телефон"),
        VocabularyItem("Computer", "компьютер"),
        VocabularyItem("Happiness", "счастье"),
        VocabularyItem("Friend", "друг"),
        VocabularyItem("Family", "семья"),
        VocabularyItem("Knowledge", "знание"),
    )
    private val _currentItem = MutableStateFlow(getDefaultItem())

    override suspend fun getRandomVocabularyItem(): VocabularyItem {
        val item = vocabularyDatabase[Random.nextInt(vocabularyDatabase.size)]
        _currentItem.value = item
        return item
    }

    override fun observeCurrentItem(): Flow<VocabularyItem> {
        return _currentItem.asStateFlow()
    }

    private fun getDefaultItem() = vocabularyDatabase.first()
}