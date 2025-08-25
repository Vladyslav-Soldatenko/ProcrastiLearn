package com.procrastilearn.app.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.procrastilearn.app.domain.usecase.GetNextVocabularyItemUseCase
import com.procrastilearn.app.domain.usecase.SaveDifficultyRatingUseCase
import com.procrastilearn.app.overlay.OverlayViewModel

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
