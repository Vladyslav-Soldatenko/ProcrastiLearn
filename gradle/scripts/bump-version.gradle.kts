tasks.register("bumpVersion") {
  group = "release"
  description = "Bumps versionCode/versionName in app/build.gradle.kts and adds a changelog placeholder. " +
    "Usage: ./gradlew bumpVersion [-PbumpType=patch|minor|major] (default: patch)"

  doLast {
    val bumpType = (project.findProperty("bumpType") as String?) ?: "patch"
    check(bumpType in setOf("patch", "minor", "major")) {
      "bumpType must be one of: patch, minor, major (got '$bumpType')"
    }

    val buildFile = rootProject.file("app/build.gradle.kts")
    val lines = buildFile.readLines().toMutableList()

    val versionCodeRegex = Regex("""^(\s*versionCode\s*=\s*)(\d+)\s*$""")
    val versionNameRegex = Regex("""^(\s*versionName\s*=\s*")(\d+)\.(\d+)\.(\d+)("\s*)$""")

    var newVersionCode = -1
    var newVersionName = ""

    for (i in lines.indices) {
      val line = lines[i]
      versionCodeRegex.find(line)?.let { match ->
        val (prefix, code) = match.destructured
        newVersionCode = code.toInt() + 1
        lines[i] = "$prefix$newVersionCode"
      }
      versionNameRegex.find(line)?.let { match ->
        val (prefix, major, minor, patch, suffix) = match.destructured
        newVersionName =
          when (bumpType) {
            "major" -> "${major.toInt() + 1}.0.0"
            "minor" -> "$major.${minor.toInt() + 1}.0"
            else -> "$major.$minor.${patch.toInt() + 1}"
          }
        lines[i] = "$prefix$newVersionName$suffix"
      }
    }

    check(newVersionCode > 0 && newVersionName.isNotEmpty()) {
      "Could not find versionCode/versionName in app/build.gradle.kts"
    }

    buildFile.writeText(lines.joinToString(separator = "\n", postfix = "\n"))

    val metadataDir = rootProject.file("fastlane/metadata/android")
    val changelogFiles =
      metadataDir
        .listFiles { file -> file.isDirectory && file.resolve("changelogs").isDirectory }
        .orEmpty()
        .sortedBy { it.name }
        .map { localeDir -> localeDir.resolve("changelogs/$newVersionCode.txt") }

    check(changelogFiles.isNotEmpty()) {
      "Could not find any locale changelogs directory under ${metadataDir.relativeTo(rootDir)}"
    }

    changelogFiles.forEach { changelogFile ->
      changelogFile.writeText(if (changelogFile.parentFile.parentFile.name == "en-US") "Some minor tweaks and improvements" else "")
    }

    logger.lifecycle(
      "Bumped versionCode -> $newVersionCode, versionName -> $newVersionName. " +
        "Fill in ${changelogFiles.joinToString(", ") { it.relativeTo(rootDir).toString() }} before releasing.",
    )
  }
}
