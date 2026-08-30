import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
  alias(libs.plugins.hilt.android)
  alias(libs.plugins.ktlint)
  alias(libs.plugins.detekt)
  alias(libs.plugins.dependency.analysis)
  jacoco
}

jacoco {
  toolVersion = "0.8.12"
}

val localProperties =
  Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
      file.inputStream().use { load(it) }
    }
  }

val openAiApiKeyForTests: String =
  System.getenv("OPENAI_API_KEY") ?: localProperties.getProperty("OPENAI_API_KEY") ?: ""

android {
  namespace = "com.procrastilearn.app"
  compileSdk = 37

  defaultConfig {
    applicationId = "com.procrastilearn.app"
    minSdk = 30
    //noinspection OldTargetApi
    targetSdk = 36
    versionCode = 16
    versionName = "1.4.2"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    testInstrumentationRunnerArguments["OPENAI_API_KEY"] = openAiApiKeyForTests
  }

  ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.generateKotlin", "true")
    arg("appfunctions:aggregateAppFunctions", "true")
  }
  sourceSets {
    getByName("androidTest").assets.directories.apply {
      clear()
      addAll(listOf("$projectDir/schemas", "src/androidTest/assets", "src/testShared/resources"))
    }
    getByName("test").resources.srcDirs("src/testShared/resources")
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
  compilerOptions {
    allWarningsAsErrors = true
  }
}

dependencies {
  implementation(libs.androidx.appfunctions)
  implementation(libs.androidx.appfunctions.service)
  ksp(libs.androidx.appfunctions.compiler)

  implementation(libs.fsrs)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.kotlinx.collections.immutable)
  implementation(libs.androidx.room.runtime)
  androidTestImplementation(libs.androidx.room.testing)
  ksp(libs.androidx.room.compiler)

  implementation(libs.androidx.datastore.preferences)
  implementation(libs.hilt.android)
  ksp(libs.hilt.compiler)
  implementation(libs.androidx.hilt.navigation.compose)
  implementation(libs.fastscroller.material3)
  implementation(libs.reorderable)

  implementation(libs.openai.java) {
    exclude(group = "org.apache.httpcomponents.client5", module = "httpclient5")
    exclude(group = "org.apache.httpcomponents.core5", module = "httpcore5")
    exclude(group = "org.apache.httpcomponents.core5", module = "httpcore5-h2")
  }
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.ui.tooling.preview)
  implementation(libs.androidx.material3)
  implementation(libs.androidx.material.icons.extended)
  lintChecks(libs.compose.lint.checks)
  detektPlugins(libs.compose.rules.detekt)
  implementation("${libs.zstd.jni.get()}@aar")
  // Not redundant despite the main `@aar` dependency above: AnkiApkgVocabularyParserTest loads
  // the native zstd JNI binding on the plain JVM test classpath, which the AAR classifier alone
  // does not provide there. See DAGP exclude() below.
  testImplementation(libs.zstd.jni)
  testImplementation(libs.junit)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.espresso.intents)
  testImplementation(platform(libs.androidx.compose.bom))
  testImplementation(libs.androidx.ui.test.junit4)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.ui.test.junit4)
  debugImplementation(libs.androidx.ui.tooling)
  debugRuntimeOnly(libs.androidx.ui.test.manifest)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.androidx.room.testing)
  testImplementation(libs.androidx.test.core)
  testRuntimeOnly(libs.androidx.test.runner)
  testImplementation(libs.mockk)
  testImplementation(libs.turbine)
  testImplementation(libs.truth)
  testImplementation(libs.robolectric)
}
tasks.named("check") {
  dependsOn("ktlintCheck")
  dependsOn("detekt")
  dependsOn("lint")
  dependsOn("projectHealth")
}

dependencyAnalysis {
  issues {
    onUnusedDependencies {
      severity("fail")
      exclude(
        // AnkiApkgVocabularyParserTest loads the native zstd JNI binding on the plain JVM test
        // classpath; the `@aar`-classified main dependency alone doesn't provide that there.
        "com.github.luben:zstd-jni",
        // Umbrella artifact; the actually-used classes live in its openai-java-core and
        // openai-java-client-okhttp children. Swapping to declare those directly needs a
        // deliberate pass to carry over the httpclient5 excludes below, not an automated removal.
        "com.openai:openai-java",
      )
    }
    onIncorrectConfiguration {
      severity("fail")
    }
  }
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
