// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  id("com.android.application") version "8.12.1" apply false
  id("org.jetbrains.kotlin.android") version "2.1.10" apply false
  id("org.jetbrains.kotlin.plugin.compose") version "2.1.10" apply false
  id("com.google.devtools.ksp") version "2.1.10-1.0.31" apply false
  id("com.google.dagger.hilt.android") version "2.56.2" apply false
  id("org.jlleitschuh.gradle.ktlint") version "13.1.0"
}
