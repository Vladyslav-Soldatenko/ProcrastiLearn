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
  implementation(libs.fsrs)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  androidTestImplementation(libs.androidx.room.testing)
  ksp(libs.androidx.room.compiler)

  implementation(libs.androidx.datastore.preferences)
  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
  implementation(libs.androidx.hilt.navigation.compose)
  // (Optional but common) AndroidX Hilt extensions:
  // If you use hiltViewModel(), WorkManager with Hilt, etc., add:
  // implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
  // ksp("androidx.hilt:hilt-compiler:1.2.0")

  implementation(libs.openai.java) {
    exclude(group = "org.apache.httpcomponents.client5", module = "httpclient5")
    exclude(group = "org.apache.httpcomponents.core5", module = "httpcore5")
    exclude(group = "org.apache.httpcomponents.core5", module = "httpcore5-h2")
  }
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

  testImplementation("junit:junit:4.13.2")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
  testImplementation("androidx.room:room-testing:2.6.0")
  testImplementation("androidx.test:core:1.5.0")
  testImplementation("androidx.test:runner:1.5.2")
  testImplementation("androidx.test.ext:junit:1.1.5")
  testImplementation("io.mockk:mockk:1.13.8")
  testImplementation("app.cash.turbine:turbine:1.0.0")
  testImplementation("com.google.truth:truth:1.1.5")
  testImplementation("org.robolectric:robolectric:4.16")
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
