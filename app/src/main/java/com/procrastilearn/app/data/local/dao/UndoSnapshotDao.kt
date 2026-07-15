package com.procrastilearn.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.procrastilearn.app.data.local.entity.UndoSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UndoSnapshotDao {
    @Insert
    suspend fun insert(snapshot: UndoSnapshotEntity): Long

    @Query("SELECT * FROM undo_snapshot ORDER BY id DESC LIMIT 1")
    suspend fun peekLatest(): UndoSnapshotEntity?

    @Query("SELECT COUNT(*) FROM undo_snapshot")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM undo_snapshot")
    suspend fun count(): Int

    @Query("DELETE FROM undo_snapshot WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM undo_snapshot WHERE vocabId = :vocabId")
    suspend fun deleteForVocab(vocabId: Long)

    @Query(
        """
        DELETE FROM undo_snapshot
        WHERE id NOT IN (SELECT id FROM undo_snapshot ORDER BY id DESC LIMIT :keep)
        """,
    )
    suspend fun trimToLast(keep: Int)

    @Query("DELETE FROM undo_snapshot")
    suspend fun deleteAll()
}
