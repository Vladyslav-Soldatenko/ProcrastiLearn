plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose")
  id("com.google.devtools.ksp")
  id("com.google.dagger.hilt.android")
  id("org.jlleitschuh.gradle.ktlint") version "13.1.0"
  id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

android {
  namespace = "com.procrastilearn.app"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.procrastilearn.app"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
  }
  sourceSets {
    getByName("androidTest").assets.srcDir("$projectDir/schemas")
  }

  buildTypes {
    debug {
      isMinifyEnabled = false
    }
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro",
      )
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions {
    jvmTarget = "17"
  }
  buildFeatures {
    compose = true
  }
}

dependencies {
  implementation("io.github.open-spaced-repetition:fsrs:1.0.0")
  implementation("androidx.room:room-runtime:2.6.1")
  implementation("androidx.room:room-ktx:2.6.1")
  androidTestImplementation("androidx.room:room-testing:2.6.1")
  ksp("androidx.room:room-compiler:2.6.1")

  implementation(libs.androidx.datastore.preferences)
  implementation("com.google.dagger:hilt-android:2.56.2")
  ksp("com.google.dagger:hilt-compiler:2.56.2")
  implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
  // (Optional but common) AndroidX Hilt extensions:
  // If you use hiltViewModel(), WorkManager with Hilt, etc., add:
  // implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
  // ksp("androidx.hilt:hilt-compiler:1.2.0")

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.ui.tooling.preview)
  implementation(libs.androidx.material3)
  testImplementation(libs.junit)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.ui.test.junit4)
  debugImplementation(libs.androidx.ui.tooling)
  debugImplementation(libs.androidx.ui.test.manifest)
}
tasks.named("check") {
  dependsOn("ktlintCheck")
  dependsOn("detekt")
}
ktlint {
  version.set("1.3.1")
  android.set(true)
  outputToConsole.set(true)
  ignoreFailures.set(false)
  reporters {
    reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
    reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
  }
}

detekt {
  buildUponDefaultConfig = true // start from default rules
  allRules = false // don’t enable every experimental rule
  config.setFrom(files("$rootDir/detekt.yml"))
  ignoreFailures = false
}
