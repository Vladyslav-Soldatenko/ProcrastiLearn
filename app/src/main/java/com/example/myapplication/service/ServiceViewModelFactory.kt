package com.example.myapplication.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.myapplication.domain.usecase.GetNextVocabularyItemUseCase
import com.example.myapplication.domain.usecase.SaveDifficultyRatingUseCase
import com.example.myapplication.overlay.OverlayViewModel

class ServiceViewModelFactory(
    private val getNextVocabularyItemUseCase: GetNextVocabularyItemUseCase,
    private val saveDifficultyRatingUseCase: SaveDifficultyRatingUseCase,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        when {
            modelClass.isAssignableFrom(OverlayViewModel::class.java) -> {
                OverlayViewModel(
                    getNextVocabularyItem = getNextVocabularyItemUseCase,
                    saveDifficultyRating = saveDifficultyRatingUseCase,
                ) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
}
