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

@Suppress("TooManyFunctions")
@Dao
interface VocabularyDao {
    // Existing
    @Query("SELECT * FROM vocabulary ORDER BY position ASC, id ASC")
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

    @Insert
    suspend fun insertAllVocabulary(items: List<VocabularyEntity>)

    @Update
    suspend fun updateVocabulary(item: VocabularyEntity)

    @Update
    suspend fun updateAllVocabulary(items: List<VocabularyEntity>)

    @Transaction
    suspend fun applyImportBatch(
        toInsert: List<VocabularyEntity>,
        toUpdate: List<VocabularyEntity>,
    ) {
        if (toInsert.isNotEmpty()) {
            var nextPosition = getMaxPosition() + 1
            insertAllVocabulary(toInsert.map { it.copy(position = nextPosition++) })
        }
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
