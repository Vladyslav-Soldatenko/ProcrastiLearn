package com.example.myapplication.data.counter

data class DayCounters(
    val yyyymmdd: Int, // e.g., 20250824
    val newShown: Int,
    val reviewShown: Int,
    val reviewsSinceLastNew: Int,
)
