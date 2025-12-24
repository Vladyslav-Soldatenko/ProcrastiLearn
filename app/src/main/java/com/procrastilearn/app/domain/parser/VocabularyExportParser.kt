package com.procrastilearn.app.domain.parser

import com.procrastilearn.app.domain.model.VocabularyExportItem
import java.io.File

/**
 * Parser that can return fully populated vocabulary rows (all DB fields).
 */
interface VocabularyExportParser {
    /**
     * Parse the provided file into full vocabulary export items.
     *
     * @throws IllegalArgumentException when the supplied file is invalid or cannot be processed.
     */
    fun parseExport(file: File): List<VocabularyExportItem>
}
