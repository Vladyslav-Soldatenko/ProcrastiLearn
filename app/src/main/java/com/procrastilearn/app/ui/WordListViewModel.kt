package com.procrastilearn.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.repository.VocabularyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WordListViewModel
    @Inject
    constructor(
        private val repository: VocabularyRepository,
    ) : ViewModel() {
        val words =
            repository
                .getAllVocabulary()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList(),
                )

        fun deleteWord(item: VocabularyItem) {
            viewModelScope.launch {
                repository.deleteVocabularyItem(item)
            }
        }

        fun updateWord(item: VocabularyItem) {
            viewModelScope.launch {
                repository.updateVocabularyItem(item)
            }
        }
    }
