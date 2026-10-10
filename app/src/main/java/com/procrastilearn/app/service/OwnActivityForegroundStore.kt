package com.procrastilearn.app.service

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections
import java.util.IdentityHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OwnActivityForegroundStore @Inject constructor() : Application.ActivityLifecycleCallbacks {
    private val resumedActivities = Collections.newSetFromMap(IdentityHashMap<Activity, Boolean>())
    private val mutableIsResumed = MutableStateFlow(false)
    val isResumed: StateFlow<Boolean> = mutableIsResumed.asStateFlow()
    private var registered = false

    fun register(application: Application) {
        if (registered) return
        registered = true
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityResumed(activity: Activity) {
        resumedActivities.add(activity)
        mutableIsResumed.value = true
    }

    override fun onActivityPaused(activity: Activity) {
        resumedActivities.remove(activity)
        mutableIsResumed.value = resumedActivities.isNotEmpty()
    }

    override fun onActivityDestroyed(activity: Activity) {
        onActivityPaused(activity)
    }

    @Suppress("EmptyFunctionBlock")
    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) {}

    @Suppress("EmptyFunctionBlock")
    override fun onActivityStarted(activity: Activity) {}

    @Suppress("EmptyFunctionBlock")
    override fun onActivityStopped(activity: Activity) {}

    @Suppress("EmptyFunctionBlock")
    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) {}
}
