package com.procrastilearn.app.data.translation

sealed interface ErrorClassification {
    val message: String

    data class Permanent(override val message: String) : ErrorClassification

    data class Transient(override val message: String) : ErrorClassification
}
