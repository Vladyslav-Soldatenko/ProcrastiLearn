package com.procrastilearn.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.repository.VocabularyCatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WordListViewModel
    @Inject
    constructor(
        private val repository: VocabularyCatalogRepository,
    ) : ViewModel() {
        data class SelectionState(
            val isActive: Boolean = false,
            val selectedIds: Set<Long> = emptySet(),
        )

        val words =
            repository
                .getAllVocabulary()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = emptyList(),
                )

        private val _selectionState = MutableStateFlow(SelectionState())
        val selectionState: StateFlow<SelectionState> = _selectionState.asStateFlow()

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

        fun resetWordProgress(item: VocabularyItem) {
            viewModelScope.launch {
                repository.resetVocabularyProgress(item)
            }
        }

        fun enterSelectionMode(initialSelectedId: Long) {
            _selectionState.value = SelectionState(isActive = true, selectedIds = setOf(initialSelectedId))
        }

        fun toggleSelection(id: Long) {
            _selectionState.update { state ->
                if (!state.isActive) return@update state
                val next = if (id in state.selectedIds) state.selectedIds - id else state.selectedIds + id
                state.copy(selectedIds = next)
            }
        }

        fun selectAll(ids: List<Long>) {
            _selectionState.update { it.copy(selectedIds = it.selectedIds + ids) }
        }

        fun deselectAll() {
            _selectionState.update { it.copy(selectedIds = emptySet()) }
        }

        fun exitSelectionMode() {
            _selectionState.value = SelectionState()
        }

        fun deleteSelectedWords() {
            val ids = _selectionState.value.selectedIds
            if (ids.isEmpty()) return
            val itemsToDelete = words.value.filter { it.id in ids }
            viewModelScope.launch {
                repository.deleteVocabularyItems(itemsToDelete)
                exitSelectionMode()
            }
        }

        fun setSelectedWordsBidirectional(bidirectional: Boolean) {
            val ids = selectedIdsNeedingChange(bidirectional)
            if (ids.isEmpty()) return
            viewModelScope.launch {
                repository.setBidirectional(ids, bidirectional)
                exitSelectionMode()
            }
        }

        // The set-equality guard is a correctness guard, not just tidiness: it's what keeps
        // repository.reorderVocabulary from ever being called with a partial id set, which
        // would leave excluded rows' stale positions colliding with the newly-assigned ones.
        // Reordering is a low-stakes convenience action, so unlike every other method here, a
        // repository failure is swallowed rather than left to propagate - the UI already
        // reflects the attempted order locally and will resync to the last-persisted order on
        // the next `words` emission.
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        fun reorderWords(orderedIds: List<Long>) {
            val currentIds = words.value.map { it.id }
            if (orderedIds.size < 2 || currentIds.size < 2) return
            if (orderedIds.toSet() != currentIds.toSet()) return
            if (orderedIds == currentIds) return
            viewModelScope.launch {
                try {
                    repository.reorderVocabulary(orderedIds)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Swallowed intentionally - see comment above.
                }
            }
        }

        private fun selectedIdsNeedingChange(bidirectional: Boolean): Set<Long> {
            val selected = _selectionState.value.selectedIds
            return words.value
                .filter { it.id in selected && it.bidirectional != bidirectional }
                .map { it.id }
                .toSet()
        }
    }
