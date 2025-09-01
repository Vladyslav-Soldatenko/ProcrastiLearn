package com.procrastilearn.app.data.repository

import android.util.Log
import com.procrastilearn.app.data.local.dao.VocabularyDao
import com.procrastilearn.app.data.local.entity.VocabularyEntity
import com.procrastilearn.app.data.local.mapper.toDomain
import com.procrastilearn.app.data.local.mapper.toEntity
import com.procrastilearn.app.data.local.prefs.DayCountersStore
import com.procrastilearn.app.domain.model.MixMode
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.repository.VocabularyRepository
import io.github.openspacedrepetition.Card
import io.github.openspacedrepetition.Rating
import io.github.openspacedrepetition.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

private fun todayStamp(): Int =
    LocalDate
        .now()
        .format(DateTimeFormatter.BASIC_ISO_DATE)
        .toInt()

class NoAvailableItemsException : Exception("Daily limits reached and no reviews due")

@Singleton
class VocabularyRepositoryImpl
    @Inject
    constructor(
        private val vocabularyDao: VocabularyDao,
        private val scheduler: Scheduler,
        private val prefs: DayCountersStore,
    ) : VocabularyRepository {
        private val currentItem = MutableStateFlow<VocabularyItem?>(null)
        private val io = Dispatchers.IO
        private var lastShownId: Long? = null

        override suspend fun addVocabularyItem(item: VocabularyItem): Unit =
            withContext(io) {
                val cardJson = Card.builder().build().toJson()
                val dueAt = Instant.now().toEpochMilli()
                vocabularyDao.insertVocabulary(item.toEntity(fsrsCardJson = cardJson, fsrsDueAt = dueAt))
            }

        override suspend fun updateVocabularyItem(item: VocabularyItem): Unit =
            withContext(io) {
                val existingEntity = vocabularyDao.getVocabularyById(item.id)
                if (existingEntity != null) {
                    val updatedEntity =
                        existingEntity.copy(
                            word = item.word,
                            translation = item.translation,
                        )
                    vocabularyDao.updateVocabulary(updatedEntity)
                }
            }

        override suspend fun deleteVocabularyItem(item: VocabularyItem) =
            withContext(io) {
                vocabularyDao.deleteVocabulary(item.toEntity())
            }

        override fun observeCurrentItem(): Flow<VocabularyItem> = currentItem.asStateFlow().filterNotNull()

        override fun getAllVocabulary(): Flow<List<VocabularyItem>> =
            vocabularyDao.getAllVocabulary().map { list -> list.map { it.toDomain() } }

        override suspend fun reviewVocabularyItem(
            id: Long,
            rating: Rating,
        ): Unit =
            withContext(io) {
                Log.i("fsrs", "reviewng $id")
                val entity =
                    vocabularyDao.getVocabularyById(id)
                        ?: throw IllegalArgumentException("Vocabulary $id not found")

                val card =
                    if (entity.fsrsCardJson.isBlank()) {
                        Card.builder().build()
                    } else {
                        Card.fromJson(entity.fsrsCardJson)
                    }

                val result = scheduler.reviewCard(card, rating)
                val updatedCard = result.card()
                val log = result.reviewLog()

                val reviewedAt = log.reviewDatetime().toEpochMilli()
                val nextDue = updatedCard.getDue().toEpochMilli()

                val incCorrect = if (rating == Rating.AGAIN) 0 else 1
                val incIncorrect = if (rating == Rating.AGAIN) 1 else 0

                vocabularyDao.applyFsrsReview(
                    id = id,
                    cardJson = updatedCard.toJson(),
                    dueAt = nextDue,
                    reviewedAt = reviewedAt,
                    incCorrect = incCorrect,
                    incIncorrect = incIncorrect,
                )

                // IMPORTANT: Clear current item after review to force new item on next call
                currentItem.value = null
                // Update day counters based on card type at *display* time
                val isNew = (entity.correctCount == 0 && entity.incorrectCount == 0)
                if (isNew) prefs.markNewShown() else prefs.markReviewShown()
                Log.i("FSRS", "Reviewed $id as $rating; next due at $nextDue")
            }

        @Suppress("CyclomaticComplexMethod")
        override suspend fun getNextVocabularyItem(): VocabularyItem =
            withContext(io) {
                val now = System.currentTimeMillis()
                ensureDay()
                Log.i("fsrs", "before counters")
                // Read day counters once
                val counters = prefs.read().first()
                val policy = prefs.readPolicy().first()

                Log.i("fsrs", counters.toString())
                val newRemaining = (policy.newPerDay - counters.newShown).coerceAtLeast(0)
                val reviewRemaining = (policy.reviewPerDay - counters.reviewShown).coerceAtLeast(0)

                // 1) Check due reviews (incl. learning due now via FSRS dueAt)
                val dueCount = if (reviewRemaining > 0) vocabularyDao.countReviewsDue(now) else 0
                Log.i("fsrs", "newRemaining=$newRemaining, dueCount=$dueCount, prefs=$prefs.")
                // Check if we've hit limits and have nothing to show
                if (newRemaining == 0 && dueCount == 0) {
                    throw NoAvailableItemsException()
                }

                // Decide which queue we *intend* to draw from
                val wantNew =
                    when (policy.mixMode) {
                        MixMode.NEW_FIRST -> newRemaining > 0
                        MixMode.REVIEWS_FIRST -> false
                        MixMode.MIX ->
                            shouldServeNewMixed(
                                newRemaining,
                                reviewRemaining,
                                dueCount,
                                counters.reviewsSinceLastNew,
                            )
                    }

                val pickId: Long? =
                    when {
                        // Prefer due reviews unless we explicitly want new right now
                        dueCount > 0 && !wantNew -> vocabularyDao.pickNextReviewId(now)

                        // If we want a new now (ratio hit) or no reviews due, try new (within daily cap)
                        newRemaining > 0 && (wantNew || dueCount == 0) -> {
                            val totalNew = vocabularyDao.countNewTotal()
                            if (totalNew > 0) {
                                val offset = kotlin.random.Random.nextInt(totalNew) // O(1) sampler
                                vocabularyDao.pickNewIdByOffset(offset)
                                    ?: vocabularyDao.pickNewIdByOffset(0)
                            } else {
                                null
                            }
                        }

                        // Don't fall back to random/upcoming if limits are reached
                        else -> null
                    }

                val chosenId = pickId ?: throw NoAvailableItemsException()

                // Avoid immediate repeat if policy says so
                if (policy.buryImmediateRepeat && lastShownId != null && chosenId == lastShownId) {
                    vocabularyDao.pickRandomAnyId(lastShownId)?.let { alternate ->
                        return@withContext finalizePick(alternate)
                    }
                }

                return@withContext finalizePick(chosenId)
            }

        override suspend fun hasAvailableItems(): Boolean =
            withContext(io) {
                val now = System.currentTimeMillis()
                ensureDay()

                val counters = prefs.read().first()
                val policy = prefs.readPolicy().first()
                val newRemaining = (policy.newPerDay - counters.newShown).coerceAtLeast(0)
                val reviewRemaining = (policy.reviewPerDay - counters.reviewShown).coerceAtLeast(0)

                // Check if there are due reviews
                val dueCount = if (reviewRemaining > 0) vocabularyDao.countReviewsDue(now) else 0

                // We have items available if:
                // 1. There are reviews due, OR
                // 2. We haven't hit the new card limit AND there are new cards
                return@withContext when {
                    dueCount > 0 -> true
                    newRemaining > 0 && vocabularyDao.countNewTotal() > 0 -> true
                    else -> false
                }
            }

        private suspend fun finalizePick(id: Long): VocabularyItem {
            val entity = vocabularyDao.getVocabularyById(id)
            check(entity != null) { "Picked id $id not found" }

            val item = entity.ensureFsrs().toDomain()
            currentItem.value = item
            lastShownId = item.id
            return item
        }

        /**
         * MIX policy: show 1 new after N reviews, where N is derived from remaining quotas.
         * This mimics Anki’s dynamic interleaving rather than a fixed probability.
         */
        private fun shouldServeNewMixed(
            newRemaining: Int,
            reviewRemaining: Int,
            dueCount: Int,
            reviewsSinceLastNew: Int,
        ): Boolean {
            if (newRemaining <= 0) return false
            if (dueCount == 0 && reviewRemaining == 0) return true // no reviews left; show new
            // target: 1 new after R reviews
            val r = kotlin.math.max(1.0, kotlin.math.ceil(reviewRemaining.toDouble() / newRemaining)).toInt()
            return reviewsSinceLastNew >= r
        }

        private suspend fun ensureDay() {
            val today = todayStamp()
            val current = prefs.read().first()
            if (current.yyyymmdd != today) {
                prefs.resetFor(today)
            }
        }

        // --- existing helpers unchanged ---
        private fun VocabularyEntity.ensureFsrs(): VocabularyEntity {
            if (fsrsCardJson.isNotBlank()) return this
            val card = Card.builder().build()
            return copy(fsrsCardJson = card.toJson(), fsrsDueAt = 0L)
        }
    }
