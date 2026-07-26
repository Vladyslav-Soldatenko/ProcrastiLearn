package com.procrastilearn.app.data.export

import com.procrastilearn.app.domain.model.VocabularyExportItem

sealed interface ImportOutcome {
    data class Success(
        val items: List<VocabularyExportItem>,
    ) : ImportOutcome

    data class SchemaTooNew(
        val schemaVersion: Int,
    ) : ImportOutcome
}
