package com.procrastilearn.app.ui

import com.procrastilearn.app.domain.model.AiTranslationDirection
import com.procrastilearn.app.domain.model.VocabularyItem
import com.procrastilearn.app.domain.usecase.GenerateAiTranslationUseCase
import com.procrastilearn.app.domain.usecase.VocabularyEntryUseCases
import javax.inject.Inject

data class BidirectionalCardOptions(
    val bidirectional: Boolean,
    val backwardPromptOverride: String?,
    val backwardAnswerOverride: String?,
)

sealed interface ExistingWordPreflight {
    data object NoConflict : ExistingWordPreflight

    data class ConfirmationRequired(
        val word: String,
    ) : ExistingWordPreflight

    data class LookupFailed(
        val error: Throwable,
    ) : ExistingWordPreflight
}

sealed interface SubmissionResolution {
    data object NoConflict : SubmissionResolution

    data class ConfirmationRequired(
        val word: String,
    ) : SubmissionResolution

    data class Overridden(
        val fromPreview: Boolean,
    ) : SubmissionResolution

    data class OverrideFailed(
        val error: Throwable,
        val fromPreview: Boolean,
    ) : SubmissionResolution

    data class LookupFailed(
        val error: Throwable,
    ) : SubmissionResolution
}

sealed interface OverrideProceedResult {
    data object NoPendingOverride : OverrideProceedResult

    data object Overridden : OverrideProceedResult

    data class TranslationFailed(
        val error: Throwable,
    ) : OverrideProceedResult

    data class OverrideFailed(
        val error: Throwable,
    ) : OverrideProceedResult
}

/**
 * Owns the "word already exists" conflict flow for the add-word screen: deciding whether to
 * silently re-apply an override the user already acknowledged this session, or to pause and
 * wait for the user to confirm via the existing-word dialog.
 */
class ExistingWordOverrideCoordinator
    @Inject
    constructor(
        private val vocabularyEntryUseCases: VocabularyEntryUseCases,
        private val generateAiTranslationUseCase: GenerateAiTranslationUseCase,
    ) {
        private data class PendingOverrideSubmission(
            val existingItem: VocabularyItem,
            val word: String,
            val translation: String?,
            val direction: AiTranslationDirection,
            val fromPreview: Boolean,
        )

        private var pendingOverride: PendingOverrideSubmission? = null
        private var acknowledgedOverrideWord: String? = null

        fun hasPendingOverride(): Boolean = pendingOverride != null

        fun resetForNewWord() {
            pendingOverride = null
            acknowledgedOverrideWord = null
        }

        fun clearAcknowledgement() {
            acknowledgedOverrideWord = null
        }

        fun acknowledge(word: String) {
            acknowledgedOverrideWord = word
        }

        fun cancelPendingOverride(): Boolean {
            val fromPreview = pendingOverride?.fromPreview == true
            pendingOverride = null
            return fromPreview
        }

        suspend fun checkBeforeAiTranslationRequest(
            word: String,
            direction: AiTranslationDirection,
        ): ExistingWordPreflight {
            val existingItem =
                runCatching { vocabularyEntryUseCases.getByWord(word) }
                    .getOrElse { return ExistingWordPreflight.LookupFailed(it) }
                    ?: return ExistingWordPreflight.NoConflict

            pendingOverride =
                PendingOverrideSubmission(
                    existingItem = existingItem,
                    word = word,
                    translation = null,
                    direction = direction,
                    fromPreview = false,
                )
            return ExistingWordPreflight.ConfirmationRequired(word)
        }

        suspend fun resolveForSubmission(
            word: String,
            translation: String,
            direction: AiTranslationDirection,
            fromPreview: Boolean,
            resolveCardOptions: () -> BidirectionalCardOptions,
        ): SubmissionResolution =
            runCatching { vocabularyEntryUseCases.getByWord(word) }.fold(
                onSuccess = { existingItem ->
                    when {
                        existingItem == null -> {
                            SubmissionResolution.NoConflict
                        }
                        acknowledgedOverrideWord?.equals(word, ignoreCase = true) != true -> {
                            pendingOverride =
                                PendingOverrideSubmission(
                                    existingItem = existingItem,
                                    word = word,
                                    translation = translation,
                                    direction = direction,
                                    fromPreview = fromPreview,
                                )
                            SubmissionResolution.ConfirmationRequired(word)
                        }
                        else -> {
                            applyOverride(existingItem, word, translation, resolveCardOptions())
                                .also { acknowledgedOverrideWord = null }
                                .fold(
                                    onSuccess = { SubmissionResolution.Overridden(fromPreview) },
                                    onFailure = { error -> SubmissionResolution.OverrideFailed(error, fromPreview) },
                                )
                        }
                    }
                },
                onFailure = { SubmissionResolution.LookupFailed(it) },
            )

        suspend fun proceedWithPendingOverride(
            resolveCardOptions: () -> BidirectionalCardOptions,
        ): OverrideProceedResult {
            val pending = pendingOverride ?: return OverrideProceedResult.NoPendingOverride

            val translation =
                resolvePendingTranslation(pending).getOrElse { error ->
                    pendingOverride = null
                    return OverrideProceedResult.TranslationFailed(error)
                }

            return applyOverride(pending.existingItem, pending.word, translation, resolveCardOptions()).fold(
                onSuccess = {
                    pendingOverride = null
                    acknowledgedOverrideWord = null
                    OverrideProceedResult.Overridden
                },
                onFailure = { error ->
                    pendingOverride = null
                    OverrideProceedResult.OverrideFailed(error)
                },
            )
        }

        private class BlankAiTranslationException : Exception()

        private suspend fun resolvePendingTranslation(pending: PendingOverrideSubmission): Result<String> =
            pending.translation?.let { Result.success(it) }
                ?: runCatching { generateAiTranslationUseCase(pending.word, pending.direction) }
                    .mapCatching { generated -> generated.trim().ifBlank { throw BlankAiTranslationException() } }

        private suspend fun applyOverride(
            existingItem: VocabularyItem,
            word: String,
            translation: String,
            cardOptions: BidirectionalCardOptions,
        ): Result<Unit> =
            vocabularyEntryUseCases.override(
                existingItem = existingItem,
                newWord = word,
                newTranslation = translation,
                bidirectional = cardOptions.bidirectional,
                backwardPromptOverride = cardOptions.backwardPromptOverride,
                backwardAnswerOverride = cardOptions.backwardAnswerOverride,
            )
    }
