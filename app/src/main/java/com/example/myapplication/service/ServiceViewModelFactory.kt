package com.example.myapplication.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.myapplication.domain.usecase.GetNextVocabularyItemUseCase
import com.example.myapplication.presentation.overlay.OverlayViewModel

class ServiceViewModelFactory(
    private val getNextVocabularyItemUseCase: GetNextVocabularyItemUseCase,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(OverlayViewModel::class.java) -> {
                OverlayViewModel(
                    getNextVocabularyItem = getNextVocabularyItemUseCase,
                ) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}