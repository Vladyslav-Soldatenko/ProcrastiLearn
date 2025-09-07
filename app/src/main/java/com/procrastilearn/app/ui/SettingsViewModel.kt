package com.procrastilearn.app.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.procrastilearn.app.data.local.dao.VocabularyDao
import com.procrastilearn.app.data.local.prefs.DayCountersStore
import com.procrastilearn.app.domain.model.MixMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

data class SettingsUiState(
    val mixMode: MixMode = MixMode.MIX,
    val newPerDay: Int = 10,
    val reviewPerDay: Int = 100,
    val overlayInterval: Int = 6,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: DayCountersStore,
    private val vocabularyDao: VocabularyDao,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> =
        store
            .readPolicy()
            .map { SettingsUiState(it.mixMode, it.newPerDay, it.reviewPerDay, it.overlayInterval) }
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

    fun onOverlayIntervalChange(value: Int) {
        viewModelScope.launch { store.setOverlayInterval(value) }
    }

    /**
     * Export all vocabulary rows (full DB fields) as a JSON array to the given [uri].
     * Calls [onComplete] on the main thread with success/failure.
     */
    fun exportVocabularyToUri(
        context: Context,
        uri: Uri,
        onComplete: (Boolean) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = try {
                val list = vocabularyDao.getAllVocabulary().first()
                val json = JSONArray().apply {
                    list.forEach { e ->
                        put(
                            JSONObject().apply {
                                put("id", e.id)
                                put("word", e.word)
                                put("translation", e.translation)
                                put("createdAt", e.createdAt)
                                if (e.lastShownAt == null) put("lastShownAt", JSONObject.NULL) else put("lastShownAt", e.lastShownAt)
                                put("correctCount", e.correctCount)
                                put("incorrectCount", e.incorrectCount)
                                put("fsrsCardJson", e.fsrsCardJson)
                                put("fsrsDueAt", e.fsrsDueAt)
                            },
                        )
                    }
                }

                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.writer(Charsets.UTF_8).use { writer ->
                        writer.write(json.toString())
                        writer.flush()
                    }
                } ?: false
                true
            } catch (t: Throwable) {
                false
            }

            withContext(Dispatchers.Main) { onComplete(ok) }
        }
    }
}
