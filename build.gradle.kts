plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.hilt.android) apply false
  id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
  id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

apply(from = "gradle/scripts/bump-version.gradle.kts")
