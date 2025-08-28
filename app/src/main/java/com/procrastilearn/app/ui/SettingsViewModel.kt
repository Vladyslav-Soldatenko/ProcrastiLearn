package com.procrastilearn.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.procrastilearn.app.data.local.prefs.DayCountersStore
import com.procrastilearn.app.domain.model.MixMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val mixMode: MixMode = MixMode.MIX,
    val newPerDay: Int = 10,
    val reviewPerDay: Int = 100,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: DayCountersStore,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> =
        store
            .readPolicy()
            .map { SettingsUiState(it.mixMode, it.newPerDay, it.reviewPerDay) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun onMixModeChange(mode: MixMode) {
        viewModelScope.launch { store.setMixMode(mode) }
    }

    fun onNewPerDayChange(value: Int) {
        viewModelScope.launch { store.setNewPerDay(value) }
    }

    fun onReviewPerDayChange(value: Int) {
        viewModelScope.launch { store.setReviewPerDay(value) }
    }
}
