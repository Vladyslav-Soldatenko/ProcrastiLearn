package com.procrastilearn.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.procrastilearn.app.data.local.entity.PendingWordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingWordDao {
    @Query("SELECT * FROM pending_words ORDER BY createdAt ASC")
    fun getAllPendingWords(): Flow<List<PendingWordEntity>>

    @Query("SELECT * FROM pending_words ORDER BY createdAt ASC")
    suspend fun getAllPendingWordsSnapshot(): List<PendingWordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingWord(item: PendingWordEntity): Long

    @Update
    suspend fun updatePendingWord(item: PendingWordEntity)

    @Query(
        """
        UPDATE pending_words
        SET status = 'PENDING', retryCount = 0, nextAttemptAt = 0, lastError = NULL
        WHERE id = :id
        """,
    )
    suspend fun resetPendingWordForRetry(id: Long)

    @Delete
    suspend fun deletePendingWord(item: PendingWordEntity)

    @Query("DELETE FROM pending_words WHERE id = :id")
    suspend fun deletePendingWordById(id: Long)
}
