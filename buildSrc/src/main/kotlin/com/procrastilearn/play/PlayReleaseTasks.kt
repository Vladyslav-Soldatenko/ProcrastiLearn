package com.procrastilearn.play

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@CacheableTask
abstract class ValidatePlayMetadataTask : DefaultTask() {
  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val metadataDirectory: DirectoryProperty

  @TaskAction
  fun validate() {
    val errors = PlayMetadataValidator(metadataDirectory.get().asFile.toPath()).validateListings()
    failIfInvalid("Play listing metadata validation failed", errors)
    logger.lifecycle("Play listing metadata is valid.")
  }
}

@CacheableTask
abstract class ValidatePlayChangelogsTask : DefaultTask() {
  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val metadataDirectory: DirectoryProperty

  @get:Input
  abstract val versionCode: Property<Int>

  @TaskAction
  fun validate() {
    val errors =
      PlayMetadataValidator(metadataDirectory.get().asFile.toPath()).validateChangelogs(versionCode.get())
    failIfInvalid("Play changelog validation failed", errors)
    logger.lifecycle("Play changelogs for version ${versionCode.get()} are valid.")
  }
}

@DisableCachingByDefault(because = "Checks process environment and a private keystore path")
abstract class ValidatePlaySigningTask : DefaultTask() {
  @get:Input
  abstract val environmentNames: ListProperty<String>

  @TaskAction
  fun validate() {
    val missing = environmentNames.get().filter { System.getenv(it).isNullOrBlank() }
    if (missing.isNotEmpty()) {
      throw GradleException("Missing release signing environment variables: ${missing.joinToString()}")
    }
    val storeFile = System.getenv("PROCRASTILEARN_UPLOAD_STORE_FILE")
    if (!java.io.File(storeFile).isFile) {
      throw GradleException("PROCRASTILEARN_UPLOAD_STORE_FILE does not point to a file: $storeFile")
    }
    logger.lifecycle("Release signing configuration is present.")
  }
}

@DisableCachingByDefault(because = "Verifies and assembles a credential-dependent release bundle")
abstract class PreparePlayReleaseTask : DefaultTask() {
  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val metadataDirectory: DirectoryProperty

  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val releaseBundle: RegularFileProperty

  @get:OutputDirectory
  abstract val preparedDirectory: DirectoryProperty

  @get:Input
  abstract val packageName: Property<String>

  @get:Input
  abstract val versionCode: Property<Int>

  @get:Input
  abstract val versionName: Property<String>

  @get:Input
  abstract val expectedCertificateSha256: Property<String>

  @TaskAction
  fun prepare() {
    val output = preparedDirectory.get().asFile.toPath()
    val release =
      PreparedRelease(
        packageName = packageName.get(),
        versionCode = versionCode.get(),
        versionName = versionName.get(),
        relativeAabPath = "aab/app-release-${versionCode.get()}.aab",
      )
    try {
      ReleasePreparer(
        metadataRoot = metadataDirectory.get().asFile.toPath(),
        preparedDirectory = output,
        bundle = releaseBundle.get().asFile.toPath(),
        expectedCertificateSha256 = expectedCertificateSha256.get(),
      ).prepare(release)
    } catch (error: Exception) {
      output.resolve("manifest.json").toFile().delete()
      throw GradleException("Play release preparation failed: ${error.message}", error)
    }
    logger.lifecycle("Prepared Play release manifest: ${output.resolve("manifest.json")}")
  }
}

private fun failIfInvalid(
  heading: String,
  errors: List<String>,
) {
  if (errors.isNotEmpty()) {
    throw GradleException("$heading:\n${errors.joinToString("\n") { "- $it" }}")
  }
}
