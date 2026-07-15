package com.procrastilearn.app.data.sync

import com.procrastilearn.app.data.connectivity.NetworkConnectivityObserver
import com.procrastilearn.app.data.translation.ErrorClassification
import com.procrastilearn.app.data.translation.TranslationErrorClassifier
import com.procrastilearn.app.domain.model.PendingWord
import com.procrastilearn.app.domain.model.PendingWordStatus
import com.procrastilearn.app.domain.repository.PendingWordRepository
import com.procrastilearn.app.domain.usecase.AddVocabularyItemUseCase
import com.procrastilearn.app.domain.usecase.GenerateAiTranslationUseCase
import com.procrastilearn.app.domain.usecase.GetVocabularyItemByWordUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide singleton that retries AI translation for queued words (see
 * [com.procrastilearn.app.domain.usecase.QueuePendingWordUseCase]) - both words queued while
 * offline and words queued after an online AI request failed - for as long as the app process
 * is alive.
 *
 * While online and the queue is non-empty, it polls on [POLL_INTERVAL_MS] and immediately
 * whenever the queue or connectivity changes (e.g. a new word is queued or the user taps
 * manual retry). Each pass only processes items whose [PendingWord.nextAttemptAt] has elapsed,
 * so transient failures back off exponentially instead of being hammered on every poll.
 * Permanent failures, and transient failures that exhaust [MAX_ATTEMPTS], are marked
 * [PendingWordStatus.FAILED] and are no longer auto-retried until the user retries manually.
 */
@Singleton
class PendingWordSyncManager
    @Inject
    @Suppress("LongParameterList")
    constructor(
        private val connectivityObserver: NetworkConnectivityObserver,
        private val pendingWordRepository: PendingWordRepository,
        private val generateAiTranslationUseCase: GenerateAiTranslationUseCase,
        private val addVocabularyItemUseCase: AddVocabularyItemUseCase,
        private val getVocabularyItemByWordUseCase: GetVocabularyItemByWordUseCase,
        private val errorClassifier: TranslationErrorClassifier,
        private val timeProvider: TimeProvider,
        dispatcher: CoroutineDispatcher,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + dispatcher)
        private var started = false

        fun start() {
            if (started) return
            started = true
            scope.launch {
                combine(
                    pendingWordRepository.observePendingWords(),
                    connectivityObserver.observe(),
                ) { pendingWords, online -> online && pendingWords.isNotEmpty() }
                    .collectLatest { shouldSync -> if (shouldSync) pollUntilCancelled() }
            }
        }

        private suspend fun pollUntilCancelled() {
            while (true) {
                syncPendingWords()
                delay(POLL_INTERVAL_MS)
            }
        }

        suspend fun syncPendingWords() {
            val now = timeProvider.now()
            val eligible =
                pendingWordRepository.getAllPendingWordsSnapshot()
                    .filter { it.status == PendingWordStatus.PENDING && it.nextAttemptAt <= now }
            for (item in eligible) {
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
                if (translation.isBlank()) {
                    reschedule(item, ErrorClassification.Transient(EMPTY_TRANSLATION_ERROR))
                    return
                }

                val result = addVocabularyItemUseCase(word = item.word, translation = translation)
                if (result.isSuccess) {
                    pendingWordRepository.deletePendingWord(item)
                } else {
                    reschedule(item, errorClassifier.classify(result.exceptionOrNull()))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reschedule(item, errorClassifier.classify(e))
            }
        }

        private suspend fun reschedule(
            item: PendingWord,
            classification: ErrorClassification,
        ) {
            val updated =
                when (classification) {
                    is ErrorClassification.Permanent ->
                        item.copy(status = PendingWordStatus.FAILED, lastError = classification.message)

                    is ErrorClassification.Transient -> {
                        val attempt = item.retryCount + 1
                        if (attempt >= MAX_ATTEMPTS) {
                            item.copy(
                                status = PendingWordStatus.FAILED,
                                retryCount = attempt,
                                lastError = classification.message,
                            )
                        } else {
                            item.copy(
                                retryCount = attempt,
                                nextAttemptAt = timeProvider.now() + backoffDelayMillis(attempt),
                                lastError = classification.message,
                            )
                        }
                    }
                }
            pendingWordRepository.updatePendingWord(updated)
        }

        private fun backoffDelayMillis(attempt: Int): Long {
            val multiplier = 1L shl (attempt - 1)
            return (BASE_BACKOFF_MS * multiplier).coerceAtMost(MAX_BACKOFF_MS)
        }

        companion object {
            private const val EMPTY_TRANSLATION_ERROR = "AI returned an empty translation"
            internal const val MAX_ATTEMPTS = 5
            internal const val BASE_BACKOFF_MS = 30_000L
            internal const val MAX_BACKOFF_MS = 3_600_000L
            internal const val POLL_INTERVAL_MS = 30_000L
        }
    }
