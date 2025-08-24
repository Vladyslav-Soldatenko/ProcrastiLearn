package com.example.myapplication.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.AppRepository
import com.example.myapplication.domain.model.AppInfo
import com.example.myapplication.domain.repository.AppPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppsViewModel
    @Inject
    constructor(
        private val appRepository: AppRepository,
        private val appPreferencesRepository: AppPreferencesRepository,
    ) : ViewModel() {
        data class UiState(
            val apps: List<AppInfo> = emptyList(),
            val selected: Set<String> = emptySet(),
            val isLoading: Boolean = true,
            val error: String? = null,
        )

        private val _state = MutableStateFlow(UiState())
        val state: StateFlow<UiState> = _state.asStateFlow()

        init {
            observeSelectedApps()
            refresh()
        }

        private fun observeSelectedApps() {
            appPreferencesRepository
                .getBlockedApps()
                .onEach { selectedApps ->
                    _state.update { it.copy(selected = selectedApps) }
                }.launchIn(viewModelScope)
        }

        fun refresh() =
            viewModelScope.launch {
                _state.update { it.copy(isLoading = true, error = null) }

                runCatching {
                    appRepository.loadLaunchableApps()
                }.fold(
                    onSuccess = { apps ->
                        _state.update { it.copy(apps = apps, isLoading = false) }
                    },
                    onFailure = { exception ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                error = exception.message ?: "Unknown error occurred",
                            )
                        }
                    },
                )
            }

        fun toggle(app: AppInfo) {
            val key = app.packageName
            viewModelScope.launch {
                appPreferencesRepository.toggleApp(key)
            }
        }
    }
