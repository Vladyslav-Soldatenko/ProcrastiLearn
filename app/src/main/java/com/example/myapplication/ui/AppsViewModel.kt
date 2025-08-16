package com.example.myapplication.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.AppRepository
import com.example.myapplication.model.AppInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class AppsViewModel @Inject constructor(
    private val repo: AppRepository
) : ViewModel() {

    data class UiState(
        val apps: List<AppInfo> = emptyList(),
        val selected: Set<String> = emptySet(), // key = packageName/activityName
        val isLoading: Boolean = true,
        val error: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(isLoading = true, error = null) }
        runCatching { repo.loadLaunchableApps() }
            .onSuccess { list ->
                _state.update { it.copy(apps = list, isLoading = false) }
            }
            .onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
    }

    fun toggle(app: AppInfo) {
        val key = "${app.packageName}/${app.activityName}"
        _state.update { s ->
            val next = s.selected.toMutableSet().apply {
                if (!add(key)) remove(key)
            }
            s.copy(selected = next)
        }
    }
}
