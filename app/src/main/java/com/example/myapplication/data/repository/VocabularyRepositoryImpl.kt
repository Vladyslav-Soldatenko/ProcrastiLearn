package com.example.myapplication.data.repository

import android.util.Log
import com.example.myapplication.data.local.dao.VocabularyDao
import com.example.myapplication.data.local.mapper.toDomainModel
import com.example.myapplication.data.local.mapper.toEntity
import com.example.myapplication.domain.model.VocabularyItem
import com.example.myapplication.domain.repository.VocabularyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VocabularyRepositoryImpl @Inject constructor(
    private val vocabularyDao: VocabularyDao
) : VocabularyRepository {

    private val _currentItem = MutableStateFlow<VocabularyItem?>(null)

    override suspend fun getRandomVocabularyItem(): VocabularyItem {
        getAllVocabulary().collect {
            Log.i("LaunchableApp", it.toString())

        }
        val entity = vocabularyDao.getRandomVocabulary()
            ?: throw NoSuchElementException("No vocabulary items in database")

        val item = entity.toDomainModel()
        _currentItem.value = item
        return item
    }


    override fun observeCurrentItem(): Flow<VocabularyItem> {
        return _currentItem.asStateFlow().filterNotNull()
    }

    // Additional method to get all vocabulary
    override fun getAllVocabulary(): Flow<List<VocabularyItem>> {
        return vocabularyDao.getAllVocabulary()
            .map { entities -> entities.map { it.toDomainModel() } }
    }

    override suspend fun addVocabularyItem(item: VocabularyItem) {
        vocabularyDao.insertVocabulary(item.toEntity())
    }

    override suspend fun deleteVocabularyItem(item: VocabularyItem) {
        vocabularyDao.deleteVocabulary(item.toEntity())
    }
}