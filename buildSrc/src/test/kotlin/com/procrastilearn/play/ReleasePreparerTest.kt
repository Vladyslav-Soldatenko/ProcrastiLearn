package com.procrastilearn.play

import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.outputStream
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.io.TempDir

class ReleasePreparerTest {
  @TempDir
  lateinit var temporaryDirectory: Path

  @Test
  fun `failed signature verification leaves no usable manifest`() {
    val metadata = temporaryDirectory.resolve("metadata")
    metadata.resolve("en-US/changelogs").createDirectories()
    metadata.resolve("en-US/changelogs/18.txt").writeText("Notes")
    val prepared = temporaryDirectory.resolve("prepared")
    prepared.createDirectories()
    prepared.resolve("manifest.json").writeText("stale")
    val unsignedBundle = temporaryDirectory.resolve("app-release.aab")
    JarOutputStream(unsignedBundle.outputStream()).use { jar ->
      jar.putNextEntry(JarEntry("base/manifest/AndroidManifest.xml"))
      jar.write(byteArrayOf(1, 2, 3))
      jar.closeEntry()
    }

    assertFailsWith<IllegalStateException> {
      ReleasePreparer(
        metadataRoot = metadata,
        preparedDirectory = prepared,
        bundle = unsignedBundle,
      ).prepare(
        PreparedRelease(
          packageName = "com.example",
          versionCode = 18,
          versionName = "1.0",
          relativeAabPath = "aab/app.aab",
        ),
      )
    }

    assertFalse(prepared.resolve("manifest.json").exists())
  }
}
