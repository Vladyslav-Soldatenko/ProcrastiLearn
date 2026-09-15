package com.procrastilearn.play

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Delete

abstract class PlayReleaseExtension {
  abstract val metadataDirectory: DirectoryProperty
  abstract val preparedDirectory: DirectoryProperty
  abstract val expectedUploadCertificateSha256: Property<String>
}

class PlayReleasePlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val extension = project.extensions.create("playRelease", PlayReleaseExtension::class.java)
    extension.metadataDirectory.convention(project.rootProject.layout.projectDirectory.dir("fastlane/metadata/android"))
    extension.preparedDirectory.convention(project.layout.buildDirectory.dir("play-release/prepared"))

    project.pluginManager.withPlugin("com.android.application") {
      project.afterEvaluate {
        val android = project.extensions.getByName("android")
        val defaultConfig = android.callGetter("getDefaultConfig")
        val versionCode = requireNotNull(defaultConfig.callGetter("getVersionCode") as? Int) { "Android versionCode is required" }
        val versionName = requireNotNull(defaultConfig.callGetter("getVersionName") as? String) { "Android versionName is required" }
        val packageName = requireNotNull(defaultConfig.callGetter("getApplicationId") as? String) { "Android applicationId is required" }

        val validateMetadata =
          project.tasks.register("validatePlayMetadata", ValidatePlayMetadataTask::class.java) {
            group = "verification"
            description = "Validates localized Google Play listing text and images."
            metadataDirectory.set(extension.metadataDirectory)
          }
        val validateChangelogs =
          project.tasks.register("validatePlayChangelogs", ValidatePlayChangelogsTask::class.java) {
            group = "verification"
            description = "Validates localized Google Play changelogs for the Android version code."
            metadataDirectory.set(extension.metadataDirectory)
            this.versionCode.set(versionCode)
          }
        val validateSigning =
          project.tasks.register("validatePlayReleaseSigning", ValidatePlaySigningTask::class.java) {
            group = "release"
            description = "Checks that release signing environment variables and the keystore file are present."
            environmentNames.set(
              listOf(
                "PROCRASTILEARN_UPLOAD_STORE_FILE",
                "PROCRASTILEARN_UPLOAD_STORE_PASSWORD",
                "PROCRASTILEARN_UPLOAD_KEY_ALIAS",
                "PROCRASTILEARN_UPLOAD_KEY_PASSWORD",
              ),
            )
          }
        val cleanPrepared =
          project.tasks.register("cleanPreparedPlayRelease", Delete::class.java) {
            group = "release"
            description = "Invalidates the previously prepared Play release."
            delete(extension.preparedDirectory)
          }
        val bundleRelease = project.tasks.named("bundleRelease")
        validateChangelogs.configure { mustRunAfter(cleanPrepared) }
        validateSigning.configure { mustRunAfter(cleanPrepared) }
        bundleRelease.configure {
          mustRunAfter(cleanPrepared, validateChangelogs, validateSigning)
        }
        project.tasks.register("preparePlayRelease", PreparePlayReleaseTask::class.java) {
          group = "release"
          description = "Builds, verifies, and assembles the current Google Play release."
          dependsOn(cleanPrepared, validateChangelogs, validateSigning, bundleRelease)
          metadataDirectory.set(extension.metadataDirectory)
          releaseBundle.set(project.layout.buildDirectory.file("outputs/bundle/release/app-release.aab"))
          preparedDirectory.set(extension.preparedDirectory)
          this.packageName.set(packageName)
          this.versionCode.set(versionCode)
          this.versionName.set(versionName)
          expectedCertificateSha256.set(extension.expectedUploadCertificateSha256)
        }
        project.tasks.named("check") {
          dependsOn(validateMetadata, validateChangelogs)
        }
      }
    }
  }
}

private fun Any.callGetter(name: String): Any =
  javaClass.methods.firstOrNull { it.name == name && it.parameterCount == 0 }?.invoke(this)
    ?: error("${javaClass.name} does not expose $name")
