package com.procrastilearn.app

import android.app.Application
import com.procrastilearn.app.data.sync.PendingWordSyncManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MyApp : Application() {
    @Inject
    @Suppress("LateinitUsage") // Hilt field injection into Application has no constructor path
    lateinit var pendingWordSyncManager: PendingWordSyncManager

    override fun onCreate() {
        super.onCreate()
        pendingWordSyncManager.start()
    }
}
