package com.example.myapplication.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.example.myapplication.domain.model.AppInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : AppRepository {

    override suspend fun loadLaunchableApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm: PackageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val flags = 0 // keep simple; add MATCH_DIRECT_BOOT_AWARE etc. if you need
        val activities = pm.queryIntentActivities(intent, flags)

        activities
            .asSequence()
            .map { ri ->
                val label = ri.loadLabel(pm)?.toString().orEmpty()
                val pkg = ri.activityInfo.packageName
                val act = ri.activityInfo.name
                AppInfo(label = label, packageName = pkg, activityName = act)
            }
            .distinctBy { it.packageName to it.activityName }     // avoid dupes (e.g., work profile)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .toList()
    }
}
