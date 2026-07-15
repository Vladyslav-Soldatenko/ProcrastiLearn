package com.procrastilearn.app.data.local.mapper

import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.local.entity.PendingWordEntity
import com.procrastilearn.app.domain.model.AiTranslationDirection
import com.procrastilearn.app.domain.model.PendingWord
import com.procrastilearn.app.domain.model.PendingWordStatus
import org.junit.Test

class PendingWordMapperTest {
    @Test
    fun `toDomain maps retry state fields`() {
        val entity =
            PendingWordEntity(
                id = 3,
                word = "Haus",
                direction = "EN_TO_RU",
                createdAt = 111L,
                status = "FAILED",
                retryCount = 4,
                nextAttemptAt = 999L,
                lastError = "401: unauthorized",
            )

        val result = entity.toDomain()

        assertThat(result.id).isEqualTo(3)
        assertThat(result.status).isEqualTo(PendingWordStatus.FAILED)
        assertThat(result.retryCount).isEqualTo(4)
        assertThat(result.nextAttemptAt).isEqualTo(999L)
        assertThat(result.lastError).isEqualTo("401: unauthorized")
    }

    @Test
    fun `toDomain falls back to pending for an unrecognized status string`() {
        val entity =
            PendingWordEntity(
                word = "Haus",
                direction = "EN_TO_RU",
                status = "SOMETHING_UNKNOWN",
            )

        val result = entity.toDomain()

        assertThat(result.status).isEqualTo(PendingWordStatus.PENDING)
    }

    @Test
    fun `toDomain defaults to fresh retry state when columns are at their defaults`() {
        val entity = PendingWordEntity(word = "Haus", direction = "EN_TO_RU")

        val result = entity.toDomain()

        assertThat(result.status).isEqualTo(PendingWordStatus.PENDING)
        assertThat(result.retryCount).isEqualTo(0)
        assertThat(result.nextAttemptAt).isEqualTo(0L)
        assertThat(result.lastError).isNull()
    }

    @Test
    fun `toEntity maps retry state fields`() {
        val domain =
            PendingWord(
                id = 8,
                word = "Auto",
                direction = AiTranslationDirection.RU_TO_EN,
                status = PendingWordStatus.FAILED,
                retryCount = 2,
                nextAttemptAt = 555L,
                lastError = "timeout",
            )

        val result = domain.toEntity()

        assertThat(result.status).isEqualTo("FAILED")
        assertThat(result.retryCount).isEqualTo(2)
        assertThat(result.nextAttemptAt).isEqualTo(555L)
        assertThat(result.lastError).isEqualTo("timeout")
    }

    @Test
    fun `round trip through entity preserves retry state`() {
        val original =
            PendingWord(
                id = 1,
                word = "Haus",
                direction = AiTranslationDirection.EN_TO_RU,
                status = PendingWordStatus.FAILED,
                retryCount = 3,
                nextAttemptAt = 4242L,
                lastError = "boom",
            )

        val result = original.toEntity().toDomain()

        assertThat(result).isEqualTo(original)
    }
}
