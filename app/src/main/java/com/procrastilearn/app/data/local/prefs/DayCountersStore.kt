package com.procrastilearn.app.data.local.prefs

import android.content.Context
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.procrastilearn.app.data.counter.DayCounters
import com.procrastilearn.app.domain.model.LearningPreferencesConfig
import com.procrastilearn.app.domain.model.MixMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DayCountersStore @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    private val ds =
        PreferenceDataStoreFactory.create(
            produceFile = { ctx.dataStoreFile("study_prefs.preferences_pb") },
        )

    private object K {
        // ── existing counters ────────────────────────────────
        val DAY = intPreferencesKey("day")
        val NEW_SHOWN = intPreferencesKey("new_shown")
        val REVIEW_SHOWN = intPreferencesKey("review_shown")
        val REVIEWS_SINCE_NEW = intPreferencesKey("reviews_since_new")

        // ── NEW: user policy (settings) ──────────────────────
        val MIX_MODE = stringPreferencesKey("mix_mode")
        val NEW_PER_DAY_LIMIT = intPreferencesKey("new_per_day_limit")
        val REVIEW_PER_DAY_LIMIT = intPreferencesKey("review_per_day_limit")
    }

    // Defaults for policy
    private companion object {
        const val DEFAULT_NEW_PER_DAY = 15
        const val DEFAULT_REVIEW_PER_DAY = 99
    }

    // ─────────────────────────────────────────────────────────
    // Existing API (unchanged)
    fun read(): Flow<DayCounters> =
        ds.data.map { p ->
            DayCounters(
                yyyymmdd = p[K.DAY] ?: 0,
                newShown = p[K.NEW_SHOWN] ?: 0,
                reviewShown = p[K.REVIEW_SHOWN] ?: 0,
                reviewsSinceLastNew = p[K.REVIEWS_SINCE_NEW] ?: 0,
            )
        }

    suspend fun resetFor(day: Int) {
        ds.edit { p ->
            p[K.DAY] = day
            p[K.NEW_SHOWN] = 0
            p[K.REVIEW_SHOWN] = 0
            p[K.REVIEWS_SINCE_NEW] = 0
        }
    }

    suspend fun markNewShown() {
        ds.edit { p ->
            p[K.NEW_SHOWN] = (p[K.NEW_SHOWN] ?: 0) + 1
            p[K.REVIEWS_SINCE_NEW] = 0
        }
    }

    suspend fun markReviewShown() {
        ds.edit { p ->
            p[K.REVIEW_SHOWN] = (p[K.REVIEW_SHOWN] ?: 0) + 1
            p[K.REVIEWS_SINCE_NEW] = (p[K.REVIEWS_SINCE_NEW] ?: 0) + 1
        }
    }

    // ─────────────────────────────────────────────────────────
    // NEW: policy (settings) API used by your Settings screen

    // Read the persisted settings as a single stream
    fun readPolicy(): Flow<LearningPreferencesConfig> =
        ds.data.map { p ->
            val mixName = p[K.MIX_MODE] ?: MixMode.MIX.name
            LearningPreferencesConfig(
                newPerDay = p[K.NEW_PER_DAY_LIMIT] ?: DEFAULT_NEW_PER_DAY,
                reviewPerDay = p[K.REVIEW_PER_DAY_LIMIT] ?: DEFAULT_REVIEW_PER_DAY,
                mixMode = runCatching { MixMode.valueOf(mixName) }.getOrDefault(MixMode.MIX),
                buryImmediateRepeat = true, // or load another flag if you add it later
            )
        }

    suspend fun setMixMode(mode: MixMode) {
        ds.edit { it[K.MIX_MODE] = mode.name }
    }

    suspend fun setNewPerDay(value: Int) {
        ds.edit { it[K.NEW_PER_DAY_LIMIT] = value.coerceIn(0, 200) }
    }

    suspend fun setReviewPerDay(value: Int) {
        ds.edit { it[K.REVIEW_PER_DAY_LIMIT] = value.coerceIn(0, 2000) }
    }
}
