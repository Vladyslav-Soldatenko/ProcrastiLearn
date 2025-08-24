package com.example.myapplication.data

import android.content.Context
import android.content.Intent
import com.example.myapplication.domain.model.AppInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRepositoryImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : AppRepository {
        override suspend fun loadLaunchableApps(): List<AppInfo> =
            withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val intent =
                    Intent(Intent.ACTION_MAIN, null).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                    }

                val activities = pm.queryIntentActivities(intent, 0)

                activities
                    .asSequence()
                    .mapNotNull { resolveInfo ->
                        try {
                            AppInfo(
                                label = resolveInfo.loadLabel(pm).toString(),
                                packageName = resolveInfo.activityInfo.packageName,
//                        activityName = resolveInfo.activityInfo.name,
                                icon = resolveInfo.loadIcon(pm),
                            )
                        } catch (e: Exception) {
                            null // Skip apps that fail to load
                        }
                    }.distinctBy { "${it.packageName}" }
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
                    .toList()
            }
    }
