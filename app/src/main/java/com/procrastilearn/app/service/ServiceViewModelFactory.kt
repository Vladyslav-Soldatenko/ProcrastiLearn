package com.procrastilearn.app.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.procrastilearn.app.data.local.prefs.DayCountersStore
import com.procrastilearn.app.domain.usecase.GetNextVocabularyItemUseCase
import com.procrastilearn.app.domain.usecase.SaveDifficultyRatingUseCase
import com.procrastilearn.app.overlay.OverlayViewModel

class ServiceViewModelFactory(
    private val getNextVocabularyItemUseCase: GetNextVocabularyItemUseCase,
    private val saveDifficultyRatingUseCase: SaveDifficultyRatingUseCase,
    private val dayCountersStore: DayCountersStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        when {
            modelClass.isAssignableFrom(OverlayViewModel::class.java) -> {
                OverlayViewModel(
                    getNextVocabularyItem = getNextVocabularyItemUseCase,
                    saveDifficultyRating = saveDifficultyRatingUseCase,
                    dayCountersStore = dayCountersStore,
                ) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
}
