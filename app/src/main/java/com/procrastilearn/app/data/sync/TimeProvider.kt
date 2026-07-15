package com.procrastilearn.app.data.sync

fun interface TimeProvider {
    fun now(): Long
}
