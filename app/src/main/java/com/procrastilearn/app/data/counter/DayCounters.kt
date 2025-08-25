package com.procrastilearn.app.data.counter

data class DayCounters(
    val yyyymmdd: Int, // e.g., 20250824
    val newShown: Int,
    val reviewShown: Int,
    val reviewsSinceLastNew: Int,
)
