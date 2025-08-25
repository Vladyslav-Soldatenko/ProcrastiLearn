package com.procrastilearn.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import com.procrastilearn.app.domain.model.AppInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppRepository {
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    override suspend fun loadLaunchableApps(): List<AppInfo> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val mainIntent =
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }

            val resolveInfos: List<ResolveInfo> =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.queryIntentActivities(
                        mainIntent,
                        PackageManager.ResolveInfoFlags.of(0),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    pm.queryIntentActivities(mainIntent, 0)
                }

            val collator =
                java.text.Collator.getInstance(
                    context.resources.configuration.locales[0],
                )

            resolveInfos
                .asSequence()
                .mapNotNull { ri ->
                    val ai = ri.activityInfo ?: return@mapNotNull null
                    try {
                        val label = ri.loadLabel(pm)?.toString() ?: ai.packageName
                        AppInfo(
                            label = label,
                            packageName = ai.packageName,
                            icon = ri.loadIcon(pm),
                        )
                    } catch (_: Exception) {
                        null // gracefully skip anything that fails to load
                    }
                }.distinctBy { it.packageName }
                .sortedWith { a, b -> collator.compare(a.label, b.label) }
                .toList()
        }
}
