package com.example.myapplication.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.myapplication.domain.repository.VocabularyRepository
import com.example.myapplication.overlay.OverlayViewModel


class ServiceViewModelFactory(
    private val vocabularyRepository: VocabularyRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(OverlayViewModel::class.java) -> {
                OverlayViewModel(vocabularyRepository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}