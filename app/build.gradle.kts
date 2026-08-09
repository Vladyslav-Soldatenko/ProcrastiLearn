plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.hilt.android)
  alias(libs.plugins.ktlint)
  alias(libs.plugins.detekt)
  jacoco
}

jacoco {
  toolVersion = "0.8.12"
}

android {
  namespace = "com.procrastilearn.app"
  compileSdk = 37

  defaultConfig {
    applicationId = "com.procrastilearn.app"
    minSdk = 30
    //noinspection OldTargetApi
    targetSdk = 36
    versionCode = 11
    versionName = "1.3.3"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
    arg("appfunctions:aggregateAppFunctions", "true")
  }
  sourceSets {
    getByName("androidTest").assets.setSrcDirs(listOf("$projectDir/schemas", "src/androidTest/assets"))
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
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = false
  }

  lint {
    checkReleaseBuilds = true
    abortOnError = true
    error += setOf("MissingTranslation", "ExtraTranslation")
    checkDependencies = true
    checkTestSources = true
    checkAllWarnings = true
    textReport = true
    htmlReport = true
    warningsAsErrors = true
    sarifReport = true
  }
}

kotlin {
  jvmToolchain(21)
}

dependencies {
  implementation(libs.androidx.appfunctions)
  implementation(libs.androidx.appfunctions.service)
  ksp(libs.androidx.appfunctions.compiler)

  implementation(libs.fsrs)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  androidTestImplementation(libs.androidx.room.testing)
  ksp(libs.androidx.room.compiler)

  implementation(libs.androidx.datastore.preferences)
  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
  implementation(libs.androidx.hilt.navigation.compose)
  implementation(libs.fastscroller.material3)

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
  implementation(libs.androidx.material.icons.extended)
  implementation("${libs.zstd.jni.get()}@aar")
  testImplementation(libs.zstd.jni)
  testImplementation(libs.junit)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.espresso.intents)
  testImplementation(platform(libs.androidx.compose.bom))
  testImplementation(libs.androidx.ui.test.junit4)
  testImplementation(libs.androidx.ui.test.manifest)
  testReleaseImplementation(libs.androidx.ui.test.manifest)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.ui.test.junit4)
  androidTestImplementation(libs.androidx.test.uiautomator)
  debugImplementation(libs.androidx.ui.tooling)
  debugImplementation(libs.androidx.ui.test.manifest)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.androidx.room.testing)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.androidx.test.runner)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.mockk)
  testImplementation(libs.turbine)
  testImplementation(libs.truth)
  testImplementation(libs.robolectric)
}
tasks.named("check") {
  dependsOn("ktlintCheck")
  dependsOn("detekt")
  dependsOn("lint")
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
  source.setFrom(
    "src/main/java",
    "src/test/java",
    "src/androidTest/java",
  )
}
android {
  testOptions {
    unitTests.isIncludeAndroidResources = true
  }
}

tasks.withType<Test>().configureEach {
  extensions.configure(JacocoTaskExtension::class) {
    isIncludeNoLocationClasses = true
    excludes = listOf("jdk.internal.*")
  }
}

tasks.register<JacocoReport>("jacocoTestReport") {
  dependsOn("testDebugUnitTest")
  group = "verification"
  description = "Generates JaCoCo coverage report for debug unit tests."

  reports {
    xml.required.set(true)
    html.required.set(true)
    csv.required.set(true)
  }

  val coverageExcludes =
    listOf(
      "**/*_Impl*.*", // Room-generated DAO/database
      "**/*ComposableSingletons*.*", // Compose-generated
      "**/*Preview*.*", // @Preview composables
      "**/*aggregated_deps/**", // Hilt/AppFunctions generated registries
      "**/di/**", // Hilt DI modules (config)
    )

  classDirectories.setFrom(
    fileTree("${layout.buildDirectory.get()}/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes") {
      exclude(coverageExcludes)
    },
  )
  sourceDirectories.setFrom(files("$projectDir/src/main/java"))
  executionData.setFrom(files("${layout.buildDirectory.get()}/jacoco/testDebugUnitTest.exec"))
}
