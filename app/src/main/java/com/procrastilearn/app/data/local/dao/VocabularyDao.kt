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
        WHERE normalizedWord = :normalizedWord
        LIMIT 1
        """,
    )
    suspend fun getVocabularyByWord(normalizedWord: String): VocabularyEntity?

    @Query(
        """
        SELECT * FROM vocabulary
        WHERE normalizedWord IN (:normalizedWords)
        """,
    )
    suspend fun getVocabularyByWords(normalizedWords: List<String>): List<VocabularyEntity>

    @Query("SELECT COALESCE(MAX(position), 0) FROM vocabulary")
    suspend fun getMaxPosition(): Long

    @Query("SELECT id FROM vocabulary ORDER BY position ASC, id ASC")
    suspend fun getAllIdsOrderedByPosition(): List<Long>

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

    @Query("UPDATE vocabulary SET position = :position WHERE id = :id")
    suspend fun updatePosition(
        id: Long,
        position: Long,
    )

    @Transaction
    suspend fun reorderVocabulary(orderedIds: List<Long>) {
        orderedIds.forEachIndexed { index, id -> updatePosition(id, -(index + 1L)) }
        orderedIds.forEachIndexed { index, id -> updatePosition(id, index + 1L) }
    }

    @Transaction
    suspend fun deleteVocabularyAndRenumber(items: List<VocabularyEntity>) {
        if (items.isEmpty()) return
        deleteVocabulary(items)
        reorderVocabulary(getAllIdsOrderedByPosition())
    }

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
