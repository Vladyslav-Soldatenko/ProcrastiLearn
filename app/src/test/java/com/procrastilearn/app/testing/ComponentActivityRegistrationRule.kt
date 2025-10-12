package com.procrastilearn.app.testing

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.robolectric.Shadows

/**
 * Ensures ComponentActivity is registered in Robolectric so ActivityScenario-based Compose rules
 * can launch without a manifest stub. Needed for release unit tests where ui-test-manifest is absent.
 */
class ComponentActivityRegistrationRule : TestRule {
    override fun apply(
        base: Statement,
        description: Description?,
    ): Statement =
        object : Statement() {
            override fun evaluate() {
                val app: Application = ApplicationProvider.getApplicationContext()
                val componentName =
                    ComponentName(app.packageName, ComponentActivity::class.java.name)
                val intentFilter =
                    IntentFilter(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                    }

                val shadowPackageManager = Shadows.shadowOf(app.packageManager)
                val activityInfo = shadowPackageManager.addActivityIfNotPresent(componentName)
                activityInfo.packageName = componentName.packageName
                activityInfo.name = componentName.className
                activityInfo.applicationInfo = app.applicationInfo
                activityInfo.exported = true
                activityInfo.enabled = true
                shadowPackageManager.addIntentFilterForActivity(componentName, intentFilter)

                base.evaluate()
            }
        }
}
