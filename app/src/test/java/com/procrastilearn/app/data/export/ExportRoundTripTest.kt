package com.procrastilearn.app.data.export

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.procrastilearn.app.domain.model.VocabularyExportItem
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.junit.Test
import java.io.File
import kotlin.random.Random

private const val FUZZ_SEED = 20260726L
private const val MAX_TIMESTAMP = 2_000_000_000_000L
private const val MAX_REVIEW_COUNT = 50
private const val MAX_WORD_SUFFIX = 100_000
private const val RANDOM_SUBSET_TRIALS = 5

class ExportRoundTripFuzzTest {

    @Test
    fun `upgrading a v1 export and re-exporting round trips through the current format`() {
        val firstDecode = decodeFixtureOrFail("src/test/resources/exports/v1/real-device-export.json")

        val reExported = VocabularyExportSerializer.encode(firstDecode.items)
        val secondDecode = VocabularyExportSerializer.decode(reExported)

        assertThat(secondDecode).isEqualTo(firstDecode)
        // Legacy (pre-bidirectional) data must not spuriously acquire bidirectional state.
        assertBackwardFieldsAreDefaulted(firstDecode.items)
    }

    @Test
    fun `upgrading a v2 export and re-exporting round trips through the current format`() {
        val firstDecode = decodeFixtureOrFail("src/test/resources/exports/v2/pre-bidirectional-export.json")

        val reExported = VocabularyExportSerializer.encode(firstDecode.items)
        val secondDecode = VocabularyExportSerializer.decode(reExported)

        assertThat(secondDecode).isEqualTo(firstDecode)
        assertBackwardFieldsAreDefaulted(firstDecode.items)
    }

    private fun decodeFixtureOrFail(path: String): ImportOutcome.Success {
        val raw = File(path).readText()
        val decoded = VocabularyExportSerializer.decode(raw)
        check(decoded is ImportOutcome.Success) { "Fixture no longer decodes: $decoded" }
        return decoded
    }

    private fun assertBackwardFieldsAreDefaulted(items: List<VocabularyExportItem>) {
        assertThat(items).isNotEmpty()
        items.forEach { item ->
            assertWithMessage("${item.word} should default to non-bidirectional")
                .that(item.bidirectional)
                .isFalse()
            assertThat(item.backwardFsrsCardJson).isEmpty()
            assertThat(item.backwardFsrsDueAt).isEqualTo(0L)
            assertThat(item.backwardCorrectCount).isEqualTo(0)
            assertThat(item.backwardIncorrectCount).isEqualTo(0)
            assertThat(item.backwardPromptOverride).isNull()
            assertThat(item.backwardAnswerOverride).isNull()
        }
    }

    @Test
    fun `dropping any droppable field from a typical export still decodes`() {
        val typicalItem = randomItem(Random(FUZZ_SEED), id = 1)
        val fullJson = typicalItemJson(typicalItem)
        val droppableKeys = droppableItemFields()
        val random = Random(FUZZ_SEED)

        val subsets =
            droppableKeys.map { setOf(it) } + randomSubsets(droppableKeys, random)

        for (keysToDrop in subsets) {
            val degraded =
                buildJsonObject {
                    fullJson.forEach { (key, value) -> if (key !in keysToDrop) put(key, value) }
                }
            val raw = buildJsonArray { add(degraded) }.toString()

            assertWithMessage("dropping $keysToDrop should not break decoding")
                .that(VocabularyExportSerializer.decode(raw))
                .isInstanceOf(ImportOutcome.Success::class.java)
        }
    }
}

private fun randomSubsets(
    droppableKeys: List<String>,
    random: Random,
): List<Set<String>> {
    if (droppableKeys.isEmpty()) return emptyList()
    return (1..RANDOM_SUBSET_TRIALS).map {
        droppableKeys.shuffled(random).take(random.nextInt(1, droppableKeys.size + 1)).toSet()
    }
}

private fun randomItem(
    random: Random,
    id: Long,
): VocabularyExportItem =
    VocabularyExportItem(
        id = id,
        word = "word-${random.nextInt(MAX_WORD_SUFFIX)}",
        translation = "translation-${random.nextInt(MAX_WORD_SUFFIX)}",
        createdAt = random.nextLong(MAX_TIMESTAMP),
        lastShownAt = if (random.nextBoolean()) random.nextLong(MAX_TIMESTAMP) else null,
        correctCount = random.nextInt(MAX_REVIEW_COUNT),
        incorrectCount = random.nextInt(MAX_REVIEW_COUNT),
        fsrsCardJson = """{"cardId":${random.nextInt()}}""",
        fsrsDueAt = random.nextLong(MAX_TIMESTAMP),
        bidirectional = random.nextBoolean(),
        backwardFsrsCardJson = """{"backwardCardId":${random.nextInt()}}""",
        backwardFsrsDueAt = random.nextLong(MAX_TIMESTAMP),
        backwardCorrectCount = random.nextInt(MAX_REVIEW_COUNT),
        backwardIncorrectCount = random.nextInt(MAX_REVIEW_COUNT),
        backwardPromptOverride = if (random.nextBoolean()) "prompt-${random.nextInt(MAX_WORD_SUFFIX)}" else null,
        backwardAnswerOverride = if (random.nextBoolean()) "answer-${random.nextInt(MAX_WORD_SUFFIX)}" else null,
    )

private fun typicalItemJson(item: VocabularyExportItem): JsonObject =
    buildJsonObject {
        put("id", JsonPrimitive(item.id))
        put("word", JsonPrimitive(item.word))
        put("translation", JsonPrimitive(item.translation))
        put("createdAt", JsonPrimitive(item.createdAt))
        put("lastShownAt", item.lastShownAt?.let { JsonPrimitive(it) } ?: JsonNull)
        put("correctCount", JsonPrimitive(item.correctCount))
        put("incorrectCount", JsonPrimitive(item.incorrectCount))
        put("fsrsCardJson", JsonPrimitive(item.fsrsCardJson))
        put("fsrsDueAt", JsonPrimitive(item.fsrsDueAt))
        put("bidirectional", JsonPrimitive(item.bidirectional))
        put("backwardFsrsCardJson", JsonPrimitive(item.backwardFsrsCardJson))
        put("backwardFsrsDueAt", JsonPrimitive(item.backwardFsrsDueAt))
        put("backwardCorrectCount", JsonPrimitive(item.backwardCorrectCount))
        put("backwardIncorrectCount", JsonPrimitive(item.backwardIncorrectCount))
        put("backwardPromptOverride", item.backwardPromptOverride?.let { JsonPrimitive(it) } ?: JsonNull)
        put("backwardAnswerOverride", item.backwardAnswerOverride?.let { JsonPrimitive(it) } ?: JsonNull)
    }

@OptIn(ExperimentalSerializationApi::class)
private fun droppableItemFields(): List<String> {
    val descriptor = VocabularyExportItem.serializer().descriptor
    return (0 until descriptor.elementsCount)
        .filter { descriptor.isElementOptional(it) || descriptor.getElementDescriptor(it).isNullable }
        .map { descriptor.getElementName(it) }
}
