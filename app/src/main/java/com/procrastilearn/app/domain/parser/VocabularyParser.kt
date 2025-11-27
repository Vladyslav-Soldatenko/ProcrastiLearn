package com.procrastilearn.app.domain.parser

import androidx.annotation.StringRes
import com.procrastilearn.app.domain.model.VocabularyItem
import java.io.File

/**
 * Abstraction for parsing exported vocabulary sources into domain items.
 */
interface VocabularyParser {
    /**
     * Unique identifier for this parser (example: `apkg`).
     */
    val id: String

    /**
     * Human-readable title for UI display.
     */
    @get:StringRes
    val titleResId: Int

    /**
     * Optional description shown alongside the title.
     */
    @get:StringRes
    val descriptionResId: Int?
        get() = null

    /**
     * File extensions supported by this parser (for example `apkg`).
     */
    val supportedExtensions: Set<String>

    /**
     * MIME types to present when prompting the user to pick a file for this parser.
     */
    val mimeTypes: List<String>

    /**
     * Parse the provided file into a list of vocabulary items ready for further processing.
     *
     * @throws IllegalArgumentException when the supplied file is invalid or cannot be processed.
     */
    fun parse(file: File): List<VocabularyItem>
}
