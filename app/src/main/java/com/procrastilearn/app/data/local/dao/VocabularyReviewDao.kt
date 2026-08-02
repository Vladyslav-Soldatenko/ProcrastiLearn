package com.procrastilearn.app.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Update
import com.procrastilearn.app.data.local.entity.VocabularyEntity
import com.procrastilearn.app.data.local.entity.VocabularyFsrsState

data class DueCandidate(
    val id: Long,
    val dueAt: Long,
)

data class VocabularyFsrsStateRestore(
    val id: Long,
    @Embedded val fsrsState: VocabularyFsrsState,
)

@Dao
interface VocabularyReviewDao {
    // Apply a forward-direction review atomically. When [seedOtherDirection] is true and
    // the backward direction has never been introduced (backwardFsrsDueAt = 0), seeds it
    // to [seedDueAt] so it later surfaces as an ordinary due review rather than staying
    // dormant forever or requiring separate "new" bookkeeping.
    @Query(
        """
        UPDATE vocabulary
        SET
            fsrsCardJson = :cardJson,
            fsrsDueAt = :dueAt,
            lastShownAt = :reviewedAt,
            correctCount = correctCount + (CASE WHEN :wasCorrect = 1 THEN 1 ELSE 0 END),
            incorrectCount = incorrectCount + (CASE WHEN :wasCorrect = 1 THEN 0 ELSE 1 END),
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
        wasCorrect: Boolean,
        seedOtherDirection: Boolean = false,
        seedDueAt: Long = 0L,
    )

    // Symmetric twin of [applyFsrsReview] for the backward direction: on first-ever
    // introduction it can seed the forward direction's due date instead.
    @Query(
        """
        UPDATE vocabulary
        SET
            backwardFsrsCardJson = :cardJson,
            backwardFsrsDueAt = :dueAt,
            lastShownAt = :reviewedAt,
            backwardCorrectCount = backwardCorrectCount + (CASE WHEN :wasCorrect = 1 THEN 1 ELSE 0 END),
            backwardIncorrectCount = backwardIncorrectCount + (CASE WHEN :wasCorrect = 1 THEN 0 ELSE 1 END),
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
        wasCorrect: Boolean,
        seedOtherDirection: Boolean = false,
        seedDueAt: Long = 0L,
    )

    @Update(entity = VocabularyEntity::class)
    suspend fun restoreFsrsState(state: VocabularyFsrsStateRestore)

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
}
