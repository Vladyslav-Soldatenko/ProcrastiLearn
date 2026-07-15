package com.procrastilearn.app.data.translation

import com.google.common.truth.Truth.assertThat
import com.openai.errors.OpenAIIoException
import com.openai.errors.OpenAIServiceException
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

class TranslationErrorClassifierTest {
    private val classifier = TranslationErrorClassifier()

    @Test
    fun `classifies 401 unauthorized as permanent`() {
        val error = serviceException(statusCode = 401, message = "401: unauthorized")

        val result = classifier.classify(error)

        assertThat(result).isEqualTo(ErrorClassification.Permanent("401: unauthorized"))
    }

    @Test
    fun `classifies 400 bad request as permanent`() {
        val error = serviceException(statusCode = 400, message = "400: bad request")

        val result = classifier.classify(error)

        assertThat(result).isInstanceOf(ErrorClassification.Permanent::class.java)
    }

    @Test
    fun `classifies 403 permission denied as permanent`() {
        val error = serviceException(statusCode = 403, message = "403: forbidden")

        val result = classifier.classify(error)

        assertThat(result).isInstanceOf(ErrorClassification.Permanent::class.java)
    }

    @Test
    fun `classifies 404 not found as permanent`() {
        val error = serviceException(statusCode = 404, message = "404: not found")

        val result = classifier.classify(error)

        assertThat(result).isInstanceOf(ErrorClassification.Permanent::class.java)
    }

    @Test
    fun `classifies 422 unprocessable entity as permanent`() {
        val error = serviceException(statusCode = 422, message = "422: unprocessable")

        val result = classifier.classify(error)

        assertThat(result).isInstanceOf(ErrorClassification.Permanent::class.java)
    }

    @Test
    fun `classifies 429 rate limit as transient`() {
        val error = serviceException(statusCode = 429, message = "429: rate limited")

        val result = classifier.classify(error)

        assertThat(result).isEqualTo(ErrorClassification.Transient("429: rate limited"))
    }

    @Test
    fun `classifies 500 internal server error as transient`() {
        val error = serviceException(statusCode = 500, message = "500: server error")

        val result = classifier.classify(error)

        assertThat(result).isInstanceOf(ErrorClassification.Transient::class.java)
    }

    @Test
    fun `classifies 503 as transient`() {
        val error = serviceException(statusCode = 503, message = "503: unavailable")

        val result = classifier.classify(error)

        assertThat(result).isInstanceOf(ErrorClassification.Transient::class.java)
    }

    @Test
    fun `classifies network io failures as transient`() {
        val error = OpenAIIoException("connection timed out")

        val result = classifier.classify(error)

        assertThat(result).isEqualTo(ErrorClassification.Transient("connection timed out"))
    }

    @Test
    fun `classifies blank api key as permanent`() {
        val error = IllegalArgumentException("Missing OpenAI API key")

        val result = classifier.classify(error)

        assertThat(result).isEqualTo(ErrorClassification.Permanent("Missing OpenAI API key"))
    }

    @Test
    fun `classifies unknown exceptions as transient by default`() {
        val error = IllegalStateException("OpenAI returned an empty response")

        val result = classifier.classify(error)

        assertThat(result).isEqualTo(ErrorClassification.Transient("OpenAI returned an empty response"))
    }

    @Test
    fun `classifies a null error as transient with a default message`() {
        val result = classifier.classify(null)

        assertThat(result).isEqualTo(ErrorClassification.Transient("Unknown error"))
    }

    @Test
    fun `falls back to default message when the exception message is null`() {
        val error = serviceException(statusCode = 500, message = null)

        val result = classifier.classify(error)

        assertThat(result).isEqualTo(ErrorClassification.Transient("Unknown error"))
    }

    private fun serviceException(
        statusCode: Int,
        message: String?,
    ): OpenAIServiceException {
        val exception = mockk<OpenAIServiceException>()
        every { exception.statusCode() } returns statusCode
        every { exception.message } returns message
        return exception
    }
}
