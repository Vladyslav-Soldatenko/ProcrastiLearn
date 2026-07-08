package com.procrastilearn.app.data.sync

import com.procrastilearn.app.data.connectivity.NetworkConnectivityObserver
import com.procrastilearn.app.domain.model.PendingWord
import com.procrastilearn.app.domain.repository.PendingWordRepository
import com.procrastilearn.app.domain.usecase.AddVocabularyItemUseCase
import com.procrastilearn.app.domain.usecase.GenerateAiTranslationUseCase
import com.procrastilearn.app.domain.usecase.GetVocabularyItemByWordUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide singleton that retries AI translation for words queued while offline
 * (see [com.procrastilearn.app.domain.usecase.QueuePendingWordUseCase]) whenever
 * connectivity is restored, for as long as the app process is alive.
 */
@Singleton
class PendingWordSyncManager
    @Inject
    constructor(
        private val connectivityObserver: NetworkConnectivityObserver,
        private val pendingWordRepository: PendingWordRepository,
        private val generateAiTranslationUseCase: GenerateAiTranslationUseCase,
        private val addVocabularyItemUseCase: AddVocabularyItemUseCase,
        private val getVocabularyItemByWordUseCase: GetVocabularyItemByWordUseCase,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var started = false

        fun start() {
            if (started) return
            started = true
            scope.launch {
                connectivityObserver.observe()
                    .filter { online -> online }
                    .collect { syncPendingWords() }
            }
        }

        suspend fun syncPendingWords() {
            val pending = pendingWordRepository.getAllPendingWordsSnapshot()
            for (item in pending) {
                processPendingWord(item)
            }
        }

        @Suppress("TooGenericExceptionCaught")
        private suspend fun processPendingWord(item: PendingWord) {
            try {
                val existing = getVocabularyItemByWordUseCase(item.word)
                if (existing != null) {
                    pendingWordRepository.deletePendingWord(item)
                    return
                }

                val translation = generateAiTranslationUseCase(item.word, item.direction).trim()
                if (translation.isBlank()) return

                val result = addVocabularyItemUseCase(word = item.word, translation = translation)
                if (result.isSuccess) {
                    pendingWordRepository.deletePendingWord(item)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Leave it pending; it will be retried on the next reconnect. No user is
                // present to be notified, so we deliberately stay silent here.
            }
        }
    }
