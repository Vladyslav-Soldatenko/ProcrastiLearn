package com.procrastilearn.app.data.local.prefs

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.procrastilearn.app.domain.model.Language
import com.procrastilearn.app.domain.model.LanguagePair
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LanguagePreferencesStore
    @Inject
    constructor(
        studyPreferences: StudyPreferencesDataStore,
    ) {
        private val ds = studyPreferences.ds

        private object K {
            val NATIVE_LANGUAGE_CODE = stringPreferencesKey("native_language_code")
            val TARGET_LANGUAGE_CODE = stringPreferencesKey("target_language_code")
        }

        fun readLanguagePair(): Flow<LanguagePair?> =
            ds.data.map { p ->
                val native = Language.fromCode(p[K.NATIVE_LANGUAGE_CODE])
                val target = Language.fromCode(p[K.TARGET_LANGUAGE_CODE])
                if (native != null && target != null) LanguagePair(native, target) else null
            }

        suspend fun setLanguagePair(
            native: Language,
            target: Language,
        ) {
            ds.edit { p ->
                p[K.NATIVE_LANGUAGE_CODE] = native.code
                p[K.TARGET_LANGUAGE_CODE] = target.code
            }
        }
    }
