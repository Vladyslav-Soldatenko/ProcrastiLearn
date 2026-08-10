package com.procrastilearn.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.procrastilearn.app.data.local.entity.VocabularyEntity
import kotlinx.coroutines.flow.Flow

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVocabulary(item: VocabularyEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllVocabulary(items: List<VocabularyEntity>)

    @Update
    suspend fun updateVocabulary(item: VocabularyEntity)

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
