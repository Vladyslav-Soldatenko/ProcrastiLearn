package com.procrastilearn.app.ui.views

import androidx.lifecycle.ViewModel
import com.procrastilearn.app.data.text.ProcessTextEventBus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        processTextEventBus: ProcessTextEventBus,
    ) : ViewModel() {
        val processTextEvents: StateFlow<String?> = processTextEventBus.events
    }
