package com.procrastilearn.play

import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

data class PreparedRelease(
  val packageName: String,
  val versionCode: Int,
  val versionName: String,
  val relativeAabPath: String,
)

object BundleSignatureVerifier {
  fun verify(bundle: Path) {
    var signedPayloadEntries = 0
    val unsignedPayloadEntries = mutableListOf<String>()
    JarFile(bundle.toFile(), true).use { jar ->
      jar.entries().asSequence().filterNot { it.isDirectory }.forEach { entry ->
        jar.getInputStream(entry).use { it.readAllBytes() }
        if (!entry.name.startsWith("META-INF/")) {
          if (entry.certificates.isNullOrEmpty()) {
            unsignedPayloadEntries += entry.name
          } else {
            signedPayloadEntries += 1
          }
        }
      }
    }
    check(signedPayloadEntries > 0) { "bundle contains no signed payload entries" }
    check(unsignedPayloadEntries.isEmpty()) {
      "bundle contains unsigned payload entries: ${unsignedPayloadEntries.take(5).joinToString()}"
    }
  }
}

class ReleasePreparer(
  private val metadataRoot: Path,
  private val preparedDirectory: Path,
  private val bundle: Path,
) {
  fun prepare(release: PreparedRelease) {
    clearPreparedDirectory()
    check(Files.isRegularFile(bundle)) { "release bundle is missing: $bundle" }
    BundleSignatureVerifier.verify(bundle)

    val aabDestination = preparedDirectory.resolve(release.relativeAabPath)
    aabDestination.parent.createDirectories()
    Files.copy(bundle, aabDestination)

    val validator = PlayMetadataValidator(metadataRoot)
    validator.discoverLocales().forEach { locale ->
      val source = metadataRoot.resolve("$locale/changelogs/${release.versionCode}.txt")
      val destination = preparedDirectory.resolve("metadata/android/$locale/changelogs/${release.versionCode}.txt")
      destination.parent.createDirectories()
      Files.copy(source, destination)
    }

    val manifest = preparedDirectory.resolve("manifest.json")
    Files.writeString(manifest, release.toJson())
  }

  fun clearPreparedDirectory() {
    if (!preparedDirectory.exists()) {
      return
    }
    Files.walk(preparedDirectory).use { paths ->
      paths.sorted(Comparator.reverseOrder()).forEach { path ->
        if (path != preparedDirectory || path.isDirectory()) {
          path.deleteIfExists()
        }
      }
    }
  }

  private fun PreparedRelease.toJson(): String =
    """{
  "packageName": "${packageName.jsonEscape()}",
  "versionCode": $versionCode,
  "versionName": "${versionName.jsonEscape()}",
  "aab": "${relativeAabPath.jsonEscape()}"
}
"""

  private fun String.jsonEscape(): String = replace("\\", "\\\\").replace("\"", "\\\"")
}
