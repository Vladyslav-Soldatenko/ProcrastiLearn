package com.procrastilearn.app.appfunctions

import android.annotation.SuppressLint
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
@SuppressLint("NewApi", "InlinedApi")
class VocabularyFunctionsContractTest {
    @Test
    fun `manifest and generated metadata describe one safe function`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val flags = PackageManager.GET_META_DATA
        val component = ComponentName(context, VocabularyAppFunctionService::class.java)
        val service = context.packageManager.getServiceInfo(component, flags)
        assertThat(service.exported).isTrue()
        assertThat(service.permission).isEqualTo("android.permission.BIND_APP_FUNCTION_SERVICE")
        val intent = Intent("android.app.appfunctions.AppFunctionService").setPackage(context.packageName)
        assertThat(context.packageManager.queryIntentServices(intent, flags).map { it.serviceInfo.name })
            .contains(component.className)
        val document =
            requireNotNull(
                javaClass.classLoader!!.getResourceAsStream("assets/vocabulary_app_function_service.xml"),
            ).use {
                DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(it)
            }
        val functions = document.getElementsByTagName("appfunction")
        assertThat(functions.length).isEqualTo(1)
        assertThat(document.getElementsByTagName("functionId").item(0).textContent)
            .isEqualTo(VocabularyAppFunctionService.FUNCTION_ID_ADD_WORD)
        val parameters = document.getElementsByTagName("parameters")
        assertThat(parameters.length).isEqualTo(2)
        val text = document.documentElement.textContent
        assertThat(text).contains("translation and learning progress")
        assertThat(text).contains("word")
        assertThat(text).contains("translation")
        assertThat(text).contains("false")
    }
}
