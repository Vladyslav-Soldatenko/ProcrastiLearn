plugins {
  id("com.android.application") version "9.2.1" apply false
  id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
  id("com.google.devtools.ksp") version "2.3.9" apply false
  id("com.google.dagger.hilt.android") version "2.59.2" apply false
  id("org.jlleitschuh.gradle.ktlint") version "13.1.0"
  id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

apply(from = "gradle/scripts/bump-version.gradle.kts")
