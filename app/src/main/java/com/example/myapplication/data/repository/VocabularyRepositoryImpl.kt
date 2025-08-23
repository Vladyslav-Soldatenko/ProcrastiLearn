package com.example.myapplication.data.repository

import android.util.Log
import com.example.myapplication.data.local.dao.VocabularyDao
import com.example.myapplication.data.local.entity.VocabularyEntity
import com.example.myapplication.domain.model.VocabularyItem
import com.example.myapplication.domain.repository.VocabularyRepository
import io.github.openspacedrepetition.Card
import io.github.openspacedrepetition.Rating
import io.github.openspacedrepetition.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class VocabularyRepositoryImpl @Inject constructor(
    private val vocabularyDao: VocabularyDao,
    private val scheduler: Scheduler,
) : VocabularyRepository {

    private val _currentItem = MutableStateFlow<VocabularyItem?>(null)
    private val io = Dispatchers.IO

    // Add this to track the last shown item ID to avoid repeats
    private var lastShownId: Long? = null

    override fun observeCurrentItem(): Flow<VocabularyItem> =
        _currentItem.asStateFlow().filterNotNull()

    override fun getAllVocabulary(): Flow<List<VocabularyItem>> {
        return vocabularyDao.getAllVocabulary()
            .onEach { list -> Log.i("fsrs", list.toString()) }  // side effect
            .map { list -> list.map { it.toDomain() } }
    }


    override suspend fun addVocabularyItem(item: VocabularyItem): Unit = withContext(io) {
        val cardJson = Card.builder().build().toJson()
        val dueAt = Instant.now().toEpochMilli()
        vocabularyDao.insertVocabulary(item.toEntity(fsrsCardJson = cardJson, fsrsDueAt = dueAt))
    }

    override suspend fun deleteVocabularyItem(item: VocabularyItem) = withContext(io) {
        vocabularyDao.deleteVocabulary(item.toEntity())
    }

    override suspend fun getNextVocabularyItem(): VocabularyItem = withContext(io) {
        val now = System.currentTimeMillis()

        // 1) Prefer due/overdue (excluding the last shown)
        vocabularyDao.getEarliestDue(now)?.let {
            if (it.id != lastShownId) {
                val item = it.toDomain()
                _currentItem.value = item
                lastShownId = item.id
                return@withContext item
            }
        }

        // 2) Sometimes inject a new card
        val hasNew = vocabularyDao.countNew() > 0
        if (hasNew && Random.nextDouble() < MIX_NEW_PROBABILITY) {
            vocabularyDao.getRandomNew()?.let {
                if (it.id != lastShownId) {
                    val item = it.ensureFsrs().toDomain()
                    _currentItem.value = item
                    lastShownId = item.id
                    return@withContext item
                }
            }
        }

        // 3) Otherwise take the nearest upcoming due
        val candidate = vocabularyDao.getNearestDue() ?: vocabularyDao.getRandomAny()
        val item = candidate?.ensureFsrs()?.toDomain()
            ?: throw NoSuchElementException("No vocabulary items in database")

        // If we got the same item, try to get a different one
        if (item.id == lastShownId && vocabularyDao.getVocabularyCount() > 1) {
            val alternativeItem = vocabularyDao.getRandomAny()?.toDomain()
            if (alternativeItem != null && alternativeItem.id != lastShownId) {
                _currentItem.value = alternativeItem
                lastShownId = alternativeItem.id
                return@withContext alternativeItem
            }
        }

        _currentItem.value = item
        lastShownId = item.id
        return@withContext item
    }

    override suspend fun reviewVocabularyItem(id: Long, rating: Rating) : Unit = withContext(io) {
        Log.i("fsrs", "reviewng $id")
        val entity = vocabularyDao.getVocabularyById(id)
            ?: throw IllegalArgumentException("Vocabulary $id not found")

        val card = if (entity.fsrsCardJson.isBlank())
            Card.builder().build()
        else
            Card.fromJson(entity.fsrsCardJson)

        val result = scheduler.reviewCard(card, rating)
        val updatedCard = result.card()
        val log = result.reviewLog()

        val reviewedAt = log.reviewDatetime().toEpochMilli()
        val nextDue = updatedCard.getDue().toEpochMilli()

        val incCorrect = if (rating == Rating.AGAIN) 0 else 1
        val incIncorrect = if (rating == Rating.AGAIN) 1 else 0

        vocabularyDao.applyFsrsReview(
            id = id,
            cardJson = updatedCard.toJson(),
            dueAt = nextDue,
            reviewedAt = reviewedAt,
            incCorrect = incCorrect,
            incIncorrect = incIncorrect
        )

        // IMPORTANT: Clear current item after review to force new item on next call
        _currentItem.value = null

        Log.i("FSRS", "Reviewed $id as $rating; next due at $nextDue")
    }

    // --- Helpers ---
    private fun VocabularyEntity.ensureFsrs(): VocabularyEntity {
        if (fsrsCardJson.isNotBlank()) return this
        val card = Card.builder().build()
        return copy(fsrsCardJson = card.toJson(), fsrsDueAt = Instant.now().toEpochMilli())
    }

    private fun VocabularyEntity.toDomain(): VocabularyItem =
        VocabularyItem(
            id = id,
            word = word,
            translation = translation
        )

    private fun VocabularyItem.toEntity(
        fsrsCardJson: String = "",
        fsrsDueAt: Long = 0L
    ): VocabularyEntity =
        VocabularyEntity(
            id = id,
            word = word,
            translation = translation,
            fsrsCardJson = fsrsCardJson,
            fsrsDueAt = fsrsDueAt
        )

    companion object {
        private const val MIX_NEW_PROBABILITY = 0.35 // 35% chance to slip in a new card when none due
    }
}
