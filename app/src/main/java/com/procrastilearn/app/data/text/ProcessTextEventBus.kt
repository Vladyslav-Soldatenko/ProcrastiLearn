package com.procrastilearn.app.data.text

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges an incoming ACTION_PROCESS_TEXT intent from [com.procrastilearn.app.MainActivity]
 * to the Add-Word screen, since nav routes take no arguments and the receiving ViewModel
 * may not exist yet when the intent arrives.
 */
@Singleton
class ProcessTextEventBus
    @Inject
    constructor() {
        private val _events = MutableStateFlow<String?>(null)
        val events: StateFlow<String?> = _events.asStateFlow()

        fun submit(text: String) {
            _events.value = text
        }

        fun consume() {
            _events.value = null
        }
    }
