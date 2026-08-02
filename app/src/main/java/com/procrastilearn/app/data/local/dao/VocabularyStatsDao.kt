package com.procrastilearn.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VocabularyStatsDao {
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
