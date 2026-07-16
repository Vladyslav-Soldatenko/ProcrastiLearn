package com.procrastilearn.app.data.local.prefs

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.procrastilearn.app.data.counter.DayCounters
import com.procrastilearn.app.domain.model.LearningPreferencesConfig
import com.procrastilearn.app.domain.model.MixMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DayCountersStore
    @Inject
    constructor(
        studyPreferences: StudyPreferencesDataStore,
    ) {
        private val ds = studyPreferences.ds

        private object K {
            val DAY = intPreferencesKey("day")
            val NEW_SHOWN = intPreferencesKey("new_shown")
            val REVIEW_SHOWN = intPreferencesKey("review_shown")
            val REVIEWS_SINCE_NEW = intPreferencesKey("reviews_since_new")
            val EXTRA_NEW_TODAY = intPreferencesKey("extra_new_today")

            val MIX_MODE = stringPreferencesKey("mix_mode")
            val NEW_PER_DAY_LIMIT = intPreferencesKey("new_per_day_limit")
            val REVIEW_PER_DAY_LIMIT = intPreferencesKey("review_per_day_limit")
            val OVERLAY_INTERVAL_TIME = intPreferencesKey("overlay_interval_time")
        }

        private companion object {
            const val DEFAULT_NEW_PER_DAY = 15
            const val DEFAULT_REVIEW_PER_DAY = 99
            const val DEFAULT_OVERLAY_INTERVAL_TIME = 0
            const val MIN_LIMIT = 0
            const val MAX_NEW_PER_DAY = 200
            const val MAX_REVIEW_PER_DAY = 2000
            const val MAX_OVERLAY_INTERVAL_MINUTES = 2000
        }

        fun read(): Flow<DayCounters> =
            ds.data.map { p ->
                DayCounters(
                    yyyymmdd = p[K.DAY] ?: 0,
                    newShown = p[K.NEW_SHOWN] ?: 0,
                    reviewShown = p[K.REVIEW_SHOWN] ?: 0,
                    reviewsSinceLastNew = p[K.REVIEWS_SINCE_NEW] ?: 0,
                    extraNewToday = p[K.EXTRA_NEW_TODAY] ?: 0,
                )
            }

        suspend fun resetFor(day: Int) {
            ds.edit { p ->
                p[K.DAY] = day
                p[K.NEW_SHOWN] = 0
                p[K.REVIEW_SHOWN] = 0
                p[K.REVIEWS_SINCE_NEW] = 0
                p[K.EXTRA_NEW_TODAY] = 0
            }
        }

        suspend fun markNewShown() {
            ds.edit { p ->
                p[K.NEW_SHOWN] = (p[K.NEW_SHOWN] ?: 0) + 1
                p[K.REVIEWS_SINCE_NEW] = 0
            }
        }

        // Restore the three rating-derived counters to an absolute prior value (undo).
        // Deliberately leaves EXTRA_NEW_TODAY and DAY untouched: a rating never changes them.
        suspend fun restoreCounters(
            newShown: Int,
            reviewShown: Int,
            reviewsSinceLastNew: Int,
        ) {
            ds.edit { p ->
                p[K.NEW_SHOWN] = newShown
                p[K.REVIEW_SHOWN] = reviewShown
                p[K.REVIEWS_SINCE_NEW] = reviewsSinceLastNew
            }
        }

        suspend fun addExtraNewToday(
            amount: Int,
            availableNew: Int,
        ) {
            if (amount <= 0) return
            ds.edit { p ->
                val newPerDay = p[K.NEW_PER_DAY_LIMIT] ?: DEFAULT_NEW_PER_DAY
                val newShown = p[K.NEW_SHOWN] ?: 0
                val extra = p[K.EXTRA_NEW_TODAY] ?: 0
                val remaining = (newPerDay + extra - newShown).coerceAtLeast(0)
                val capacity = (availableNew - remaining).coerceAtLeast(0)
                p[K.EXTRA_NEW_TODAY] = extra + amount.coerceAtMost(capacity)
            }
        }

        suspend fun markReviewShown() {
            ds.edit { p ->
                p[K.REVIEW_SHOWN] = (p[K.REVIEW_SHOWN] ?: 0) + 1
                p[K.REVIEWS_SINCE_NEW] = (p[K.REVIEWS_SINCE_NEW] ?: 0) + 1
            }
        }

        fun readPolicy(): Flow<LearningPreferencesConfig> =
            ds.data.map { p ->
                val mixName = p[K.MIX_MODE] ?: MixMode.MIX.name
                LearningPreferencesConfig(
                    newPerDay = p[K.NEW_PER_DAY_LIMIT] ?: DEFAULT_NEW_PER_DAY,
                    reviewPerDay = p[K.REVIEW_PER_DAY_LIMIT] ?: DEFAULT_REVIEW_PER_DAY,
                    overlayInterval = p[K.OVERLAY_INTERVAL_TIME] ?: DEFAULT_OVERLAY_INTERVAL_TIME,
                    mixMode = runCatching { MixMode.valueOf(mixName) }.getOrDefault(MixMode.MIX),
                )
            }

        suspend fun setMixMode(mode: MixMode) {
            ds.edit { it[K.MIX_MODE] = mode.name }
        }

        suspend fun setNewPerDay(value: Int) {
            ds.edit { it[K.NEW_PER_DAY_LIMIT] = value.coerceIn(MIN_LIMIT, MAX_NEW_PER_DAY) }
        }

        suspend fun setReviewPerDay(value: Int) {
            ds.edit { it[K.REVIEW_PER_DAY_LIMIT] = value.coerceIn(MIN_LIMIT, MAX_REVIEW_PER_DAY) }
        }

        suspend fun setOverlayInterval(value: Int) {
            ds.edit { it[K.OVERLAY_INTERVAL_TIME] = value.coerceIn(MIN_LIMIT, MAX_OVERLAY_INTERVAL_MINUTES) }
        }
    }
