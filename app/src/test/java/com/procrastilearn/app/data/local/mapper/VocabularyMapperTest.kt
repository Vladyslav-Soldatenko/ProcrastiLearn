package com.procrastilearn.app.data.local.mapper

import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.data.local.entity.VocabularyEntity
import com.procrastilearn.app.domain.model.StudyDirection
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
        // "new" is derived from due-timestamps, not raw counts, so a reviewed row must
        // have a nonzero fsrsDueAt to be considered not-new.
        val entity =
            VocabularyEntity(
                id = 22,
                word = "lesen",
                translation = "read",
                correctCount = 1,
                incorrectCount = 3,
                fsrsDueAt = 5_000L,
            )

        val result = entity.toDomain()

        assertThat(result.isNew).isFalse()
    }

    @Test
    fun `toDomain with FORWARD direction returns word and translation unchanged`() {
        val entity = VocabularyEntity(id = 1, word = "run", translation = "бігати")

        val result = entity.toDomain(StudyDirection.FORWARD)

        assertThat(result.word).isEqualTo("run")
        assertThat(result.translation).isEqualTo("бігати")
        assertThat(result.direction).isEqualTo(StudyDirection.FORWARD)
    }

    @Test
    fun `toDomain with BACKWARD direction and no overrides swaps word and translation`() {
        val entity = VocabularyEntity(id = 1, word = "run", translation = "бігати")

        val result = entity.toDomain(StudyDirection.BACKWARD)

        assertThat(result.word).isEqualTo("бігати")
        assertThat(result.translation).isEqualTo("run")
        assertThat(result.direction).isEqualTo(StudyDirection.BACKWARD)
    }

    @Test
    fun `toDomain with BACKWARD direction uses backwardPromptOverride instead of translation`() {
        val entity =
            VocabularyEntity(
                id = 1,
                word = "run",
                translation = "бігати",
                backwardPromptOverride = "custom prompt",
            )

        val result = entity.toDomain(StudyDirection.BACKWARD)

        assertThat(result.word).isEqualTo("custom prompt")
        assertThat(result.translation).isEqualTo("run")
    }

    @Test
    fun `toDomain with BACKWARD direction uses backwardAnswerOverride instead of word`() {
        val entity =
            VocabularyEntity(
                id = 1,
                word = "run",
                translation = "бігати",
                backwardAnswerOverride = "custom answer",
            )

        val result = entity.toDomain(StudyDirection.BACKWARD)

        assertThat(result.word).isEqualTo("бігати")
        assertThat(result.translation).isEqualTo("custom answer")
    }

    @Test
    fun `toDomain with BACKWARD direction and both overrides ignores word and translation entirely`() {
        val entity =
            VocabularyEntity(
                id = 1,
                word = "run",
                translation = "бігати",
                backwardPromptOverride = "custom prompt",
                backwardAnswerOverride = "custom answer",
            )

        val result = entity.toDomain(StudyDirection.BACKWARD)

        assertThat(result.word).isEqualTo("custom prompt")
        assertThat(result.translation).isEqualTo("custom answer")
    }

    @Test
    fun `toDomain isNew is false when fsrsDueAt is nonzero even if backwardFsrsDueAt is zero`() {
        val entity = VocabularyEntity(id = 1, word = "a", translation = "b", fsrsDueAt = 100L)

        assertThat(entity.toDomain().isNew).isFalse()
    }

    @Test
    fun `toDomain isNew is false when backwardFsrsDueAt is nonzero even if fsrsDueAt is zero`() {
        val entity = VocabularyEntity(id = 1, word = "a", translation = "b", backwardFsrsDueAt = 100L)

        assertThat(entity.toDomain().isNew).isFalse()
    }

    @Test
    fun `toDomain isNew is true only when both fsrsDueAt and backwardFsrsDueAt are zero`() {
        val entity = VocabularyEntity(id = 1, word = "a", translation = "b")

        assertThat(entity.toDomain().isNew).isTrue()
    }

    @Test
    fun `toDomain carries bidirectional flag through regardless of direction requested`() {
        val entity = VocabularyEntity(id = 1, word = "a", translation = "b", bidirectional = true)

        assertThat(entity.toDomain(StudyDirection.FORWARD).bidirectional).isTrue()
        assertThat(entity.toDomain(StudyDirection.BACKWARD).bidirectional).isTrue()
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
    fun `toEntity carries bidirectional and override fields into the entity`() {
        val item =
            VocabularyItem(
                id = 9,
                word = "gehen",
                translation = "go",
                isNew = false,
                bidirectional = true,
                backwardPromptOverride = "prompt",
                backwardAnswerOverride = "answer",
            )

        val result = item.toEntity()

        assertThat(result.bidirectional).isTrue()
        assertThat(result.backwardPromptOverride).isEqualTo("prompt")
        assertThat(result.backwardAnswerOverride).isEqualTo("answer")
    }

    @Test
    fun `toEntity defaults position to zero when not provided`() {
        val item = VocabularyItem(id = 9, word = "gehen", translation = "go", isNew = false)

        assertThat(item.toEntity().position).isEqualTo(0L)
    }

    @Test
    fun `toEntity passes through a provided position`() {
        val item = VocabularyItem(id = 9, word = "gehen", translation = "go", isNew = false)

        val result = item.toEntity(position = 42L)

        assertThat(result.position).isEqualTo(42L)
    }

    @Test
    fun `toEntity leaves backward FSRS progress columns at defaults for a new item`() {
        val item = VocabularyItem(id = 9, word = "gehen", translation = "go", isNew = false, bidirectional = true)

        val result = item.toEntity()

        assertThat(result.backwardFsrsCardJson).isEmpty()
        assertThat(result.backwardFsrsDueAt).isEqualTo(0L)
        assertThat(result.backwardCorrectCount).isEqualTo(0)
        assertThat(result.backwardIncorrectCount).isEqualTo(0)
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
                position = 88L,
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
        assertThat(result.position).isEqualTo(88L)
    }

    @Test
    fun `VocabularyExportItem toEntity defaults position to zero when the export predates the field`() {
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

        assertThat(item.toEntity().position).isEqualTo(0L)
    }

    @Test
    fun `VocabularyExportItem toEntity maps all seven new bidirectional fields`() {
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
                bidirectional = true,
                backwardFsrsCardJson = "{\"card\":2}",
                backwardFsrsDueAt = 444L,
                backwardCorrectCount = 5,
                backwardIncorrectCount = 6,
                backwardPromptOverride = "prompt",
                backwardAnswerOverride = "answer",
            )

        val result = item.toEntity()

        assertThat(result.bidirectional).isTrue()
        assertThat(result.backwardFsrsCardJson).isEqualTo("{\"card\":2}")
        assertThat(result.backwardFsrsDueAt).isEqualTo(444L)
        assertThat(result.backwardCorrectCount).isEqualTo(5)
        assertThat(result.backwardIncorrectCount).isEqualTo(6)
        assertThat(result.backwardPromptOverride).isEqualTo("prompt")
        assertThat(result.backwardAnswerOverride).isEqualTo("answer")
    }

    @Test
    fun `VocabularyEntity toExportItem maps every field including bidirectional ones`() {
        val entity =
            VocabularyEntity(
                id = 7,
                word = "gehen",
                translation = "go",
                createdAt = 111L,
                lastShownAt = 222L,
                correctCount = 3,
                incorrectCount = 4,
                fsrsCardJson = "{\"card\":1}",
                fsrsDueAt = 333L,
                bidirectional = true,
                backwardFsrsCardJson = "{\"card\":2}",
                backwardFsrsDueAt = 444L,
                backwardCorrectCount = 5,
                backwardIncorrectCount = 6,
                backwardPromptOverride = "prompt",
                backwardAnswerOverride = "answer",
                position = 99L,
            )

        val result = entity.toExportItem()

        assertThat(result.id).isEqualTo(7)
        assertThat(result.word).isEqualTo("gehen")
        assertThat(result.translation).isEqualTo("go")
        assertThat(result.createdAt).isEqualTo(111L)
        assertThat(result.lastShownAt).isEqualTo(222L)
        assertThat(result.correctCount).isEqualTo(3)
        assertThat(result.incorrectCount).isEqualTo(4)
        assertThat(result.fsrsCardJson).isEqualTo("{\"card\":1}")
        assertThat(result.fsrsDueAt).isEqualTo(333L)
        assertThat(result.bidirectional).isTrue()
        assertThat(result.backwardFsrsCardJson).isEqualTo("{\"card\":2}")
        assertThat(result.backwardFsrsDueAt).isEqualTo(444L)
        assertThat(result.backwardCorrectCount).isEqualTo(5)
        assertThat(result.backwardIncorrectCount).isEqualTo(6)
        assertThat(result.backwardPromptOverride).isEqualTo("prompt")
        assertThat(result.backwardAnswerOverride).isEqualTo("answer")
        assertThat(result.position).isEqualTo(99L)
    }
}
