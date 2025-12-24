package com.procrastilearn.app.data.local.mapper

import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.local.entity.VocabularyEntity
import com.procrastilearn.app.domain.model.VocabularyExportItem
import com.procrastilearn.app.domain.model.VocabularyItem
import org.junit.Test

class VocabularyMapperTest {
    @Test
    fun `toDomain maps primitive fields and marks new items`() {
        val entity =
            VocabularyEntity(
                id = 11,
                word = "lernen",
                translation = "learn",
            )

        val result = entity.toDomain()

        assertThat(result.id).isEqualTo(11)
        assertThat(result.word).isEqualTo("lernen")
        assertThat(result.translation).isEqualTo("learn")
        assertThat(result.isNew).isTrue()
    }

    @Test
    fun `toDomain marks items with review history as not new`() {
        val entity =
            VocabularyEntity(
                id = 22,
                word = "lesen",
                translation = "read",
                correctCount = 1,
                incorrectCount = 3,
            )

        val result = entity.toDomain()

        assertThat(result.isNew).isFalse()
    }

    @Test
    fun `toEntity maps domain values with default scheduling metadata`() {
        val item =
            VocabularyItem(
                id = 5,
                word = "sehen",
                translation = "see",
                isNew = true,
            )

        val result = item.toEntity()

        assertThat(result.id).isEqualTo(5)
        assertThat(result.word).isEqualTo("sehen")
        assertThat(result.translation).isEqualTo("see")
        assertThat(result.fsrsCardJson).isEmpty()
        assertThat(result.fsrsDueAt).isEqualTo(0L)
    }

    @Test
    fun `toEntity passes through provided scheduling metadata`() {
        val item =
            VocabularyItem(
                id = 9,
                word = "gehen",
                translation = "go",
                isNew = false,
            )

        val result = item.toEntity(fsrsCardJson = "json", fsrsDueAt = 123L)

        assertThat(result.fsrsCardJson).isEqualTo("json")
        assertThat(result.fsrsDueAt).isEqualTo(123L)
    }

    @Test
    fun `export item maps all fields to entity`() {
        val item =
            VocabularyExportItem(
                id = 7,
                word = "gehen",
                translation = "go",
                createdAt = 111L,
                lastShownAt = 222L,
                correctCount = 3,
                incorrectCount = 4,
                fsrsCardJson = "{\"card\":1}",
                fsrsDueAt = 333L,
            )

        val result = item.toEntity()

        assertThat(result.id).isEqualTo(7)
        assertThat(result.word).isEqualTo("gehen")
        assertThat(result.translation).isEqualTo("go")
        assertThat(result.createdAt).isEqualTo(111L)
        assertThat(result.lastShownAt).isEqualTo(222L)
        assertThat(result.correctCount).isEqualTo(3)
        assertThat(result.incorrectCount).isEqualTo(4)
        assertThat(result.fsrsCardJson).isEqualTo("{\"card\":1}")
        assertThat(result.fsrsDueAt).isEqualTo(333L)
    }
}
