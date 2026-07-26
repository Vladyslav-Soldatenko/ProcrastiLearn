package com.procrastilearn.app.data.export

import com.google.common.truth.Truth.assertWithMessage
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

private val SCHEMA_CHANGE_CHECKLIST =
    """
    Export schema changed. Before approving:
    1. Bump CURRENT_SCHEMA_VERSION and add a SchemaMigration for the previous version
    2. Capture fixtures: add a new exports/vN/ sample
    3. Review the snapshot diffs — are the defaults semantically right, or do they erase user progress?
    4. Re-approve snapshots and commit the new fingerprint
    """.trimIndent()

class SchemaFingerprintTest {
    private val schemaRoot = File("src/test/resources/schema")
    private val currentFingerprintFile = File(schemaRoot, "v$CURRENT_SCHEMA_VERSION.fingerprint.txt")
    private val previousFingerprintFile = File(schemaRoot, "v${CURRENT_SCHEMA_VERSION - 1}.fingerprint.txt")

    @Test
    fun `export schema fingerprint matches the committed baseline`() {
        val rendered = currentFingerprint().joinToString("\n") { it.toString() }
        if (currentFingerprintFile.exists() && currentFingerprintFile.readText().trim() == rendered) {
            return
        }

        val actualFile = File(schemaRoot, "v$CURRENT_SCHEMA_VERSION.fingerprint.actual.txt")
        actualFile.parentFile?.mkdirs()
        actualFile.writeText(rendered)
        fail(
            "$SCHEMA_CHANGE_CHECKLIST\n\nWrote $actualFile — review it, then " +
                "`mv $actualFile $currentFingerprintFile` if the new shape is correct.",
        )
    }

    @Test
    fun `every field added since the previous schema version has a default`() {
        if (!previousFingerprintFile.exists()) return

        val previousNames = previousFingerprintFile.readLines().map { it.substringBefore(" : ") }.toSet()
        val newRequiredFields = currentFingerprint().filter { it.name !in previousNames && !it.optional }

        assertWithMessage("New required field(s) would break every existing export: $newRequiredFields")
            .that(newRequiredFields)
            .isEmpty()
    }

    private fun currentFingerprint(): List<FingerprintEntry> =
        VocabularyExportEnvelope.serializer().descriptor.fingerprint()
}

private data class FingerprintEntry(
    val name: String,
    val type: String,
    val optional: Boolean,
) {
    override fun toString(): String = "$name : $type : ${if (optional) "optional" else "REQUIRED"}"
}

@OptIn(ExperimentalSerializationApi::class)
private fun SerialDescriptor.fingerprint(prefix: String = ""): List<FingerprintEntry> =
    buildList {
        for (i in 0 until elementsCount) {
            val name = prefix + getElementName(i)
            val elementDescriptor = getElementDescriptor(i)
            val alreadyMarked = elementDescriptor.serialName.endsWith("?")
            val nullability = if (elementDescriptor.isNullable && !alreadyMarked) "?" else ""
            add(FingerprintEntry(name, "${elementDescriptor.serialName}$nullability", isElementOptional(i)))
            when (elementDescriptor.kind) {
                StructureKind.CLASS -> addAll(elementDescriptor.fingerprint("$name."))
                StructureKind.LIST -> addAll(elementDescriptor.getElementDescriptor(0).fingerprint("$name[]."))
                else -> Unit
            }
        }
    }
