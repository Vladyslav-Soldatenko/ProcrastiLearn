package com.procrastilearn.app.data.connectivity

import kotlinx.coroutines.flow.Flow

interface NetworkConnectivityObserver {
    fun isOnline(): Boolean

    fun observe(): Flow<Boolean>
}
