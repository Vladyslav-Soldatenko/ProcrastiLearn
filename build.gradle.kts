// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  id("com.android.application") version "9.2.1" apply false
  id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
  id("com.google.devtools.ksp") version "2.3.9" apply false
  id("com.google.dagger.hilt.android") version "2.59.2" apply false
  id("org.jlleitschuh.gradle.ktlint") version "13.1.0"
  id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

tasks.register("bumpVersion") {
  group = "release"
  description = "Bumps VERSION_CODE/VERSION_NAME in gradle.properties and adds a changelog placeholder. " +
    "Usage: ./gradlew bumpVersion [-PbumpType=patch|minor|major] (default: patch)"

  doLast {
    val bumpType = (project.findProperty("bumpType") as String?) ?: "patch"
    check(bumpType in setOf("patch", "minor", "major")) {
      "bumpType must be one of: patch, minor, major (got '$bumpType')"
    }

    val propsFile = rootProject.file("gradle.properties")
    val lines = propsFile.readLines().toMutableList()

    var newVersionCode = -1
    var newVersionName = ""

    for (i in lines.indices) {
      val line = lines[i]
      when {
        line.startsWith("VERSION_CODE=") -> {
          newVersionCode = line.substringAfter("=").trim().toInt() + 1
          lines[i] = "VERSION_CODE=$newVersionCode"
        }
        line.startsWith("VERSION_NAME=") -> {
          val (major, minor, patch) = line.substringAfter("=").trim().split(".").map { it.toInt() }
          newVersionName = when (bumpType) {
            "major" -> "${major + 1}.0.0"
            "minor" -> "$major.${minor + 1}.0"
            else -> "$major.$minor.${patch + 1}"
          }
          lines[i] = "VERSION_NAME=$newVersionName"
        }
      }
    }

    check(newVersionCode > 0 && newVersionName.isNotEmpty()) {
      "Could not find VERSION_CODE/VERSION_NAME in gradle.properties"
    }

    propsFile.writeText(lines.joinToString(separator = "\n", postfix = "\n"))

    val changelogFile = rootProject.file(
      "fastlane/metadata/android/en-US/changelogs/$newVersionCode.txt",
    )
    changelogFile.writeText("Some minor tweaks and improvements")

    logger.lifecycle(
      "Bumped versionCode -> $newVersionCode, versionName -> $newVersionName. " +
        "Fill in ${changelogFile.relativeTo(rootDir)} before releasing.",
    )
  }
}
