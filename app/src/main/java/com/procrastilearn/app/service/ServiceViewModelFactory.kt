package com.procrastilearn.app.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.procrastilearn.app.data.local.prefs.PronunciationPreferencesStore
import com.procrastilearn.app.domain.usecase.GetNextVocabularyItemUseCase
import com.procrastilearn.app.domain.usecase.SaveDifficultyRatingUseCase
import com.procrastilearn.app.overlay.OverlayViewModel
import com.procrastilearn.app.tts.Speaker

class ServiceViewModelFactory(
    private val getNextVocabularyItemUseCase: GetNextVocabularyItemUseCase,
    private val saveDifficultyRatingUseCase: SaveDifficultyRatingUseCase,
    private val pronunciationPreferencesStore: PronunciationPreferencesStore,
    private val speaker: Speaker,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        when {
            modelClass.isAssignableFrom(OverlayViewModel::class.java) -> {
                OverlayViewModel(
                    getNextVocabularyItem = getNextVocabularyItemUseCase,
                    saveDifficultyRating = saveDifficultyRatingUseCase,
                    pronunciationPreferencesStore = pronunciationPreferencesStore,
                    speaker = speaker,
                ) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
}
