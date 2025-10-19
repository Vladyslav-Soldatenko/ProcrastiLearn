package com.procrastilearn.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import com.google.common.truth.Truth.assertThat
import com.procrastilearn.app.testing.FakeResolveInfo
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AppRepositoryImplTest {
    private lateinit var context: Context
    private lateinit var packageManager: PackageManager
    private lateinit var resources: Resources
    private lateinit var configuration: Configuration
    private lateinit var repository: AppRepositoryImpl

    private val mainIntent =
        Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

    @Before
    fun setUp() {
        packageManager = mockk(relaxed = true)
        resources = mockk(relaxed = true)
        configuration =
            Configuration().apply {
                setLocales(LocaleList(Locale.US))
            }
        every { resources.configuration } returns configuration

        context = mockk(relaxed = true)
        every { context.packageManager } returns packageManager
        every { context.resources } returns resources

        repository = AppRepositoryImpl(context)
    }

    @Test
    fun `loadLaunchableApps sorts results and removes duplicate packages`() =
        runTest {
            val zebra = FakeResolveInfo.create("com.example.zebra", "ZebraActivity", label = "Zebra")
            val alpha = FakeResolveInfo.create("com.example.alpha", "AlphaActivity", label = "Alpha")
            val duplicateAlpha = FakeResolveInfo.create("com.example.alpha", "AlphaOther", label = "Alpha Clone")

            arrangeQueryIntentActivitiesNewApi(listOf(zebra, alpha, duplicateAlpha))

            val apps = repository.loadLaunchableApps()

            assertThat(apps.map { it.packageName }).containsExactly("com.example.alpha", "com.example.zebra").inOrder()
            assertThat(apps.map { it.label }).containsExactly("Alpha", "Zebra").inOrder()
        }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun `loadLaunchableApps uses legacy query path when running on older SDK`() =
        runTest {
            val entry = FakeResolveInfo.create("com.example.legacy", "LegacyActivity", label = "Legacy")

            every {
                packageManager.queryIntentActivities(
                    match { intent ->
                        intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER)
                    },
                    any<Int>(),
                )
            } returns listOf(entry)

            val apps = repository.loadLaunchableApps()

            assertThat(apps).hasSize(1)
            assertThat(apps.first().packageName).isEqualTo("com.example.legacy")
        }

    @Test
    fun `loadLaunchableApps drops entries without activity info`() =
        runTest {
            val valid = FakeResolveInfo.create("com.example.valid", "ValidActivity", label = "Valid")
            val invalid = ResolveInfo() // missing activityInfo

            arrangeQueryIntentActivitiesNewApi(listOf(invalid, valid))

            val apps = repository.loadLaunchableApps()

            assertThat(apps).hasSize(1)
            assertThat(apps.first().packageName).isEqualTo("com.example.valid")
        }

    @Test
    fun `loadLaunchableApps skips entries that throw while loading`() =
        runTest {
            val crashing =
                object : ResolveInfo() {
                    init {
                        activityInfo = FakeResolveInfo.create("com.example.crash", "CrashActivity").activityInfo
                    }

                    override fun loadLabel(pm: PackageManager): CharSequence = throw IllegalStateException("boom")
                }
            val safe = FakeResolveInfo.create("com.example.safe", "SafeActivity", label = "Safe")

            arrangeQueryIntentActivitiesNewApi(listOf(crashing, safe))

            val apps = repository.loadLaunchableApps()

            assertThat(apps.map { it.packageName }).containsExactly("com.example.safe")
        }

    private fun arrangeQueryIntentActivitiesNewApi(resolveInfos: List<ResolveInfo>) {
        every {
            packageManager.queryIntentActivities(
                match { intent ->
                    intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER)
                },
                any<PackageManager.ResolveInfoFlags>(),
            )
        } returns resolveInfos
    }
}
