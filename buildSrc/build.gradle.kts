plugins {
  `kotlin-dsl`
  `java-gradle-plugin`
}

repositories {
  google()
  mavenCentral()
  gradlePluginPortal()
}

dependencies {
  testImplementation(kotlin("test-junit5"))
}

gradlePlugin {
  plugins {
    create("playRelease") {
      id = "com.procrastilearn.play-release"
      implementationClass = "com.procrastilearn.play.PlayReleasePlugin"
    }
  }
}

tasks.test {
  useJUnitPlatform()
}

kotlin {
  jvmToolchain(21)
}
