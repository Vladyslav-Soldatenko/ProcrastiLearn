package com.procrastilearn.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.procrastilearn.app.data.local.entity.VocabularyEntity
import kotlinx.coroutines.flow.Flow

// Arity from covering every read/write shape a single `vocabulary` table CRUD DAO needs
// (single vs. bulk insert/update/delete, word/id lookups, import support), not from an
// undecomposed monolith - review/stats-specific queries already live in their own DAOs.
@Suppress("TooManyFunctions")
@Dao
interface VocabularyDao {
    // Existing
    @Query("SELECT * FROM vocabulary")
    fun getAllVocabulary(): Flow<List<VocabularyEntity>>

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

    @Query(
        """
        SELECT * FROM vocabulary
        WHERE word COLLATE NOCASE IN (:words)
        """,
    )
    suspend fun getVocabularyByWords(words: List<String>): List<VocabularyEntity>

    @Query("SELECT COALESCE(MAX(position), 0) FROM vocabulary")
    suspend fun getMaxPosition(): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVocabulary(item: VocabularyEntity): Long

    // Deliberately plain ABORT (no REPLACE): callers are expected to have already
    // split their batch into genuinely-new rows (this) vs existing-row updates
    // (updateAllVocabulary), via a word-based lookup — see VocabularyTransferManager.
    // ABORT-on-conflict here is a correctness safety net, not the primary dedup path.
    @Insert
    suspend fun insertAllVocabulary(items: List<VocabularyEntity>)

    @Update
    suspend fun updateVocabulary(item: VocabularyEntity)

    @Update
    suspend fun updateAllVocabulary(items: List<VocabularyEntity>)

    // Applies a pre-split import batch atomically: brand-new rows are inserted,
    // rows that matched an existing word (by VocabularyTransferManager) are updated.
    @Transaction
    suspend fun applyImportBatch(
        toInsert: List<VocabularyEntity>,
        toUpdate: List<VocabularyEntity>,
    ) {
        if (toInsert.isNotEmpty()) insertAllVocabulary(toInsert)
        if (toUpdate.isNotEmpty()) updateAllVocabulary(toUpdate)
    }

    @Delete
    suspend fun deleteVocabulary(item: VocabularyEntity)

    @Delete
    suspend fun deleteVocabulary(items: List<VocabularyEntity>)

    @Query("DELETE FROM vocabulary")
    suspend fun deleteAllVocabulary()

    @Query(
        """
        UPDATE vocabulary
        SET
            bidirectional = :bidirectional,
            backwardFsrsDueAt = CASE
                WHEN :bidirectional = 1 AND fsrsDueAt != 0 AND backwardFsrsDueAt = 0 THEN :seedDueAt
                ELSE backwardFsrsDueAt
            END
        WHERE id IN (:ids)
    """,
    )
    suspend fun setBidirectional(
        ids: List<Long>,
        bidirectional: Boolean,
        seedDueAt: Long,
    )
}
