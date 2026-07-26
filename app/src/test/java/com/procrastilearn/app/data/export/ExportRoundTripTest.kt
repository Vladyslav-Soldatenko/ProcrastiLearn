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
        val raw = File("src/test/resources/exports/v1/real-device-export.json").readText()
        val firstDecode = VocabularyExportSerializer.decode(raw)
        check(firstDecode is ImportOutcome.Success) { "Fixture no longer decodes: $firstDecode" }

        val reExported = VocabularyExportSerializer.encode(firstDecode.items)
        val secondDecode = VocabularyExportSerializer.decode(reExported)

        assertThat(secondDecode).isEqualTo(firstDecode)
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
    }

@OptIn(ExperimentalSerializationApi::class)
private fun droppableItemFields(): List<String> {
    val descriptor = VocabularyExportItem.serializer().descriptor
    return (0 until descriptor.elementsCount)
        .filter { descriptor.isElementOptional(it) || descriptor.getElementDescriptor(it).isNullable }
        .map { descriptor.getElementName(it) }
}
