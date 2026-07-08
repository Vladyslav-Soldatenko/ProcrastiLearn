package com.procrastilearn.app

import android.app.Application
import androidx.appfunctions.service.AppFunctionConfiguration
import com.procrastilearn.app.appfunctions.VocabularyFunctions
import com.procrastilearn.app.data.sync.PendingWordSyncManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MyApp : Application(), AppFunctionConfiguration.Provider {
    @Inject
    lateinit var vocabularyFunctions: VocabularyFunctions

    @Inject
    lateinit var pendingWordSyncManager: PendingWordSyncManager

    override fun onCreate() {
        super.onCreate()
        pendingWordSyncManager.start()
    }

    override val appFunctionConfiguration: AppFunctionConfiguration
        get() =
            AppFunctionConfiguration.Builder()
                .addEnclosingClassFactory(VocabularyFunctions::class.java) { vocabularyFunctions }
                .build()
}
