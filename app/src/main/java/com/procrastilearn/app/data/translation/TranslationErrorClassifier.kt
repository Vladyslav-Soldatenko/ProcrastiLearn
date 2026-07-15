package com.procrastilearn.app.data.translation

import com.openai.errors.OpenAIIoException
import com.openai.errors.OpenAIServiceException
import javax.inject.Inject

/**
 * Distinguishes translation failures a retry can plausibly fix (network blips, timeouts,
 * rate limits, server errors) from ones that will keep failing until the user intervenes
 * (bad API key, malformed request). Unknown failures default to transient since retries
 * are attempt-capped, so treating them as retryable first is the safer default.
 */
class TranslationErrorClassifier
    @Inject
    constructor() {
        fun classify(error: Throwable?): ErrorClassification {
            val message = error?.message ?: DEFAULT_MESSAGE
            return when {
                error is OpenAIServiceException && error.statusCode() in PERMANENT_STATUS_CODES ->
                    ErrorClassification.Permanent(message)
                error is IllegalArgumentException -> ErrorClassification.Permanent(message)
                error is OpenAIServiceException -> ErrorClassification.Transient(message)
                error is OpenAIIoException -> ErrorClassification.Transient(message)
                else -> ErrorClassification.Transient(message)
            }
        }

        private companion object {
            const val DEFAULT_MESSAGE = "Unknown error"
            val PERMANENT_STATUS_CODES = setOf(400, 401, 403, 404, 422)
        }
    }
