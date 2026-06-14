package com.procrastilearn.app

import android.app.Application
import androidx.appfunctions.service.AppFunctionConfiguration
import com.procrastilearn.app.appfunctions.VocabularyFunctions
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MyApp : Application(), AppFunctionConfiguration.Provider {
    @Inject
    lateinit var vocabularyFunctions: VocabularyFunctions

    override val appFunctionConfiguration: AppFunctionConfiguration
        get() =
            AppFunctionConfiguration.Builder()
                .addEnclosingClassFactory(VocabularyFunctions::class.java) { vocabularyFunctions }
                .build()
}
