package com.procrastilearn.app.data.export

import com.procrastilearn.app.domain.model.VocabularyExportItem
import kotlinx.serialization.Serializable

const val CURRENT_SCHEMA_VERSION = 2

@Serializable
data class VocabularyExportEnvelope(
    val schemaVersion: Int,
    val exportedAt: Long,
    val appVersion: String,
    val words: List<VocabularyExportItem>,
)
