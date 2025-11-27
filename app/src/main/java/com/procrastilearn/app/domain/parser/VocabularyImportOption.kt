package com.procrastilearn.app.domain.parser

import androidx.annotation.StringRes

data class VocabularyImportOption(
    val id: String,
    @StringRes val titleResId: Int,
    @StringRes val descriptionResId: Int? = null,
    val mimeTypes: List<String>,
    val extensions: Set<String>,
)
