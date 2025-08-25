package com.procrastilearn.app.data.local.prefs

import android.content.Context
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.procrastilearn.app.data.counter.DayCounters
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DayCountersStore
    @Inject
    constructor(
        @ApplicationContext private val ctx: Context,
    ) {
        private val ds =
            PreferenceDataStoreFactory.create(
                produceFile = { ctx.dataStoreFile("study_prefs.preferences_pb") },
            )

        private object K {
            val DAY = intPreferencesKey("day")
            val NEW_SHOWN = intPreferencesKey("new_shown")
            val REVIEW_SHOWN = intPreferencesKey("review_shown")
            val REVIEWS_SINCE_NEW = intPreferencesKey("reviews_since_new")
            // Optional: persist policy if you want it user-configurable
        }

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
    }
