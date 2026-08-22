package com.procrastilearn.app.data.local.mapper

import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.local.entity.PendingWordEntity
import com.procrastilearn.app.domain.model.AiTranslationDirection
import com.procrastilearn.app.domain.model.PendingWord
import org.junit.Test

class PendingWordMapperTest {
    @Test
    fun `toDomain maps id, word and createdAt through unchanged`() {
        val entity =
            PendingWordEntity(
                id = 7,
                word = "lernen",
                direction = AiTranslationDirection.TARGET_TO_NATIVE.name,
                createdAt = 12_345L,
            )

        val result = entity.toDomain()

        assertThat(result.id).isEqualTo(7)
        assertThat(result.word).isEqualTo("lernen")
        assertThat(result.createdAt).isEqualTo(12_345L)
    }

    @Test
    fun `toDomain parses TARGET_TO_NATIVE direction`() {
        val entity = PendingWordEntity(word = "lernen", direction = "TARGET_TO_NATIVE")

        assertThat(entity.toDomain().direction).isEqualTo(AiTranslationDirection.TARGET_TO_NATIVE)
    }

    @Test
    fun `toDomain parses NATIVE_TO_TARGET direction`() {
        val entity = PendingWordEntity(word = "lernen", direction = "NATIVE_TO_TARGET")

        assertThat(entity.toDomain().direction).isEqualTo(AiTranslationDirection.NATIVE_TO_TARGET)
    }

    @Test
    fun `toDomain falls back to TARGET_TO_NATIVE for an unrecognized direction string`() {
        val entity = PendingWordEntity(word = "lernen", direction = "SOME_UNKNOWN_ENUM_VALUE")

        assertThat(entity.toDomain().direction).isEqualTo(AiTranslationDirection.TARGET_TO_NATIVE)
    }

    @Test
    fun `toDomain falls back to TARGET_TO_NATIVE for a blank direction string`() {
        val entity = PendingWordEntity(word = "lernen", direction = "")

        assertThat(entity.toDomain().direction).isEqualTo(AiTranslationDirection.TARGET_TO_NATIVE)
    }

    @Test
    fun `toEntity maps id, word and createdAt through unchanged`() {
        val word =
            PendingWord(
                id = 9,
                word = "sprechen",
                direction = AiTranslationDirection.NATIVE_TO_TARGET,
                createdAt = 99_999L,
            )

        val result = word.toEntity()

        assertThat(result.id).isEqualTo(9)
        assertThat(result.word).isEqualTo("sprechen")
        assertThat(result.createdAt).isEqualTo(99_999L)
    }

    @Test
    fun `toEntity serializes direction using its enum name`() {
        val targetToNative = PendingWord(word = "a", direction = AiTranslationDirection.TARGET_TO_NATIVE)
        val nativeToTarget = PendingWord(word = "b", direction = AiTranslationDirection.NATIVE_TO_TARGET)

        assertThat(targetToNative.toEntity().direction).isEqualTo("TARGET_TO_NATIVE")
        assertThat(nativeToTarget.toEntity().direction).isEqualTo("NATIVE_TO_TARGET")
    }

    @Test
    fun `round trip through entity preserves every field`() {
        val original =
            PendingWord(
                id = 3,
                word = "gehen",
                direction = AiTranslationDirection.NATIVE_TO_TARGET,
                createdAt = 42L,
            )

        val roundTripped = original.toEntity().toDomain()

        assertThat(roundTripped).isEqualTo(original)
    }
}
