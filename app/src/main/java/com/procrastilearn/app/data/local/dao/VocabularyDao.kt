package com.procrastilearn.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.procrastilearn.app.data.local.entity.VocabularyEntity
import kotlinx.coroutines.flow.Flow

data class DueCandidate(
    val id: Long,
    val dueAt: Long,
)

@Suppress("TooManyFunctions")
@Dao
interface VocabularyDao {
    // Existing
    @Query("SELECT * FROM vocabulary")
    fun getAllVocabulary(): Flow<List<VocabularyEntity>>

    @Query("SELECT * FROM vocabulary ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomAny(): VocabularyEntity?

    @Query("SELECT * FROM vocabulary WHERE id = :id")
    suspend fun getVocabularyById(id: Long): VocabularyEntity?

    @Query(
        """
        SELECT * FROM vocabulary
        WHERE word = :word COLLATE NOCASE
        LIMIT 1
        """,
    )
    suspend fun getVocabularyByWord(word: String): VocabularyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVocabulary(item: VocabularyEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllVocabulary(items: List<VocabularyEntity>)

    @Update
    suspend fun updateVocabulary(item: VocabularyEntity)

    @Delete
    suspend fun deleteVocabulary(item: VocabularyEntity)

    @Query("DELETE FROM vocabulary")
    suspend fun deleteAllVocabulary()

    @Query("SELECT COUNT(*) FROM vocabulary")
    suspend fun getVocabularyCount(): Int

    // --- FSRS-oriented helpers ---

    // Any due now or overdue
    @Query(
        """
        SELECT * FROM vocabulary
        WHERE fsrsDueAt > 0 AND fsrsDueAt <= :now
        ORDER BY fsrsDueAt ASC
        LIMIT 1
    """,
    )
    suspend fun getEarliestDue(now: Long): VocabularyEntity?

    // Nearest upcoming due (when nothing is due)
    @Query(
        """
        SELECT * FROM vocabulary
        WHERE fsrsDueAt > 0
        ORDER BY fsrsDueAt ASC
        LIMIT 1
    """,
    )
    suspend fun getNearestDue(): VocabularyEntity?

    @Query(
        """
        SELECT * FROM vocabulary
        WHERE correctCount = 0 AND incorrectCount = 0
        ORDER BY RANDOM()
        LIMIT 1
    """,
    )
    suspend fun getRandomNew(): VocabularyEntity?

    // Apply a forward-direction review atomically. When [seedOtherDirection] is true and
    // the backward direction has never been introduced (backwardFsrsDueAt = 0), seeds it
    // to [seedDueAt] so it later surfaces as an ordinary due review rather than staying
    // dormant forever or requiring separate "new" bookkeeping.
    @Suppress("LongParameterList")
    @Query(
        """
        UPDATE vocabulary
        SET
            fsrsCardJson = :cardJson,
            fsrsDueAt = :dueAt,
            lastShownAt = :reviewedAt,
            correctCount = correctCount + :incCorrect,
            incorrectCount = incorrectCount + :incIncorrect,
            backwardFsrsDueAt = CASE
                WHEN :seedOtherDirection = 1 AND backwardFsrsDueAt = 0 THEN :seedDueAt
                ELSE backwardFsrsDueAt
            END
        WHERE id = :id
    """,
    )
    suspend fun applyFsrsReview(
        id: Long,
        cardJson: String,
        dueAt: Long,
        reviewedAt: Long,
        incCorrect: Int,
        incIncorrect: Int,
        seedOtherDirection: Boolean = false,
        seedDueAt: Long = 0L,
    )

    // Symmetric twin of [applyFsrsReview] for the backward direction: on first-ever
    // introduction it can seed the forward direction's due date instead.
    @Suppress("LongParameterList")
    @Query(
        """
        UPDATE vocabulary
        SET
            backwardFsrsCardJson = :cardJson,
            backwardFsrsDueAt = :dueAt,
            lastShownAt = :reviewedAt,
            backwardCorrectCount = backwardCorrectCount + :incCorrect,
            backwardIncorrectCount = backwardIncorrectCount + :incIncorrect,
            fsrsDueAt = CASE
                WHEN :seedOtherDirection = 1 AND fsrsDueAt = 0 THEN :seedDueAt
                ELSE fsrsDueAt
            END
        WHERE id = :id
    """,
    )
    suspend fun applyBackwardFsrsReview(
        id: Long,
        cardJson: String,
        dueAt: Long,
        reviewedAt: Long,
        incCorrect: Int,
        incIncorrect: Int,
        seedOtherDirection: Boolean = false,
        seedDueAt: Long = 0L,
    )

    // Restore both directions' full state to an absolute prior value (undo). A single
    // review can have seeded the other direction, so undo must revert both atomically.
    @Suppress("LongParameterList")
    @Query(
        """
        UPDATE vocabulary
        SET
            fsrsCardJson = :cardJson,
            fsrsDueAt = :dueAt,
            lastShownAt = :lastShownAt,
            correctCount = :correctCount,
            incorrectCount = :incorrectCount,
            backwardFsrsCardJson = :backwardCardJson,
            backwardFsrsDueAt = :backwardDueAt,
            backwardCorrectCount = :backwardCorrectCount,
            backwardIncorrectCount = :backwardIncorrectCount
        WHERE id = :id
    """,
    )
    suspend fun restoreFsrsState(
        id: Long,
        cardJson: String,
        dueAt: Long,
        lastShownAt: Long?,
        correctCount: Int,
        incorrectCount: Int,
        backwardCardJson: String,
        backwardDueAt: Long,
        backwardCorrectCount: Int,
        backwardIncorrectCount: Int,
    )

    @Query(
        """
        SELECT * FROM vocabulary
        WHERE fsrsDueAt > 0 AND fsrsDueAt <= :now
        ORDER BY fsrsDueAt ASC
    """,
    )
    suspend fun getDueCards(now: Long): List<VocabularyEntity>

    // Get a single card by ID
    @Query("SELECT * FROM vocabulary WHERE id = :id LIMIT 1")
    suspend fun getCard(id: Long): VocabularyEntity?

    // Counts due reviews per direction and sums them, so a row due in both directions at
    // once correctly contributes 2 (each direction is an independently reviewable item),
    // not 1 as a single OR-based row match would.
    @Query(
        """
        SELECT
            SUM(CASE WHEN :includeForward = 1 AND fsrsDueAt > 0 AND fsrsDueAt <= :now THEN 1 ELSE 0 END) +
            SUM(CASE WHEN :includeBackward = 1 AND bidirectional = 1
                      AND backwardFsrsDueAt > 0 AND backwardFsrsDueAt <= :now THEN 1 ELSE 0 END)
        FROM vocabulary
        """,
    )
    suspend fun countReviewsDue(
        now: Long,
        includeForward: Boolean = true,
        includeBackward: Boolean = false,
    ): Int

    // Reactive twin of [countReviewsDue]: re-emits whenever the vocabulary table
    // changes (e.g. a review recorded from the blocking overlay), so screens showing
    // due-count-derived state can stay in sync without polling or manual refresh.
    @Query(
        """
        SELECT
            SUM(CASE WHEN :includeForward = 1 AND fsrsDueAt > 0 AND fsrsDueAt <= :now THEN 1 ELSE 0 END) +
            SUM(CASE WHEN :includeBackward = 1 AND bidirectional = 1
                      AND backwardFsrsDueAt > 0 AND backwardFsrsDueAt <= :now THEN 1 ELSE 0 END)
        FROM vocabulary
        """,
    )
    fun observeReviewsDueCount(
        now: Long,
        includeForward: Boolean = true,
        includeBackward: Boolean = false,
    ): Flow<Int>

    // A row counts as "new" (never introduced in either direction) only once both
    // fsrsDueAt and backwardFsrsDueAt are still 0. These two columns are always written
    // together atomically, so this stays reliable even after a direction is seeded but
    // not yet actually reviewed (see applyFsrsReview/applyBackwardFsrsReview).
    @Query(
        """
        SELECT COUNT(*) FROM vocabulary
        WHERE fsrsDueAt = 0 AND backwardFsrsDueAt = 0
          AND (:requireBidirectional = 0 OR bidirectional = 1)
        """,
    )
    suspend fun countNewTotal(requireBidirectional: Boolean = false): Int

    // Reactive twin of [countNewTotal]: re-emits whenever the vocabulary table changes,
    // so screens showing new-count-derived state can stay in sync without polling.
    @Query(
        """
        SELECT COUNT(*) FROM vocabulary
        WHERE fsrsDueAt = 0 AND backwardFsrsDueAt = 0
          AND (:requireBidirectional = 0 OR bidirectional = 1)
        """,
    )
    fun observeNewTotalCount(requireBidirectional: Boolean = false): Flow<Int>

    // Pick the earliest forward-due review, if any.
    @Query(
        """
        SELECT id, fsrsDueAt AS dueAt FROM vocabulary
        WHERE fsrsDueAt > 0 AND fsrsDueAt <= :now
        ORDER BY fsrsDueAt ASC
        LIMIT 1
    """,
    )
    suspend fun pickNextForwardReviewCandidate(now: Long): DueCandidate?

    // Pick the earliest backward-due review among bidirectional cards, if any.
    @Query(
        """
        SELECT id, backwardFsrsDueAt AS dueAt FROM vocabulary
        WHERE bidirectional = 1 AND backwardFsrsDueAt > 0 AND backwardFsrsDueAt <= :now
        ORDER BY backwardFsrsDueAt ASC
        LIMIT 1
    """,
    )
    suspend fun pickNextBackwardReviewCandidate(now: Long): DueCandidate?

    // Pick next upcoming (when nothing is due)
    @Query(
        """
        SELECT id FROM vocabulary
        WHERE fsrsDueAt > :now
        ORDER BY fsrsDueAt ASC
        LIMIT 1
    """,
    )
    suspend fun pickNextUpcomingId(now: Long): Long?

    // Pick a "new" by offset (efficient alternative to ORDER BY RANDOM())
    @Query(
        """
        SELECT id FROM vocabulary
        WHERE fsrsDueAt = 0 AND backwardFsrsDueAt = 0
          AND (:requireBidirectional = 0 OR bidirectional = 1)
        LIMIT 1 OFFSET :offset
    """,
    )
    suspend fun pickNewIdByOffset(
        offset: Int,
        requireBidirectional: Boolean = false,
    ): Long?

    // Fallback: random any, excluding the last shown (still small; OK)
    @Query(
        """
        SELECT id FROM vocabulary
        WHERE (:excludeId IS NULL OR id != :excludeId)
        ORDER BY RANDOM()
        LIMIT 1
    """,
    )
    suspend fun pickRandomAnyId(excludeId: Long?): Long?

    // Counts cards that would be due-or-new right now but are hidden because the global
    // study mode is Backward-only and the card is not flagged bidirectional. Only
    // meaningful when the caller is in that mode.
    @Query(
        """
        SELECT COUNT(*) FROM vocabulary
        WHERE bidirectional = 0
          AND (
                (fsrsDueAt = 0 AND backwardFsrsDueAt = 0)
             OR (fsrsDueAt > 0 AND fsrsDueAt <= :now)
          )
        """,
    )
    fun observeBackwardOnlySkippedCount(now: Long): Flow<Int>
}
