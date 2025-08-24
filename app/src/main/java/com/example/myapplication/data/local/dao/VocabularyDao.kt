package com.example.myapplication.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.myapplication.data.local.entity.VocabularyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VocabularyDao {

    // Existing
    @Query("SELECT * FROM vocabulary")
    fun getAllVocabulary(): Flow<List<VocabularyEntity>>

    @Query("SELECT * FROM vocabulary ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomAny(): VocabularyEntity?

    @Query("SELECT * FROM vocabulary WHERE id = :id")
    suspend fun getVocabularyById(id: Long): VocabularyEntity?

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
    @Query("""
        SELECT * FROM vocabulary 
        WHERE fsrsDueAt > 0 AND fsrsDueAt <= :now 
        ORDER BY fsrsDueAt ASC 
        LIMIT 1
    """)
    suspend fun getEarliestDue(now: Long): VocabularyEntity?

    // Nearest upcoming due (when nothing is due)
    @Query("""
        SELECT * FROM vocabulary 
        WHERE fsrsDueAt > 0 
        ORDER BY fsrsDueAt ASC 
        LIMIT 1
    """)
    suspend fun getNearestDue(): VocabularyEntity?

    // “New” is inferred by counts == 0
    @Query("""
        SELECT COUNT(*) FROM vocabulary 
        WHERE correctCount = 0 AND incorrectCount = 0
    """)
    suspend fun countNew(): Int

    @Query("""
        SELECT * FROM vocabulary 
        WHERE correctCount = 0 AND incorrectCount = 0 
        ORDER BY RANDOM() 
        LIMIT 1
    """)
    suspend fun getRandomNew(): VocabularyEntity?

    // Apply a review atomically
    @Query("""
        UPDATE vocabulary 
        SET 
            fsrsCardJson = :cardJson,
            fsrsDueAt = :dueAt,
            lastShownAt = :reviewedAt,
            correctCount = correctCount + :incCorrect,
            incorrectCount = incorrectCount + :incIncorrect
        WHERE id = :id
    """)
    suspend fun applyFsrsReview(
        id: Long,
        cardJson: String,
        dueAt: Long,
        reviewedAt: Long,
        incCorrect: Int,
        incIncorrect: Int
    )

    @Query("""
        SELECT * FROM vocabulary 
        WHERE fsrsDueAt > 0 AND fsrsDueAt <= :now 
        ORDER BY fsrsDueAt ASC
    """)
    suspend fun getDueCards(now: Long): List<VocabularyEntity>

    // Get new cards (never reviewed)
    @Query("""
        SELECT * FROM vocabulary 
        WHERE correctCount = 0 AND incorrectCount = 0
        ORDER BY id ASC
        LIMIT :limit
    """)
    suspend fun getNewCards(limit: Int): List<VocabularyEntity>

    // Count today's new cards studied
    @Query("""
        SELECT COUNT(*) FROM vocabulary 
        WHERE lastShownAt >= :startOfDay 
        AND correctCount + incorrectCount = 1
    """)
    suspend fun countNewCardsStudiedToday(startOfDay: Long): Int

    // Get a single card by ID
    @Query("SELECT * FROM vocabulary WHERE id = :id LIMIT 1")
    suspend fun getCard(id: Long): VocabularyEntity?

    @Query("SELECT COUNT(*) FROM vocabulary WHERE fsrsDueAt > 0 AND fsrsDueAt <= :now")
    suspend fun countReviewsDue(now: Long): Int

    @Query("SELECT COUNT(*) FROM vocabulary WHERE correctCount = 0 AND incorrectCount = 0")
    suspend fun countNewTotal(): Int

    // Pick next review (due now), earliest first
    @Query("""
        SELECT id FROM vocabulary 
        WHERE fsrsDueAt > 0 AND fsrsDueAt <= :now 
        ORDER BY fsrsDueAt ASC 
        LIMIT 1
    """)
    suspend fun pickNextReviewId(now: Long): Long?

    // Pick next upcoming (when nothing is due)
    @Query("""
        SELECT id FROM vocabulary 
        WHERE fsrsDueAt > :now 
        ORDER BY fsrsDueAt ASC 
        LIMIT 1
    """)
    suspend fun pickNextUpcomingId(now: Long): Long?

    // Pick a "new" by offset (efficient alternative to ORDER BY RANDOM())
    @Query("""
        SELECT id FROM vocabulary 
        WHERE correctCount = 0 AND incorrectCount = 0
        LIMIT 1 OFFSET :offset
    """)
    suspend fun pickNewIdByOffset(offset: Int): Long?

    // Fallback: random any, excluding the last shown (still small; OK)
    @Query("""
        SELECT id FROM vocabulary 
        WHERE (:excludeId IS NULL OR id != :excludeId)
        ORDER BY RANDOM() 
        LIMIT 1
    """)
    suspend fun pickRandomAnyId(excludeId: Long?): Long?

}
