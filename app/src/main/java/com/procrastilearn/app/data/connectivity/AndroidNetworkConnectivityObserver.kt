package com.procrastilearn.app.data.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidNetworkConnectivityObserver
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : NetworkConnectivityObserver {
        private val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        override fun isOnline(): Boolean {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }

        // Shared/hot: a single NetworkCallback registration is multicast to every
        // collector (ViewModels, PendingWordSyncManager, ...) instead of each
        // collector registering its own callback with the OS.
        private val sharedNetworkState: Flow<Boolean> =
            callbackFlow {
                val request =
                    NetworkRequest
                        .Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build()

                val callback =
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            trySend(isOnline())
                        }

                        override fun onLost(network: Network) {
                            trySend(isOnline())
                        }

                        override fun onCapabilitiesChanged(
                            network: Network,
                            networkCapabilities: NetworkCapabilities,
                        ) {
                            trySend(isOnline())
                        }
                    }

                trySend(isOnline())
                connectivityManager.registerNetworkCallback(request, callback)

                awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
            }.distinctUntilChanged()
                .shareIn(
                    scope = scope,
                    started = SharingStarted.Eagerly,
                    replay = 1,
                )

        override fun observe(): Flow<Boolean> = sharedNetworkState
    }
