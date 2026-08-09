package com.procrastilearn.app.domain.parser

import androidx.annotation.StringRes

data class VocabularyImportOption(
    val id: String,
    @param:StringRes val titleResId: Int,
    @param:StringRes val descriptionResId: Int? = null,
    val mimeTypes: List<String>,
    val extensions: Set<String>,
)
