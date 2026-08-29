package com.procrastilearn.app.domain.model

data class SearchScope(
    val matchWord: Boolean = true,
    val matchTranslation: Boolean = true,
) {
    val isValid: Boolean get() = matchWord || matchTranslation
}
