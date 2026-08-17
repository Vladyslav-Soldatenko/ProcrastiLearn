package com.procrastilearn.app.data.export

import com.google.common.truth.Truth.assertWithMessage
import com.procrastilearn.app.domain.model.VocabularyExportItem
import org.junit.Test
import java.io.File

class ExportCorpusSnapshotTest {
    private val corpusRoot = File("src/test/resources/exports")
    private val snapshotsRoot = File("src/test/resources/snapshots")

    @Test
    fun `every historical fixture decodes to its approved snapshot`() {
        val failures = corpusFiles().mapNotNull { checkSnapshot(it) }
        assertWithMessage(failures.joinToString("\n\n")).that(failures).isEmpty()
    }

    private fun corpusFiles(): List<File> =
        corpusRoot
            .walkTopDown()
            .filter { it.isFile && it.extension == "json" }
            .filterNot { it.toRelativeString(corpusRoot).startsWith("malformed") }
            .sortedBy { it.path }
            .toList()

    private fun checkSnapshot(fixture: File): String? {
        val rendered = renderFixture(fixture)
        val relative = fixture.toRelativeString(corpusRoot).removeSuffix(".json")
        val approvedFile = File(snapshotsRoot, "$relative.approved.txt")
        val actualFile = File(snapshotsRoot, "$relative.actual.txt")

        if (approvedFile.exists() && approvedFile.readText() == rendered) {
            return null
        }

        actualFile.parentFile?.mkdirs()
        actualFile.writeText(rendered)
        return if (approvedFile.exists()) {
            "Snapshot mismatch for $relative.\n  approved: $approvedFile\n  actual:   $actualFile"
        } else {
            "No approved snapshot for $relative. Review $actualFile, then " +
                "`mv $actualFile $approvedFile` if it looks right."
        }
    }

    private fun renderFixture(fixture: File): String =
        when (val outcome = VocabularyExportSerializer.decode(fixture.readText())) {
            is ImportOutcome.Success -> outcome.items.stableRender()
            is ImportOutcome.SchemaTooNew -> "SCHEMA_TOO_NEW(${outcome.schemaVersion})"
        }
}

internal fun List<VocabularyExportItem>.stableRender(): String = joinToString("\n\n") { it.stableRender() }

internal fun VocabularyExportItem.stableRender(): String =
    listOf(
        "backwardAnswerOverride: ${backwardAnswerOverride ?: "null"}",
        "backwardCorrectCount: $backwardCorrectCount",
        "backwardFsrsCardJson: $backwardFsrsCardJson",
        "backwardFsrsDueAt: $backwardFsrsDueAt",
        "backwardIncorrectCount: $backwardIncorrectCount",
        "backwardPromptOverride: ${backwardPromptOverride ?: "null"}",
        "bidirectional: $bidirectional",
        "correctCount: $correctCount",
        "createdAt: $createdAt",
        "fsrsCardJson: $fsrsCardJson",
        "fsrsDueAt: $fsrsDueAt",
        "id: $id",
        "incorrectCount: $incorrectCount",
        "lastShownAt: ${lastShownAt ?: "null"}",
        "position: $position",
        "translation: $translation",
        "word: $word",
    ).joinToString("\n")
