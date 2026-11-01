package com.procrastilearn.app

import android.app.Application
import com.procrastilearn.app.data.sync.PendingWordSyncManager
import com.procrastilearn.app.service.OwnActivityForegroundStore
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MyApp : Application() {
    @Inject
    @Suppress("LateinitUsage") // Hilt field injection into Application has no constructor path
    lateinit var pendingWordSyncManager: PendingWordSyncManager

    @Inject
    @Suppress("LateinitUsage")
    lateinit var ownActivityForegroundStore: OwnActivityForegroundStore

    override fun onCreate() {
        super.onCreate()
        ownActivityForegroundStore.register(this)
        pendingWordSyncManager.start()
    }
}
