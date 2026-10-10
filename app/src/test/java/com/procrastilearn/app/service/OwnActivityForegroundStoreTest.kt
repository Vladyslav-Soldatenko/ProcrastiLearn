package com.procrastilearn.app.service

import android.app.Activity
import android.app.Application
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class OwnActivityForegroundStoreTest {
    @Test
    fun `resuming own activity blocks foreground until its pause`() {
        val store = OwnActivityForegroundStore()
        val activity = mockk<Activity>()

        store.onActivityResumed(activity)
        assertThat(store.isResumed.value).isTrue()
        store.onActivityPaused(activity)
        assertThat(store.isResumed.value).isFalse()
    }

    @Test
    fun `pausing one activity keeps another resumed activity foreground`() {
        val store = OwnActivityForegroundStore()
        val first = mockk<Activity>()
        val second = mockk<Activity>()

        store.onActivityResumed(first)
        store.onActivityResumed(second)
        store.onActivityPaused(first)
        assertThat(store.isResumed.value).isTrue()
        store.onActivityDestroyed(second)
        assertThat(store.isResumed.value).isFalse()
    }

    @Test
    fun `duplicate resume does not retain activity after pause`() {
        val store = OwnActivityForegroundStore()
        val activity = mockk<Activity>()

        store.onActivityResumed(activity)
        store.onActivityResumed(activity)
        store.onActivityPaused(activity)

        assertThat(store.isResumed.value).isFalse()
    }

    @Test
    fun `registering twice adds one application lifecycle callback`() {
        val store = OwnActivityForegroundStore()
        val application = mockk<Application>(relaxed = true)

        store.register(application)
        store.register(application)

        verify(exactly = 1) { application.registerActivityLifecycleCallbacks(store) }
    }
}
