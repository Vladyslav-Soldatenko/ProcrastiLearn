package com.procrastilearn.app.data.local.prefs

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.procrastilearn.app.domain.model.SearchScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WordListSearchPreferencesStore
    @Inject
    constructor(
        studyPreferences: StudyPreferencesDataStore,
    ) {
        private val ds = studyPreferences.ds

        private object K {
            val MATCH_WORD = booleanPreferencesKey("word_list_search_match_word")
            val MATCH_TRANSLATION = booleanPreferencesKey("word_list_search_match_translation")
        }

        fun readScope(): Flow<SearchScope> =
            ds.data.map { preferences ->
                SearchScope(
                    matchWord = preferences[K.MATCH_WORD] ?: true,
                    matchTranslation = preferences[K.MATCH_TRANSLATION] ?: true,
                )
            }

        suspend fun setScope(scope: SearchScope) {
            ds.edit { preferences ->
                preferences[K.MATCH_WORD] = scope.matchWord
                preferences[K.MATCH_TRANSLATION] = scope.matchTranslation
            }
        }
    }
