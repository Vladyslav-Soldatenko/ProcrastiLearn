package com.procrastilearn.app.domain.model

data class GateSession(
    val packageName: String,
    val unlockedAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
)
