package com.example.myapplication.domain.usecase

import com.example.myapplication.domain.model.VocabularyItem
import com.example.myapplication.domain.repository.VocabularyRepository
import javax.inject.Inject

class GetNextVocabularyItemUseCase @Inject constructor(
    private val vocabularyRepository: VocabularyRepository
) {
    suspend operator fun invoke(): Result<VocabularyItem> {
        return try {
            val item = vocabularyRepository.getRandomVocabularyItem()
            Result.success(item)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}