package com.procrastilearn.app.data.repository

import com.procrastilearn.app.data.local.dao.PendingWordDao
import com.procrastilearn.app.data.local.mapper.toDomain
import com.procrastilearn.app.data.local.mapper.toEntity
import com.procrastilearn.app.domain.model.AiTranslationDirection
import com.procrastilearn.app.domain.model.PendingWord
import com.procrastilearn.app.domain.repository.PendingWordRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PendingWordRepositoryImpl
    @Inject
    constructor(
        private val pendingWordDao: PendingWordDao,
    ) : PendingWordRepository {
        private val io = Dispatchers.IO

        override fun observePendingWords(): Flow<List<PendingWord>> =
            pendingWordDao.getAllPendingWords().map { list -> list.map { it.toDomain() } }

        override suspend fun getAllPendingWordsSnapshot(): List<PendingWord> =
            withContext(io) {
                pendingWordDao.getAllPendingWordsSnapshot().map { it.toDomain() }
            }

        override suspend fun queuePendingWord(
            word: String,
            direction: AiTranslationDirection,
        ): Unit =
            withContext(io) {
                val entity = PendingWord(word = word, direction = direction).toEntity()
                pendingWordDao.insertPendingWord(entity)
            }

        override suspend fun deletePendingWord(pendingWord: PendingWord): Unit =
            withContext(io) {
                pendingWordDao.deletePendingWord(pendingWord.toEntity())
            }

        override suspend fun deletePendingWordById(id: Long): Unit =
            withContext(io) {
                pendingWordDao.deletePendingWordById(id)
            }
    }
