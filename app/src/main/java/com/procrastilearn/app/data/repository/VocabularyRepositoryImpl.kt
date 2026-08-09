package com.procrastilearn.app.data.repository

import androidx.room.withTransaction
import com.procrastilearn.app.data.local.dao.VocabularyFsrsStateRestore
import com.procrastilearn.app.data.local.database.AppDatabase
import com.procrastilearn.app.data.local.entity.UndoSnapshotEntity
import com.procrastilearn.app.data.local.entity.VocabularyEntity
import com.procrastilearn.app.data.local.mapper.toDomain
import com.procrastilearn.app.data.local.mapper.toEntity
import com.procrastilearn.app.data.local.mapper.toFsrsState
import com.procrastilearn.app.data.local.prefs.DayCountersStore
import com.procrastilearn.app.domain.model.MixMode
import com.procrastilearn.app.domain.model.StudyDirection
import com.procrastilearn.app.domain.model.UndoResult
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.model.includesBackward
import com.procrastilearn.app.domain.model.includesForward
import com.procrastilearn.app.domain.model.isBackwardOnly
import com.procrastilearn.app.domain.repository.VocabularyCatalogRepository
import com.procrastilearn.app.domain.repository.VocabularyStudyRepository
import io.github.openspacedrepetition.Card
import io.github.openspacedrepetition.Rating
import io.github.openspacedrepetition.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

private const val UNDO_STACK_CAP = 3

// How far past the first-ever review of a bidirectional card its other direction's due
// date is seeded, so the just-answered card's opposite direction doesn't immediately
// re-surface in the very next pick.
private const val BACKWARD_SEED_BUFFER_MILLIS = 10 * 60 * 1000L

internal fun todayStamp(): Int =
    LocalDate
        .now()
        .format(DateTimeFormatter.BASIC_ISO_DATE)
        .toInt()

class NoAvailableItemsException : Exception("Daily limits reached and no reviews due")

@Singleton
class VocabularyRepositoryImpl
    @Inject
    constructor(
        private val appDatabase: AppDatabase,
        private val scheduler: Scheduler,
        private val prefs: DayCountersStore,
    ) : VocabularyCatalogRepository,
        VocabularyStudyRepository {
        private val vocabularyDao = appDatabase.vocabularyDao()
        private val vocabularyReviewDao = appDatabase.vocabularyReviewDao()
        private val vocabularyStatsDao = appDatabase.vocabularyStatsDao()
        private val undoSnapshotDao = appDatabase.undoSnapshotDao()
        private val currentItem = MutableStateFlow<VocabularyItem?>(null)
        private val io = Dispatchers.IO
        private val reviewMutex = Mutex()

        override suspend fun getVocabularyItemByWord(word: String): VocabularyItem? =
            withContext(io) {
                vocabularyDao.getVocabularyByWord(word.trim())?.toDomain()
            }

        override suspend fun addVocabularyItem(item: VocabularyItem): Unit =
            withContext(io) {
                // New items should not be counted as reviews due.
                // Keep FSRS card JSON, but leave dueAt as 0 until first rating.
                val cardJson = Card.builder().build().toJson()
                val dueAt = 0L
                vocabularyDao.insertVocabulary(item.toEntity(fsrsCardJson = cardJson, fsrsDueAt = dueAt))
            }

        override suspend fun updateVocabularyItem(item: VocabularyItem): Unit =
            withContext(io) {
                val existingEntity = vocabularyDao.getVocabularyById(item.id)
                if (existingEntity != null) {
                    // Enabling bidirectional on a row that's already past its first-ever review
                    // won't trigger the natural new-row seed in reviewVocabularyItem (wasRowNew
                    // requires both directions still unreviewed) - seed the backward due date here
                    // instead so the newly-enabled reverse card actually becomes reviewable.
                    val needsBackwardSeed =
                        item.bidirectional &&
                            existingEntity.fsrsDueAt != 0L &&
                            existingEntity.backwardFsrsDueAt == 0L
                    val updatedEntity =
                        existingEntity.copy(
                            word = item.word,
                            translation = item.translation,
                            bidirectional = item.bidirectional,
                            backwardPromptOverride = item.backwardPromptOverride,
                            backwardAnswerOverride = item.backwardAnswerOverride,
                            backwardFsrsDueAt =
                                if (needsBackwardSeed) System.currentTimeMillis() else existingEntity.backwardFsrsDueAt,
                        )
                    vocabularyDao.updateVocabulary(updatedEntity)
                    undoSnapshotDao.deleteForVocab(item.id)
                }
            }

        override suspend fun deleteVocabularyItem(item: VocabularyItem) = deleteVocabularyItems(listOf(item))

        override suspend fun deleteVocabularyItems(items: List<VocabularyItem>): Unit =
            withContext(io) {
                if (items.isEmpty()) return@withContext
                appDatabase.withTransaction {
                    vocabularyDao.deleteVocabulary(items.map { it.toEntity() })
                    undoSnapshotDao.deleteForVocabIds(items.map { it.id })
                }
            }

        override suspend fun resetVocabularyProgress(item: VocabularyItem): Unit =
            withContext(io) {
                val existingEntity = vocabularyDao.getVocabularyById(item.id) ?: return@withContext
                val resetCardJson = Card.builder().build().toJson()
                val resetEntity =
                    existingEntity.copy(
                        lastShownAt = null,
                        correctCount = 0,
                        incorrectCount = 0,
                        fsrsCardJson = resetCardJson,
                        fsrsDueAt = 0L,
                        backwardCorrectCount = 0,
                        backwardIncorrectCount = 0,
                        backwardFsrsCardJson = resetCardJson,
                        backwardFsrsDueAt = 0L,
                    )
                vocabularyDao.updateVocabulary(resetEntity)
                undoSnapshotDao.deleteForVocab(item.id)
                if (currentItem.value?.id == item.id) {
                    currentItem.value = resetEntity.toDomain(currentItem.value?.direction ?: StudyDirection.FORWARD)
                }
            }

        override fun getAllVocabulary(): Flow<List<VocabularyItem>> =
            vocabularyDao.getAllVocabulary().map { list -> list.map { it.toDomain() } }

        override fun observeBackwardOnlySkippedCount(): Flow<Int> =
            vocabularyStatsDao.observeBackwardOnlySkippedCount(System.currentTimeMillis())

        @Suppress("LongMethod")
        override suspend fun reviewVocabularyItem(
            id: Long,
            rating: Rating,
            direction: StudyDirection,
        ): Unit =
            withContext(io) {
                reviewMutex.withLock {
                    val entity =
                        vocabularyDao.getVocabularyById(id)
                            ?: throw IllegalArgumentException("Vocabulary $id not found")

                    val wasRowNew = entity.fsrsDueAt == 0L && entity.backwardFsrsDueAt == 0L

                    val cardJsonBefore =
                        if (direction == StudyDirection.FORWARD) entity.fsrsCardJson else entity.backwardFsrsCardJson
                    val card =
                        if (cardJsonBefore.isBlank()) {
                            Card.builder().build()
                        } else {
                            Card.fromJson(cardJsonBefore)
                        }

                    val result = scheduler.reviewCard(card, rating)
                    val updatedCard = result.card()
                    val log = result.reviewLog()

                    val reviewedAt = log.reviewDatetime().toEpochMilli()
                    val nextDue = updatedCard.getDue().toEpochMilli()

                    val wasCorrect = rating != Rating.AGAIN

                    // Only ever seed the other direction on a row's first-ever exposure, and
                    // only when the card is flagged bidirectional.
                    val shouldSeedOther = wasRowNew && entity.bidirectional
                    val seedDueAt = reviewedAt + BACKWARD_SEED_BUFFER_MILLIS

                    val counters = prefs.read().first()
                    val snapshot =
                        UndoSnapshotEntity(
                            vocabId = id,
                            createdAt = System.currentTimeMillis(),
                            snapshotDay = todayStamp(),
                            ratingName = rating.name,
                            direction = direction.name,
                            fsrsCardJson = entity.fsrsCardJson,
                            fsrsDueAt = entity.fsrsDueAt,
                            lastShownAt = entity.lastShownAt,
                            correctCount = entity.correctCount,
                            incorrectCount = entity.incorrectCount,
                            backwardFsrsCardJson = entity.backwardFsrsCardJson,
                            backwardFsrsDueAt = entity.backwardFsrsDueAt,
                            backwardCorrectCount = entity.backwardCorrectCount,
                            backwardIncorrectCount = entity.backwardIncorrectCount,
                            newShown = counters.newShown,
                            reviewShown = counters.reviewShown,
                            reviewsSinceLastNew = counters.reviewsSinceLastNew,
                        )

                    appDatabase.withTransaction {
                        when (direction) {
                            StudyDirection.FORWARD ->
                                vocabularyReviewDao.applyFsrsReview(
                                    id = id,
                                    cardJson = updatedCard.toJson(),
                                    dueAt = nextDue,
                                    reviewedAt = reviewedAt,
                                    wasCorrect = wasCorrect,
                                    seedOtherDirection = shouldSeedOther,
                                    seedDueAt = seedDueAt,
                                )
                            StudyDirection.BACKWARD ->
                                vocabularyReviewDao.applyBackwardFsrsReview(
                                    id = id,
                                    cardJson = updatedCard.toJson(),
                                    dueAt = nextDue,
                                    reviewedAt = reviewedAt,
                                    wasCorrect = wasCorrect,
                                    seedOtherDirection = shouldSeedOther,
                                    seedDueAt = seedDueAt,
                                )
                        }
                        undoSnapshotDao.insert(snapshot)
                        undoSnapshotDao.trimToLast(UNDO_STACK_CAP)
                    }

                    // IMPORTANT: Clear current item after review to force new item on next call
                    currentItem.value = null
                    // Update day counters based on whether the *row* was new at display time,
                    // regardless of which direction was rated - a row only ever consumes a
                    // new-slot once, on its first-ever exposure in either direction.
                    if (wasRowNew) prefs.markNewShown() else prefs.markReviewShown()
                }
            }

        override suspend fun undoLastRating(): UndoResult? =
            withContext(io) {
                reviewMutex.withLock {
                    val snapshot = undoSnapshotDao.peekLatest() ?: return@withLock null

                    appDatabase.withTransaction {
                        vocabularyReviewDao.restoreFsrsState(
                            VocabularyFsrsStateRestore(id = snapshot.vocabId, fsrsState = snapshot.toFsrsState()),
                        )
                        undoSnapshotDao.deleteById(snapshot.id)
                    }

                    if (snapshot.snapshotDay == todayStamp()) {
                        prefs.restoreCounters(
                            newShown = snapshot.newShown,
                            reviewShown = snapshot.reviewShown,
                            reviewsSinceLastNew = snapshot.reviewsSinceLastNew,
                        )
                    }

                    val restoredDirection =
                        runCatching { StudyDirection.valueOf(snapshot.direction) }.getOrDefault(StudyDirection.FORWARD)
                    val restoredEntity =
                        vocabularyDao.getVocabularyById(snapshot.vocabId)
                            ?: return@withLock null
                    val restoredItem = restoredEntity.toDomain(restoredDirection)
                    currentItem.value = restoredItem
                    UndoResult(
                        item = restoredItem,
                        revertedRating = Rating.valueOf(snapshot.ratingName),
                    )
                }
            }

        override fun observeUndoCount(): Flow<Int> = undoSnapshotDao.observeCount()

        @Suppress("CyclomaticComplexMethod")
        override suspend fun getNextVocabularyItem(): VocabularyItem =
            withContext(io) {
                val now = System.currentTimeMillis()
                ensureDay()
                // Read day counters once
                val counters = prefs.read().first()
                val policy = prefs.readPolicy().first()

                val includeForward = policy.studyDirectionMode.includesForward
                val includeBackward = policy.studyDirectionMode.includesBackward
                val backwardOnlyMode = policy.studyDirectionMode.isBackwardOnly

                val totalNew = vocabularyStatsDao.countNewTotal(requireBidirectional = backwardOnlyMode)

                val newRemaining =
                    (policy.newPerDay + counters.extraNewToday - counters.newShown).coerceAtLeast(0)
                val reviewRemaining = (policy.reviewPerDay - counters.reviewShown).coerceAtLeast(0)

                // 1) Check due reviews (incl. learning due now via FSRS dueAt)
                val dueCount =
                    if (reviewRemaining > 0) {
                        vocabularyStatsDao.countReviewsDue(now, includeForward, includeBackward)
                    } else {
                        0
                    }
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

                val picked: PickedCandidate? =
                    when {
                        // Prefer due reviews unless we explicitly want new right now
                        dueCount > 0 && !wantNew -> pickEarliestDue(now, includeForward, includeBackward)

                        // If we want a new now (ratio hit) or no reviews due, try new (within daily cap)
                        newRemaining > 0 && (wantNew || dueCount == 0) ->
                            pickNewCandidate(totalNew = totalNew, backwardOnlyMode = backwardOnlyMode)

                        // Don't fall back to random/upcoming if limits are reached
                        else -> null
                    }

                val chosen = picked ?: throw NoAvailableItemsException()

                return@withContext finalizePick(chosen.id, chosen.direction)
            }

        override suspend fun hasAvailableItems(): Boolean =
            withContext(io) {
                val now = System.currentTimeMillis()
                ensureDay()

                val counters = prefs.read().first()
                val policy = prefs.readPolicy().first()

                val includeForward = policy.studyDirectionMode.includesForward
                val includeBackward = policy.studyDirectionMode.includesBackward
                val backwardOnlyMode = policy.studyDirectionMode.isBackwardOnly

                val totalNew = vocabularyStatsDao.countNewTotal(requireBidirectional = backwardOnlyMode)
                val newRemaining =
                    (policy.newPerDay + counters.extraNewToday - counters.newShown).coerceAtLeast(0)
                val reviewRemaining = (policy.reviewPerDay - counters.reviewShown).coerceAtLeast(0)

                // Check if there are due reviews
                val dueCount =
                    if (reviewRemaining > 0) {
                        vocabularyStatsDao.countReviewsDue(now, includeForward, includeBackward)
                    } else {
                        0
                    }

                // We have items available if:
                // 1. There are reviews due, OR
                // 2. We haven't hit the new card limit AND there are new cards
                return@withContext when {
                    dueCount > 0 -> true
                    newRemaining > 0 && totalNew > 0 -> true
                    else -> false
                }
            }

        private data class PickedCandidate(
            val id: Long,
            val direction: StudyDirection,
        )

        private suspend fun pickEarliestDue(
            now: Long,
            includeForward: Boolean,
            includeBackward: Boolean,
        ): PickedCandidate? {
            val forward = if (includeForward) vocabularyReviewDao.pickNextForwardReviewCandidate(now) else null
            val backward = if (includeBackward) vocabularyReviewDao.pickNextBackwardReviewCandidate(now) else null
            return when {
                forward == null && backward == null -> null
                backward == null -> PickedCandidate(forward!!.id, StudyDirection.FORWARD)
                forward == null -> PickedCandidate(backward.id, StudyDirection.BACKWARD)
                // Ties resolve to forward.
                forward.dueAt <= backward.dueAt -> PickedCandidate(forward.id, StudyDirection.FORWARD)
                else -> PickedCandidate(backward.id, StudyDirection.BACKWARD)
            }
        }

        // A row is always introduced forward unless the global mode is purely Backward, in
        // which case it's introduced backward and only bidirectional-flagged rows are
        // eligible at all. Both restrictions are the same underlying condition, so they
        // share the one flag rather than being passed as two params that must be kept in sync.
        private suspend fun pickNewCandidate(
            totalNew: Int,
            backwardOnlyMode: Boolean,
        ): PickedCandidate? {
            if (totalNew <= 0) return null
            val offset = kotlin.random.Random.nextInt(totalNew)
            val id =
                vocabularyReviewDao.pickNewIdByOffset(offset, requireBidirectional = backwardOnlyMode)
                    ?: vocabularyReviewDao.pickNewIdByOffset(0, requireBidirectional = backwardOnlyMode)
                    ?: return null
            return PickedCandidate(id, if (backwardOnlyMode) StudyDirection.BACKWARD else StudyDirection.FORWARD)
        }

        private suspend fun finalizePick(
            id: Long,
            direction: StudyDirection,
        ): VocabularyItem {
            val entity = vocabularyDao.getVocabularyById(id)
            check(entity != null) { "Picked id $id not found" }

            val item = entity.ensureFsrs(direction).toDomain(direction)
            currentItem.value = item
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
        private fun VocabularyEntity.ensureFsrs(direction: StudyDirection): VocabularyEntity =
            when (direction) {
                StudyDirection.FORWARD ->
                    if (fsrsCardJson.isNotBlank()) {
                        this
                    } else {
                        copy(fsrsCardJson = Card.builder().build().toJson(), fsrsDueAt = 0L)
                    }
                StudyDirection.BACKWARD ->
                    if (backwardFsrsCardJson.isNotBlank()) {
                        this
                    } else {
                        copy(backwardFsrsCardJson = Card.builder().build().toJson(), backwardFsrsDueAt = 0L)
                    }
            }
    }
