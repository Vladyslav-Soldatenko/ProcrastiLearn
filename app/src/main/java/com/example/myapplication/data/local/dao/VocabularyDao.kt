package com.example.myapplication.data.local.dao

import androidx.room.*
import com.example.myapplication.data.local.entity.VocabularyEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO - Data Access Object for vocabulary table.
 * Like a repository interface in Spring/NestJS.
 * Room generates the implementation automatically.
 */
@Dao
interface VocabularyDao {

    @Query("SELECT * FROM vocabulary")
    fun getAllVocabulary(): Flow<List<VocabularyEntity>>

    @Query("SELECT * FROM vocabulary ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomVocabulary(): VocabularyEntity?

    @Query("SELECT * FROM vocabulary WHERE id = :id")
    suspend fun getVocabularyById(id: Long): VocabularyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVocabulary(item: VocabularyEntity)

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
}